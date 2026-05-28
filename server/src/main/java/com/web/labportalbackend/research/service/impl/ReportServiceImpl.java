package com.web.labportalbackend.research.service.impl;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.exception.ReportVersionConflictException;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.dto.request.SubmitReportRequest;
import com.web.labportalbackend.research.dto.request.LeaderReviewReportRequest;
import com.web.labportalbackend.research.dto.request.ManagerReviewReportRequest;
import com.web.labportalbackend.research.dto.response.ReportResponse;
import com.web.labportalbackend.research.dto.response.ReportFileDownload;
import com.web.labportalbackend.research.entity.CommentEntity;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.MilestoneEntity;
import com.web.labportalbackend.research.entity.ReportEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.enums.ManagerReportDecision;
import com.web.labportalbackend.research.enums.MilestoneStatus;
import com.web.labportalbackend.research.enums.ReportStatus;
import com.web.labportalbackend.research.enums.ResearchLogVisibility;
import com.web.labportalbackend.research.enums.TaskStatus;
import com.web.labportalbackend.research.mapper.ReportMapper;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.CommentRepository;
import com.web.labportalbackend.research.repository.MilestoneRepository;
import com.web.labportalbackend.research.repository.ReportRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
import com.web.labportalbackend.research.service.ReportService;
import com.web.labportalbackend.research.service.ResearchLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private static final long MAX_REPORT_FILE_SIZE = 10 * 1024 * 1024;
    private static final Map<String, String> ALLOWED_REPORT_TYPES = Map.of(
            "pdf", "application/pdf",
            "doc", "application/msword",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private final ReportRepository reportRepository;
    private final TaskRepository taskRepository;
    private final MilestoneRepository milestoneRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final CommentRepository commentRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final UserRepository userRepository;
    private final ResearchLogService researchLogService;

    @Value("${app.research.report-storage-path:storage/reports}")
    private String reportStoragePath;

    @Override
    @Transactional
    public ReportResponse submitReport(SubmitReportRequest request, MultipartFile file) {
        User currentUser = getCurrentUser();
        if (request.getTaskId() == null) {
            throw new IllegalArgumentException("Task ID is required");
        }
        TaskEntity task = taskRepository.findById(request.getTaskId())
                .orElseThrow(() -> new ResourceNotFoundException("Task", request.getTaskId()));
        MilestoneEntity milestone = findMilestone(task.getMilestoneId());
        if (milestone.getStatus() == MilestoneStatus.COMPLETED) {
            throw new IllegalArgumentException("Completed milestones do not accept new reports");
        }

        assertCanSubmitReport(currentUser, milestone, task);
        Long projectId = milestone.getProject().getId();
        Long groupId = resolveGroupId(projectId, milestone);
        String scope = buildSubmissionScope(milestone.getId(), task.getId(), currentUser.getId());
        Integer version = reportRepository
                .findMaxVersionByTaskIdAndSubmittedById(task.getId(), currentUser.getId())
                .orElse(0) + 1;
        StoredReportFile storedFile = storeReportFile(file, task.getId(), version);

        ReportEntity report = ReportEntity.builder()
                .projectId(projectId)
                .groupId(groupId)
                .milestoneId(milestone.getId())
                .taskId(task.getId())
                .submittedById(currentUser.getId())
                .version(version)
                .title(request.getTitle().trim())
                .contentDone(request.getContentDone().trim())
                .result(request.getResult().trim())
                .difficulty(request.getDifficulty().trim())
                .nextPlan(request.getNextPlan().trim())
                .selfAssessment(request.getSelfAssessment().trim())
                .fileUrl(storedFile.url())
                .fileName(storedFile.originalFileName())
                .fileType(storedFile.contentType())
                .fileSize(storedFile.size())
                .evidenceLink(normalizeEvidenceLink(request.getEvidenceLink()))
                .status(ReportStatus.SUBMITTED)
                .submissionScope(scope)
                .build();

        try {
            ReportEntity saved = reportRepository.saveAndFlush(report);
            createSystemLogSafely(
                    projectId,
                    groupId,
                    milestone.getId(),
                    task.getId(),
                    currentUser.getId(),
                    displayName(currentUser) + " đã nộp báo cáo v" + saved.getVersion()
                            + " cho nhiệm vụ " + task.getTitle() + ".",
                    saved.getResult(),
                    ResearchLogVisibility.GROUP
            );
            return ReportMapper.toResponse(saved, currentUser);
        } catch (DataIntegrityViolationException ex) {
            deleteStoredFile(storedFile.path());
            throw new ReportVersionConflictException(task.getId());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportResponse> getReportsByTask(Long taskId) {
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
        MilestoneEntity milestone = findMilestone(task.getMilestoneId());
        User currentUser = getCurrentUser();
        GroupRole role = assertCanViewReports(currentUser, milestone);
        if (currentUser.hasRole("STUDENT")
                && role == GroupRole.MEMBER
                && !currentUser.getId().equals(task.getAssigneeId())) {
            throw new AccessDeniedException("Members may view only reports for assigned tasks");
        }
        return reportRepository.findByTaskIdOrderByVersionDesc(taskId).stream()
                .filter(report -> role != GroupRole.MEMBER || currentUser.getId().equals(report.getSubmittedById()))
                .map(report -> ReportMapper.toResponse(report, findSubmitter(report)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ReportFileDownload downloadReportFile(Long reportId) {
        ReportEntity report = findReport(reportId);
        TaskEntity task = taskRepository.findById(report.getTaskId())
                .orElseThrow(() -> new ResourceNotFoundException("Task", report.getTaskId()));
        User currentUser = getCurrentUser();
        GroupRole role = assertCanViewReports(currentUser, findMilestone(task.getMilestoneId()));
        if (currentUser.hasRole("STUDENT") && role == GroupRole.MEMBER
                && (!currentUser.getId().equals(task.getAssigneeId())
                || !currentUser.getId().equals(report.getSubmittedById()))) {
            throw new AccessDeniedException("Members may download only their submitted task reports");
        }

        Path reportPath = getReportStorageDirectory()
                .resolve(String.valueOf(task.getId()))
                .resolve(report.getVersion() + "." + getExtension(report.getFileName()))
                .normalize();
        Path baseDirectory = getReportStorageDirectory();
        if (!reportPath.startsWith(baseDirectory) || !Files.isRegularFile(reportPath)) {
            throw new ResourceNotFoundException("Report file", reportId);
        }
        return new ReportFileDownload(
                new FileSystemResource(reportPath),
                report.getFileName(),
                report.getFileType()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportResponse> getReportsByMilestone(Long milestoneId) {
        MilestoneEntity milestone = findMilestone(milestoneId);
        User currentUser = getCurrentUser();
        GroupRole role = assertCanViewReports(currentUser, milestone);
        List<ReportEntity> reports = currentUser.hasRole("STUDENT") && role == GroupRole.MEMBER
                ? reportRepository.findByMilestoneIdAndSubmittedByIdOrderByCreatedAtDescVersionDesc(
                        milestoneId,
                        currentUser.getId()
                )
                : reportRepository.findByMilestoneIdOrderByCreatedAtDescVersionDesc(milestoneId);
        return reports.stream()
                .map(report -> toDetailedResponse(report, milestone))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportResponse> getMyReportsByMilestone(Long milestoneId) {
        MilestoneEntity milestone = findMilestone(milestoneId);
        User currentUser = getCurrentUser();
        if (!currentUser.hasRole("STUDENT")) {
            throw new AccessDeniedException("Only students may access their report history");
        }
        GroupRole role = assertCanViewReports(currentUser, milestone);
        if (role == GroupRole.MEMBER
                && (milestone.getAssignedToStudent() == null
                || !currentUser.getId().equals(milestone.getAssignedToStudent().getId()))
                && !taskRepository.existsByMilestoneIdAndAssigneeIdAndDeletedFalseAndActiveTrue(
                        milestoneId,
                        currentUser.getId()
                )) {
            throw new AccessDeniedException("Members may view reports only for assigned milestones or tasks");
        }
        return reportRepository.findByMilestoneIdAndSubmittedByIdOrderByCreatedAtDescVersionDesc(
                        milestoneId,
                        currentUser.getId()
                )
                .stream()
                .map(ReportMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportResponse> getReportsByGroup(Long groupId) {
        User currentUser = getCurrentUser();
        if (!currentUser.hasRole("STUDENT")) {
            throw new AccessDeniedException("Only students may access group reports");
        }
        GroupEntity group = groupRepository.findByIdAndDeletedFalseAndActiveTrue(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Research group", groupId));
        GroupRole role = groupMemberRepository.findActiveRoleByGroupIdAndUserId(groupId, currentUser.getId())
                .orElseThrow(() -> new AccessDeniedException("Cannot access reports for this group"));
        if (role != GroupRole.LEADER) {
            throw new AccessDeniedException("Only the group leader may view all member reports");
        }
        return reportRepository.findByGroupIdAndDeletedFalseAndActiveTrueOrderByCreatedAtDescVersionDesc(groupId)
                .stream()
                .map(report -> {
                    MilestoneEntity milestone = findMilestone(report.getMilestoneId());
                    TaskEntity task = report.getTaskId() == null
                            ? null
                            : taskRepository.findById(report.getTaskId()).orElse(null);
                    return ReportMapper.toResponse(report, findSubmitter(report), group, milestone, task);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportResponse> getPendingManagerReviewByLab(Long labId) {
        User currentUser = getCurrentUser();
        Laboratory managedLab = assertManagerOwnsLab(currentUser, labId);
        return reportRepository.findPendingManagerReviewByLabId(managedLab.getId()).stream()
                .map(report -> {
                    MilestoneEntity milestone = findMilestone(report.getMilestoneId());
                    GroupEntity group = report.getGroupId() == null
                            ? null
                            : groupRepository.findByIdAndDeletedFalseAndActiveTrue(report.getGroupId()).orElse(null);
                    TaskEntity task = report.getTaskId() == null
                            ? null
                            : taskRepository.findById(report.getTaskId()).orElse(null);
                    return ReportMapper.toResponse(report, findSubmitter(report), group, milestone, task);
                })
                .toList();
    }

    private ReportResponse toDetailedResponse(ReportEntity report, MilestoneEntity milestone) {
        GroupEntity group = report.getGroupId() == null
                ? null
                : groupRepository.findByIdAndDeletedFalseAndActiveTrue(report.getGroupId()).orElse(null);
        TaskEntity task = report.getTaskId() == null
                ? null
                : taskRepository.findById(report.getTaskId()).orElse(null);
        return ReportMapper.toResponse(report, findSubmitter(report), group, milestone, task);
    }

    @Override
    @Transactional
    public ReportResponse leaderReview(Long reportId, LeaderReviewReportRequest request) {
        ReportEntity report = findReport(reportId);
        User currentUser = getCurrentUser();
        String note = request.getNote().trim();
        if (!currentUser.hasRole("STUDENT") || report.getGroupId() == null
                || groupMemberRepository.findActiveRoleByGroupIdAndUserId(report.getGroupId(), currentUser.getId())
                .filter(role -> role == GroupRole.LEADER)
                .isEmpty()) {
            throw new AccessDeniedException("Không thể kiểm tra báo cáo này");
        }
        if (currentUser.getId().equals(report.getSubmittedById())) {
            throw new AccessDeniedException("Không thể kiểm tra báo cáo này");
        }
        assertCanViewReports(currentUser, findMilestone(report.getMilestoneId()));
        assertLatestSubmissionVersion(report);
        if (report.getStatus() != ReportStatus.SUBMITTED && report.getStatus() != ReportStatus.NEEDS_REVISION) {
            throw new IllegalArgumentException("Không thể kiểm tra báo cáo này");
        }

        report.setStatus(ReportStatus.LEADER_REVIEWED);
        report.setLeaderReviewedAt(Instant.now());
        report.setLeaderComment(note);
        saveReviewComment(reportId, currentUser.getId(), "Trưởng nhóm đã kiểm tra: " + note);
        ReportEntity saved = reportRepository.save(report);
        createSystemLogSafely(
                saved.getProjectId(),
                saved.getGroupId(),
                saved.getMilestoneId(),
                saved.getTaskId(),
                currentUser.getId(),
                displayName(currentUser) + " đã kiểm tra báo cáo v" + saved.getVersion() + ".",
                note,
                ResearchLogVisibility.GROUP
        );
        return ReportMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ReportResponse managerReview(Long reportId, ManagerReviewReportRequest request) {
        ReportEntity report = findReport(reportId);
        User currentUser = getCurrentUser();
        MilestoneEntity milestone = findMilestone(report.getMilestoneId());
        if (!currentUser.hasRole("LAB_MANAGER")) {
            throw new AccessDeniedException("Only laboratory managers may make the final report decision");
        }
        assertCanViewReports(currentUser, milestone);
        assertLatestSubmissionVersion(report);
        if (report.getStatus() != ReportStatus.LEADER_REVIEWED) {
            throw new IllegalArgumentException("The report must be reviewed by the group leader first");
        }

        ManagerReportDecision decision = request.getDecision();
        String comment = request.getComment().trim();
        if (decision == ManagerReportDecision.APPROVE) {
            report.setStatus(ReportStatus.APPROVED);
            approveSubmittedTask(report);
        } else if (decision == ManagerReportDecision.REQUEST_REVISION) {
            report.setStatus(ReportStatus.NEEDS_REVISION);
            requestRevisionForSubmittedTask(report);
        } else {
            report.setStatus(ReportStatus.REJECTED);
        }
        report.setManagerReviewedAt(Instant.now());
        report.setManagerComment(comment);
        milestone.setManagerComment(comment);

        saveReviewComment(reportId, currentUser.getId(), "Quản lý PTN: " + comment);
        reportRepository.saveAndFlush(report);
        recalculateMilestoneProgress(milestone, report);
        milestoneRepository.save(milestone);
        User submitter = findSubmitter(report);
        createSystemLogSafely(
                report.getProjectId(),
                report.getGroupId(),
                report.getMilestoneId(),
                report.getTaskId(),
                currentUser.getId(),
                displayName(currentUser) + " đã " + managerDecisionLabel(decision)
                        + " báo cáo của " + displayName(submitter) + ".",
                comment,
                ResearchLogVisibility.PROJECT
        );
        return ReportMapper.toResponse(report);
    }

    private ReportEntity findReport(Long reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report", reportId));
    }

    private User findSubmitter(ReportEntity report) {
        return userRepository.findById(report.getSubmittedById())
                .orElseThrow(() -> new ResourceNotFoundException("User", report.getSubmittedById()));
    }

    private String displayName(User user) {
        return StringUtils.hasText(user.getFullName()) ? user.getFullName() : user.getUsername();
    }

    private String managerDecisionLabel(ManagerReportDecision decision) {
        if (decision == ManagerReportDecision.APPROVE) {
            return "duyệt";
        }
        if (decision == ManagerReportDecision.REQUEST_REVISION) {
            return "yêu cầu chỉnh sửa";
        }
        return "từ chối";
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

    private void saveReviewComment(Long reportId, Long authorId, String content) {
        commentRepository.save(CommentEntity.builder()
                .reportId(reportId)
                .authorId(authorId)
                .content(content.trim())
                .build());
    }

    private void assertLatestSubmissionVersion(ReportEntity report) {
        if (reportRepository.existsBySubmissionScopeAndVersionGreaterThan(report.getSubmissionScope(), report.getVersion())) {
            throw new IllegalArgumentException("Only the latest report version can be reviewed");
        }
    }

    private void approveSubmittedTask(ReportEntity report) {
        if (report.getTaskId() != null) {
            TaskEntity task = taskRepository.findById(report.getTaskId())
                    .orElseThrow(() -> new ResourceNotFoundException("Task", report.getTaskId()));
            task.setStatus(TaskStatus.DONE);
            task.setProgressPercent(100);
            taskRepository.save(task);
        }
    }

    private void requestRevisionForSubmittedTask(ReportEntity report) {
        if (report.getTaskId() != null) {
            TaskEntity task = taskRepository.findById(report.getTaskId())
                    .orElseThrow(() -> new ResourceNotFoundException("Task", report.getTaskId()));
            task.setStatus(TaskStatus.NEEDS_REVISION);
            taskRepository.save(task);
        }
    }

    private void recalculateMilestoneProgress(MilestoneEntity milestone, ReportEntity reviewedReport) {
        Map<String, ReportEntity> latestByScope = latestReportsByScope(milestone.getId(), reviewedReport);
        boolean everyLatestReportApproved = !latestByScope.isEmpty() && latestByScope.values().stream()
                .allMatch(report -> report.getStatus() == ReportStatus.APPROVED);
        List<TaskEntity> tasks = taskRepository
                .findByMilestoneIdAndDeletedFalseAndActiveTrueOrderByDeadlineAscCreatedAtAsc(milestone.getId());
        if (!tasks.isEmpty()) {
            long doneTasks = tasks.stream().filter(task -> task.getStatus() == TaskStatus.DONE).count();
            milestone.setProgressPercent((int) Math.round(doneTasks * 100.0 / tasks.size()));
            boolean allTasksDone = doneTasks == tasks.size();
            boolean everyTaskHasApprovedReport = !taskRepository
                    .existsTaskWithoutApprovedReportByMilestoneId(milestone.getId());
            milestone.setStatus(allTasksDone && everyTaskHasApprovedReport && everyLatestReportApproved
                    ? MilestoneStatus.COMPLETED
                    : MilestoneStatus.IN_PROGRESS);
            return;
        }

        long approvedReports = latestByScope.values().stream()
                .filter(report -> report.getStatus() == ReportStatus.APPROVED)
                .count();
        int progressPercent = latestByScope.isEmpty()
                ? 0
                : (int) Math.round(approvedReports * 100.0 / latestByScope.size());
        milestone.setProgressPercent(progressPercent);
        milestone.setStatus(!latestByScope.isEmpty() && approvedReports == latestByScope.size()
                ? MilestoneStatus.COMPLETED
                : MilestoneStatus.IN_PROGRESS);
    }

    private Map<String, ReportEntity> latestReportsByScope(Long milestoneId, ReportEntity reviewedReport) {
        Map<String, ReportEntity> latestByScope = reportRepository
                .findByMilestoneIdOrderByCreatedAtDescVersionDesc(milestoneId)
                .stream()
                .collect(Collectors.toMap(ReportEntity::getSubmissionScope, Function.identity(),
                        (first, second) -> first.getVersion() >= second.getVersion() ? first : second));
        latestByScope.merge(reviewedReport.getSubmissionScope(), reviewedReport,
                (first, second) -> first.getVersion() >= second.getVersion() ? first : second);
        return latestByScope;
    }

    private void assertCanSubmitReport(User currentUser, MilestoneEntity milestone, TaskEntity task) {
        if (!currentUser.hasRole("STUDENT")) {
            throw new AccessDeniedException("Only students may submit progress reports");
        }
        assertCanViewReports(currentUser, milestone);
        if (!currentUser.getId().equals(task.getAssigneeId())) {
            throw new AccessDeniedException("Students may submit reports only for tasks assigned to them");
        }
    }

    private GroupRole assertCanViewReports(User currentUser, MilestoneEntity milestone) {
        if (currentUser.hasRole("LAB_MANAGER")) {
            Laboratory managedLab = laboratoryRepository.findFirstByManagerIdAndDeletedFalse(currentUser.getId())
                    .orElseThrow(() -> new AccessDeniedException("Lab manager is not assigned to any lab"));
            if (milestone.getProject().getLab() != null
                    && managedLab.getId().equals(milestone.getProject().getLab().getId())) {
                return null;
            }
            throw new AccessDeniedException("Cannot access reports from another lab");
        }
        if (!currentUser.hasRole("STUDENT")) {
            throw new AccessDeniedException("Cannot access progress reports");
        }
        return groupMemberRepository
                .findActiveRoleByProjectIdAndUserId(milestone.getProject().getId(), currentUser.getId())
                .orElseThrow(() -> new AccessDeniedException("Cannot access reports for this project"));
    }

    private Laboratory assertManagerOwnsLab(User currentUser, Long labId) {
        if (!currentUser.hasRole("LAB_MANAGER")) {
            throw new AccessDeniedException("Only laboratory managers may view pending reports");
        }
        Laboratory managedLab = laboratoryRepository.findFirstByManagerIdAndDeletedFalse(currentUser.getId())
                .orElseThrow(() -> new AccessDeniedException("Lab manager is not assigned to any lab"));
        if (!managedLab.getId().equals(labId)) {
            throw new AccessDeniedException("Cannot access reports from another lab");
        }
        return managedLab;
    }

    private MilestoneEntity findMilestone(Long milestoneId) {
        return milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", milestoneId));
    }

    private Long resolveGroupId(Long projectId, MilestoneEntity milestone) {
        if (milestone.getProject().getGroup() != null) {
            return milestone.getProject().getGroup().getId();
        }
        return groupRepository.findByProjectIdAndDeletedFalseAndActiveTrue(projectId).stream()
                .findFirst()
                .map(GroupEntity::getId)
                .orElse(null);
    }

    private StoredReportFile storeReportFile(MultipartFile file, Long taskId, Integer version) {
        validateReportFile(file);
        String originalFileName = Paths.get(file.getOriginalFilename() == null ? "report" : file.getOriginalFilename())
                .getFileName()
                .toString();
        String storedFileName = version + "." + getExtension(originalFileName);
        Path baseDirectory = getReportStorageDirectory();
        Path taskDirectory = baseDirectory.resolve(String.valueOf(taskId)).normalize();
        if (!taskDirectory.startsWith(baseDirectory)) {
            throw new IllegalArgumentException("Invalid task report storage path");
        }
        Path target = taskDirectory.resolve(storedFileName).normalize();
        if (!target.startsWith(taskDirectory)) {
            throw new IllegalArgumentException("Invalid task report storage path");
        }
        try {
            Files.createDirectories(taskDirectory);
            Files.copy(file.getInputStream(), target);
        } catch (FileAlreadyExistsException ex) {
            throw new ReportVersionConflictException(taskId);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot store report file", ex);
        }
        return new StoredReportFile(
                "/storage/reports/" + taskId + "/" + storedFileName,
                originalFileName,
                file.getContentType(),
                file.getSize(),
                target
        );
    }

    private void deleteStoredFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // The submission remains failed; orphan cleanup can be retried operationally.
        }
    }

    private void validateReportFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File báo cáo là bắt buộc.");
        }
        if (file.getSize() > MAX_REPORT_FILE_SIZE) {
            throw new IllegalArgumentException("Dung lượng file không được vượt quá 10MB.");
        }
        String extension = getExtension(file.getOriginalFilename());
        String expectedContentType = ALLOWED_REPORT_TYPES.get(extension);
        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.ROOT);
        if (expectedContentType == null || !expectedContentType.equals(contentType)) {
            throw new IllegalArgumentException("Chỉ hỗ trợ file PDF, DOC hoặc DOCX.");
        }
    }

    private String normalizeEvidenceLink(String evidenceLink) {
        if (!StringUtils.hasText(evidenceLink)) {
            return null;
        }
        String value = evidenceLink.trim();
        try {
            URI uri = URI.create(value);
            if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Link minh chứng không hợp lệ.");
        }
        return value;
    }

    private String buildSubmissionScope(Long milestoneId, Long taskId, Long userId) {
        return "M:" + milestoneId + ":T:" + (taskId == null ? "_" : taskId) + ":U:" + userId;
    }

    private Path getReportStorageDirectory() {
        String configuredPath = StringUtils.hasText(reportStoragePath) ? reportStoragePath : "storage/reports";
        return Paths.get(configuredPath).toAbsolutePath().normalize();
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication is required");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", authentication.getName()));
    }

    private record StoredReportFile(String url, String originalFileName, String contentType, Long size, Path path) {
    }
}
