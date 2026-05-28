package com.web.labportalbackend.research.service.impl;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.exception.InvalidEvaluationScoreException;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.dto.request.EvaluationRequest;
import com.web.labportalbackend.research.dto.response.EvaluationResponse;
import com.web.labportalbackend.research.entity.EvaluationEntity;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.enums.ResearchLogVisibility;
import com.web.labportalbackend.research.mapper.EvaluationMapper;
import com.web.labportalbackend.research.repository.EvaluationRepository;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.service.EvaluationService;
import com.web.labportalbackend.research.service.LogService;
import com.web.labportalbackend.research.service.ResearchLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EvaluationServiceImpl implements EvaluationService {

    private static final BigDecimal MIN_SCORE = BigDecimal.ZERO;
    private static final BigDecimal MAX_SCORE = BigDecimal.TEN;
    private static final BigDecimal SCORE_COUNT = BigDecimal.valueOf(5);

    private final EvaluationRepository evaluationRepository;
    private final ProjectRepository projectRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final UserRepository userRepository;
    private final LogService logService;
    private final ResearchLogService researchLogService;

    @Override
    @Transactional
    public EvaluationResponse evaluateProject(Long projectId, EvaluationRequest request) {
        User currentUser = getCurrentUser();
        ProjectEntity project = findProject(projectId);
        assertManagerOwnsProject(currentUser, project);
        validateScores(request);

        User student = userRepository.findById(request.getStudentId())
                .orElseThrow(() -> new ResourceNotFoundException("Student", request.getStudentId()));
        Long groupId = resolveStudentGroupId(project.getId(), student.getId());
        BigDecimal totalScore = calculateTotalScore(request);

        EvaluationEntity evaluation = evaluationRepository
                .findFirstByProjectIdAndStudentIdAndDeletedFalseAndActiveTrueOrderByCreatedAtDesc(project.getId(), student.getId())
                .orElseGet(EvaluationEntity::new);
        evaluation.setProjectId(project.getId());
        evaluation.setGroupId(groupId);
        evaluation.setStudentId(student.getId());
        evaluation.setEvaluatorId(currentUser.getId());
        evaluation.setAttendanceScore(normalizeScore(request.getAttendanceScore()));
        evaluation.setTaskScore(normalizeScore(request.getTaskScore()));
        evaluation.setReportScore(normalizeScore(request.getReportScore()));
        evaluation.setProductScore(normalizeScore(request.getProductScore()));
        evaluation.setAttitudeScore(normalizeScore(request.getAttitudeScore()));
        evaluation.setTotalScore(totalScore);
        evaluation.setLecturerComment(trimToNull(request.getLecturerComment()));

        EvaluationEntity saved = evaluationRepository.save(evaluation);
        logService.logAction(project.getId(), currentUser.getId(), "EVALUATE_STUDENT",
                "Evaluated student " + student.getId() + " with total score: " + totalScore);
        createSystemLogSafely(
                project.getId(),
                groupId,
                null,
                null,
                currentUser.getId(),
                displayName(currentUser) + " đã tạo đánh giá cho " + displayName(student) + ".",
                "Điểm tổng: " + totalScore,
                ResearchLogVisibility.PROJECT
        );
        return toDetailedResponse(saved, student, currentUser);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvaluationResponse> getByProject(Long projectId) {
        User currentUser = getCurrentUser();
        ProjectEntity project = findProject(projectId);

        if (currentUser.hasRole("LAB_MANAGER")) {
            assertManagerOwnsProject(currentUser, project);
            return evaluationRepository.findByProjectIdAndDeletedFalseAndActiveTrueOrderByCreatedAtDesc(project.getId())
                    .stream()
                    .map(this::toDetailedResponse)
                    .toList();
        }

        if (!currentUser.hasRole("STUDENT")) {
            throw new AccessDeniedException("Cannot access research evaluations");
        }
        if (!groupMemberRepository.existsActiveMemberByProjectIdAndUserId(project.getId(), currentUser.getId())) {
            throw new AccessDeniedException("Cannot access evaluations for this project");
        }
        return evaluationRepository
                .findByProjectIdAndStudentIdAndDeletedFalseAndActiveTrueOrderByCreatedAtDesc(project.getId(), currentUser.getId())
                .stream()
                .map(this::toDetailedResponse)
                .toList();
    }

    private void assertManagerOwnsProject(User currentUser, ProjectEntity project) {
        if (!currentUser.hasRole("LAB_MANAGER")) {
            throw new AccessDeniedException("Only laboratory managers may evaluate research results");
        }
        Laboratory managedLab = laboratoryRepository.findFirstByManagerIdAndDeletedFalse(currentUser.getId())
                .orElseThrow(() -> new AccessDeniedException("Lab manager is not assigned to any lab"));
        if (project.getLab() == null || !managedLab.getId().equals(project.getLab().getId())) {
            throw new AccessDeniedException("Cannot evaluate projects from another lab");
        }
    }

    private Long resolveStudentGroupId(Long projectId, Long studentId) {
        List<Long> groupIds = groupMemberRepository.findActiveGroupIdsByProjectIdAndUserId(projectId, studentId);
        if (groupIds.isEmpty()) {
            throw new AccessDeniedException("Student is not an active member of this project");
        }
        return groupIds.get(0);
    }

    private EvaluationResponse toDetailedResponse(EvaluationEntity evaluation) {
        User student = userRepository.findById(evaluation.getStudentId()).orElse(null);
        User evaluator = userRepository.findById(evaluation.getEvaluatorId()).orElse(null);
        return toDetailedResponse(evaluation, student, evaluator);
    }

    private EvaluationResponse toDetailedResponse(EvaluationEntity evaluation, User student, User evaluator) {
        GroupEntity group = evaluation.getGroupId() == null
                ? null
                : groupRepository.findByIdAndDeletedFalseAndActiveTrue(evaluation.getGroupId()).orElse(null);
        return EvaluationMapper.toResponse(evaluation, student, evaluator, group);
    }

    private ProjectEntity findProject(Long projectId) {
        return projectRepository.findByIdAndDeletedFalseAndActiveTrue(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
    }

    private void validateScores(EvaluationRequest request) {
        validateScore(request.getAttendanceScore());
        validateScore(request.getTaskScore());
        validateScore(request.getReportScore());
        validateScore(request.getProductScore());
        validateScore(request.getAttitudeScore());
    }

    private void validateScore(BigDecimal score) {
        if (score == null || score.compareTo(MIN_SCORE) < 0 || score.compareTo(MAX_SCORE) > 0) {
            throw new InvalidEvaluationScoreException(score);
        }
    }

    private BigDecimal calculateTotalScore(EvaluationRequest request) {
        return normalizeScore(request.getAttendanceScore())
                .add(normalizeScore(request.getTaskScore()))
                .add(normalizeScore(request.getReportScore()))
                .add(normalizeScore(request.getProductScore()))
                .add(normalizeScore(request.getAttitudeScore()))
                .divide(SCORE_COUNT, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeScore(BigDecimal score) {
        return score.setScale(2, RoundingMode.HALF_UP);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String displayName(User user) {
        return StringUtils.hasText(user.getFullName()) ? user.getFullName() : user.getUsername();
    }

    private void createSystemLogSafely(
            Long projectId,
            Long groupId,
            Long milestoneId,
            Long taskId,
            Long authorId,
            String content,
            String result,
            ResearchLogVisibility visibility
    ) {
        if (researchLogService != null) {
            researchLogService.createSystemLog(projectId, groupId, milestoneId, taskId, authorId, content, result, visibility);
        }
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication is required");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", authentication.getName()));
    }
}
