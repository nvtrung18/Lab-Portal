package com.web.labportalbackend.research.service.impl;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.admin.systemconfig.dto.SystemConfigResponse;
import com.web.labportalbackend.admin.systemconfig.service.SystemConfigService;
import com.web.labportalbackend.admin.audit.enums.AuditAction;
import com.web.labportalbackend.admin.audit.enums.AuditModule;
import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.common.exception.ReportVersionConflictException;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.notification.enums.NotificationEventType;
import com.web.labportalbackend.notification.enums.NotificationTargetModule;
import com.web.labportalbackend.notification.service.NotificationEmitter;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.dto.request.SubmitReportRequest;
import com.web.labportalbackend.research.dto.request.ReplaceReportRequest;
import com.web.labportalbackend.research.dto.request.LeaderReviewReportRequest;
import com.web.labportalbackend.research.dto.request.ManagerReviewReportRequest;
import com.web.labportalbackend.research.dto.response.ReportResponse;
import com.web.labportalbackend.research.dto.response.ReportFileDownload;
import com.web.labportalbackend.research.entity.CommentEntity;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.MilestoneEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.entity.ReportEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.enums.LeaderReportDecision;
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
    private final SystemConfigService systemConfigService;
    private final AuditLogService auditLogService;
    private final NotificationEmitter notificationEmitter;

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
        if (groupId != null && !isMemberOfGroup(currentUser.getId(), groupId)) {
            throw new AccessDeniedException("Cannot submit reports for this group");
        }
        validateTaskReportSubmissionState(task, currentUser.getId());
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

        if (task.getStatus() != TaskStatus.DONE && task.getStatus() != TaskStatus.CANCELLED) {
            task.setStatus(TaskStatus.IN_REVIEW);
            task.setProgressPercent(90);
            taskRepository.save(task);
        }

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
            auditLogService.log(
                    currentUser,
                    AuditAction.STUDENT_UPLOAD_REPORT,
                    AuditModule.REPORT,
                    "REPORT",
                    saved.getId(),
                    displayName(currentUser) + " đã nộp báo cáo cho nhiệm vụ " + task.getTitle() + "."
            );
            notificationEmitter.emit(saved.getSubmittedById(), NotificationEventType.REPORT_SUBMITTED,
                    "Report submitted", "Your report was submitted for review",
                    NotificationTargetModule.REPORT, saved.getId(), null);
            return enrich(ReportMapper.toResponse(saved, currentUser));
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
                .map(report -> enrich(ReportMapper.toResponse(report, findSubmitter(report))))
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
                .map(report -> enrich(toDetailedResponse(report, milestone)))
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
                .map(report -> enrich(ReportMapper.toResponse(report)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportResponse> getReportsByGroup(Long groupId) {
        User currentUser = getCurrentUser();
        GroupEntity group = groupRepository.findByIdAndDeletedFalseAndActiveTrue(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Research group", groupId));
        if (currentUser.hasRole("LAB_MANAGER")) {
            Laboratory managedLab = laboratoryRepository.findFirstByManagerIdAndDeletedFalse(currentUser.getId())
                    .orElseThrow(() -> new AccessDeniedException("Lab manager is not assigned to any lab"));
            if (group.getLab() == null || !managedLab.getId().equals(group.getLab().getId())) {
                throw new AccessDeniedException("Cannot access reports from another lab");
            }
        } else if (currentUser.hasRole("STUDENT")) {
            GroupRole role = groupMemberRepository.findActiveRoleByGroupIdAndUserId(groupId, currentUser.getId())
                    .orElseThrow(() -> new AccessDeniedException("Cannot access reports for this group"));
            if (role != GroupRole.LEADER) {
                throw new AccessDeniedException("Only the group leader may view all member reports");
            }
        } else {
            throw new AccessDeniedException("Cannot access group reports");
        }

        return reportRepository.findReportsByGroupScope(groupId)
                .stream()
                .map(report -> {
                    MilestoneEntity milestone = findMilestone(report.getMilestoneId());
                    TaskEntity task = report.getTaskId() == null
                            ? null
                            : taskRepository.findById(report.getTaskId()).orElse(null);
                    return enrich(ReportMapper.toResponse(report, findSubmitter(report), group, milestone, task));
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportResponse> getMyReportsByGroup(Long groupId) {
        User currentUser = getCurrentUser();
        if (!currentUser.hasRole("STUDENT")) {
            throw new AccessDeniedException("Only students may access their report history");
        }
        GroupEntity group = groupRepository.findByIdAndDeletedFalseAndActiveTrue(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Research group", groupId));

        if (!isMemberOfGroup(currentUser.getId(), groupId)) {
            throw new AccessDeniedException("Cannot access reports for this group");
        }

        return reportRepository.findOwnReportsByGroupScope(
                        groupId,
                        currentUser.getId()
                )
                .stream()
                .map(report -> {
                    MilestoneEntity milestone = findMilestone(report.getMilestoneId());
                    TaskEntity task = report.getTaskId() == null
                            ? null
                            : taskRepository.findById(report.getTaskId()).orElse(null);
                    return enrich(ReportMapper.toResponse(report, currentUser, group, milestone, task));
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportResponse> getPendingManagerReviewByLab(Long labId) {
        User currentUser = getCurrentUser();
        Laboratory managedLab = assertManagerOwnsLab(currentUser, labId);
        boolean requireLeaderReview = systemConfig().research().requireLeaderReviewBeforeManagerReview();
        return reportRepository.findPendingManagerReviewByLabId(managedLab.getId(), requireLeaderReview).stream()
                .map(report -> {
                    MilestoneEntity milestone = findMilestone(report.getMilestoneId());
                    GroupEntity group = report.getGroupId() == null
                            ? null
                            : groupRepository.findByIdAndDeletedFalseAndActiveTrue(report.getGroupId()).orElse(null);
                    TaskEntity task = report.getTaskId() == null
                            ? null
                            : taskRepository.findById(report.getTaskId()).orElse(null);
                    return enrich(ReportMapper.toResponse(report, findSubmitter(report), group, milestone, task));
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
        String comment = request.getComment().trim();
        Long groupId = report.getGroupId();

        if (!currentUser.hasRole("STUDENT") || groupId == null || !isLeaderOfGroup(currentUser.getId(), groupId)) {
            throw new AccessDeniedException("Bạn không có quyền xử lý báo cáo này.");
        }
        if (currentUser.getId().equals(report.getSubmittedById())) {
            throw new AccessDeniedException("Bạn không thể tự xử lý báo cáo của chính mình. Báo cáo này sẽ do quản lý PTN duyệt.");
        }

        assertCanViewReports(currentUser, findMilestone(report.getMilestoneId()));
        assertLatestSubmissionVersion(report);

        if (report.getStatus() != ReportStatus.SUBMITTED) {
            throw new IllegalArgumentException("Chỉ có thể xử lý báo cáo đang chờ trưởng nhóm kiểm tra.");
        }

        LeaderReportDecision decision = request.getDecision();
        if (decision == LeaderReportDecision.ACCEPT) {
            report.setStatus(ReportStatus.LEADER_REVIEWED);
        } else if (decision == LeaderReportDecision.REQUEST_REVISION) {
            report.setStatus(ReportStatus.NEEDS_REVISION);
            if (report.getTaskId() != null) {
                TaskEntity task = taskRepository.findById(report.getTaskId())
                        .orElseThrow(() -> new ResourceNotFoundException("Task", report.getTaskId()));
                if (task.getStatus() == TaskStatus.IN_REVIEW || task.getStatus() == TaskStatus.IN_PROGRESS) {
                    task.setStatus(TaskStatus.NEEDS_REVISION);
                    taskRepository.save(task);
                }
            }
        } else {
            report.setStatus(ReportStatus.LEADER_REJECTED);
            if (report.getTaskId() != null) {
                TaskEntity task = taskRepository.findById(report.getTaskId())
                        .orElseThrow(() -> new ResourceNotFoundException("Task", report.getTaskId()));
                task.setStatus(TaskStatus.NEEDS_REVISION);
                taskRepository.save(task);
            }
        }

        report.setLeaderReviewedAt(Instant.now());
        report.setLeaderReviewerId(currentUser.getId());
        report.setLeaderComment(comment);

        String commentPrefix;
        if (decision == LeaderReportDecision.ACCEPT) {
            commentPrefix = "Trưởng nhóm đã chấp nhận báo cáo: ";
        } else if (decision == LeaderReportDecision.REQUEST_REVISION) {
            commentPrefix = "Trưởng nhóm yêu cầu nộp lại báo cáo: ";
        } else {
            commentPrefix = "Trưởng nhóm từ chối báo cáo: ";
        }
        saveReviewComment(reportId, currentUser.getId(), commentPrefix + comment);

        ReportEntity saved = reportRepository.save(report);
        createSystemLogSafely(
                saved.getProjectId(),
                groupId,
                saved.getMilestoneId(),
                saved.getTaskId(),
                currentUser.getId(),
                displayName(currentUser) + " đã kiểm tra báo cáo v" + saved.getVersion() + ".",
                comment,
                ResearchLogVisibility.GROUP
        );
        auditLogService.log(
                currentUser,
                AuditAction.LEADER_REVIEW_REPORT,
                AuditModule.REPORT,
                "REPORT",
                saved.getId(),
                displayName(currentUser) + " đã kiểm tra báo cáo v" + saved.getVersion() + "."
        );
        emitReportReviewed(saved);
        return enrich(ReportMapper.toResponse(saved));
    }

    @Override
    @Transactional
    public ReportResponse managerReview(Long reportId, ManagerReviewReportRequest request) {
        ReportEntity report = findReport(reportId);
        User currentUser = getCurrentUser();
        MilestoneEntity milestone = findMilestone(report.getMilestoneId());

        if (!currentUser.hasRole("LAB_MANAGER")) {
            throw new AccessDeniedException("Bạn không có quyền duyệt báo cáo này.");
        }

        Laboratory managedLab = laboratoryRepository.findFirstByManagerIdAndDeletedFalse(currentUser.getId())
                .orElse(null);
        if (managedLab == null || milestone.getProject().getLab() == null
                || !managedLab.getId().equals(milestone.getProject().getLab().getId())) {
            throw new AccessDeniedException("Bạn không có quyền duyệt báo cáo này.");
        }

        assertLatestSubmissionVersion(report);

        boolean requireLeaderReview = systemConfig().research().requireLeaderReviewBeforeManagerReview();
        boolean isSubmittedByLeader = false;
        if (report.getGroupId() != null) {
            isSubmittedByLeader = isLeaderOfGroup(report.getSubmittedById(), report.getGroupId());
        }

        if (requireLeaderReview && !isSubmittedByLeader) {
            if (report.getStatus() != ReportStatus.LEADER_REVIEWED) {
                throw new IllegalArgumentException("Báo cáo cần được trưởng nhóm kiểm tra trước.");
            }
        } else {
            if (report.getStatus() != ReportStatus.SUBMITTED && report.getStatus() != ReportStatus.LEADER_REVIEWED) {
                throw new IllegalArgumentException("Báo cáo phải ở trạng thái chờ duyệt hoặc đã được trưởng nhóm kiểm tra.");
            }
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
            report.setStatus(ReportStatus.MANAGER_REJECTED);
            requestRevisionForSubmittedTask(report);
        }

        report.setManagerReviewedAt(Instant.now());
        report.setManagerReviewerId(currentUser.getId());
        report.setManagerComment(comment);
        milestone.setManagerComment(comment);

        String commentPrefix;
        if (decision == ManagerReportDecision.APPROVE) {
            commentPrefix = "Quản lý PTN đã chấp nhận báo cáo: ";
        } else if (decision == ManagerReportDecision.REQUEST_REVISION) {
            commentPrefix = "Quản lý PTN yêu cầu nộp lại báo cáo: ";
        } else {
            commentPrefix = "Quản lý PTN từ chối báo cáo: ";
        }
        saveReviewComment(reportId, currentUser.getId(), commentPrefix + comment);
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
        auditLogService.log(
                currentUser,
                AuditAction.MANAGER_REVIEW_REPORT,
                AuditModule.REPORT,
                "REPORT",
                report.getId(),
                displayName(currentUser) + " đã " + managerDecisionLabel(decision)
                        + " báo cáo của " + displayName(submitter) + "."
        );
        emitReportReviewed(report);
        return enrich(ReportMapper.toResponse(report));
    }

    private void emitReportReviewed(ReportEntity report) {
        notificationEmitter.emit(report.getSubmittedById(), NotificationEventType.REPORT_REVIEWED,
                "Report reviewed", "Your report status changed to " + report.getStatus().name(),
                NotificationTargetModule.REPORT, report.getId(), null);
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
            throw new IllegalArgumentException("Chỉ có thể xử lý phiên bản báo cáo mới nhất.");
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
        Long groupId = resolveGroupId(milestone.getProject().getId(), milestone);
        if (groupId != null && !isMemberOfGroup(currentUser.getId(), groupId)) {
            throw new AccessDeniedException("Cannot submit reports for this group");
        }
        if (!currentUser.getId().equals(task.getAssigneeId())) {
            throw new AccessDeniedException("Bạn không có quyền nộp báo cáo cho nhiệm vụ này.");
        }
    }

    private void validateTaskReportSubmissionState(TaskEntity task, Long currentUserId) {
        TaskStatus taskStatus = task.getStatus() == null ? TaskStatus.TODO : task.getStatus();
        if (taskStatus == TaskStatus.TODO) {
            throw new IllegalArgumentException("Bạn cần bắt đầu thực hiện nhiệm vụ trước khi nộp báo cáo.");
        }

        ReportStatus latestStatus = reportRepository
                .findTopByTaskIdAndSubmittedByIdAndDeletedFalseAndActiveTrueOrderByVersionDescCreatedAtDesc(
                        task.getId(),
                        currentUserId
                )
                .map(ReportEntity::getStatus)
                .orElse(null);

        if (latestStatus == null) {
            if (taskStatus == TaskStatus.IN_PROGRESS || taskStatus == TaskStatus.NEEDS_REVISION) {
                return;
            }
            throw new IllegalArgumentException("Bạn cần bắt đầu thực hiện nhiệm vụ trước khi nộp báo cáo.");
        }

        if (latestStatus == ReportStatus.SUBMITTED) {
            throw new IllegalArgumentException("Báo cáo đang chờ trưởng nhóm kiểm tra, chưa thể nộp lại.");
        }
        if (latestStatus == ReportStatus.LEADER_REVIEWED) {
            throw new IllegalArgumentException("Báo cáo đang chờ quản lý duyệt, chưa thể nộp lại.");
        }
        if (latestStatus == ReportStatus.APPROVED) {
            throw new IllegalArgumentException("Báo cáo đã được duyệt, không thể nộp lại.");
        }
        if (latestStatus == ReportStatus.NEEDS_REVISION
                || latestStatus == ReportStatus.LEADER_REJECTED
                || latestStatus == ReportStatus.MANAGER_REJECTED) {
            return;
        }

        throw new IllegalArgumentException("Trạng thái báo cáo hiện tại không cho phép nộp lại.");
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
        if (milestoneId == null) {
            throw new IllegalArgumentException("Report operation requires the task to have a milestone");
        }
        return milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", milestoneId));
    }

    private Long resolveGroupId(Long projectId, MilestoneEntity milestone) {
        if (milestone.getGroup() != null) {
            return milestone.getGroup().getId();
        }
        if (milestone.getProject().getGroup() != null) {
            return milestone.getProject().getGroup().getId();
        }
        return groupRepository.findByProjectIdAndDeletedFalseAndActiveTrue(projectId).stream()
                .findFirst()
                .map(GroupEntity::getId)
                .orElse(null);
    }

    private Long resolveReportGroupId(ReportEntity report) {
        if (report.getGroupId() != null) {
            return report.getGroupId();
        }

        MilestoneEntity milestone = findMilestone(report.getMilestoneId());
        return resolveGroupId(report.getProjectId(), milestone);
    }

    private boolean isMemberOfGroup(Long userId, Long groupId) {
        return groupMemberRepository.existsByGroupIdAndUserIdAndActiveTrueAndDeletedFalse(groupId, userId);
    }

    @SuppressWarnings("unused")
    private boolean isLeaderOfGroup(Long userId, Long groupId) {
        return groupMemberRepository.findActiveRoleByGroupIdAndUserId(groupId, userId)
                .filter(role -> role == GroupRole.LEADER)
                .isPresent();
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
        SystemConfigResponse.UploadConfig uploadConfig = systemConfig().upload();
        long maxBytes = uploadConfig.reportMaxSizeMb() * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("Dung lượng file không được vượt quá "
                    + uploadConfig.reportMaxSizeMb() + "MB.");
        }
        String extension = getExtension(file.getOriginalFilename());
        if (!uploadConfig.reportAllowedTypes().contains(extension)) {
            throw new IllegalArgumentException("Định dạng file báo cáo không được hỗ trợ.");
        }
        String expectedContentType = ALLOWED_REPORT_TYPES.get(extension);
        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.ROOT);
        if (expectedContentType != null && !expectedContentType.equals(contentType)) {
            throw new IllegalArgumentException("Chỉ hỗ trợ file PDF, DOC hoặc DOCX.");
        }
    }

    private SystemConfigResponse systemConfig() {
        return systemConfigService.getConfig();
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

    private ReportResponse enrich(ReportResponse response) {
        if (response == null) return null;
        boolean hasNewer = reportRepository.existsBySubmissionScopeAndVersionGreaterThan(
                buildSubmissionScope(response.getMilestoneId(), response.getTaskId(), response.getSubmittedById()),
                response.getVersion()
        );
        response.setIsLatestVersion(!hasNewer);

        if (response.getGroupId() != null) {
            boolean isLeader = isLeaderOfGroup(response.getSubmittedById(), response.getGroupId());
            response.setSubmittedByGroupRole(isLeader ? "LEADER" : "MEMBER");
        } else {
            response.setSubmittedByGroupRole("MEMBER");
        }
        return response;
    }

    @Override
    @Transactional
    public ReportResponse replaceReport(Long reportId, ReplaceReportRequest request, MultipartFile file) {
        ReportEntity report = findReport(reportId);
        User currentUser = getCurrentUser();

        if (!currentUser.getId().equals(report.getSubmittedById())) {
            throw new AccessDeniedException("Bạn không có quyền cập nhật báo cáo này.");
        }

        if (report.getStatus() != ReportStatus.SUBMITTED) {
            throw new IllegalArgumentException("Báo cáo đã được xử lý, không thể cập nhật phiên bản này.");
        }

        assertLatestSubmissionVersion(report);

        report.setTitle(request.getTitle().trim());
        report.setContentDone(request.getContentDone().trim());
        report.setResult(request.getResult().trim());
        report.setDifficulty(request.getDifficulty().trim());
        report.setNextPlan(request.getNextPlan().trim());
        report.setSelfAssessment(request.getSelfAssessment().trim());
        report.setEvidenceLink(normalizeEvidenceLink(request.getEvidenceLink()));

        if (file != null && !file.isEmpty()) {
            Path oldPath = getReportStorageDirectory()
                    .resolve(String.valueOf(report.getTaskId()))
                    .resolve(report.getVersion() + "." + getExtension(report.getFileName()))
                    .normalize();
            deleteStoredFile(oldPath);

            StoredReportFile storedFile = storeReportFile(file, report.getTaskId(), report.getVersion());
            report.setFileUrl(storedFile.url());
            report.setFileName(storedFile.originalFileName());
            report.setFileType(storedFile.contentType());
            report.setFileSize(storedFile.size());
        }

        report.setUpdatedAt(Instant.now());
        ReportEntity saved = reportRepository.save(report);

        createSystemLogSafely(
                saved.getProjectId(),
                saved.getGroupId(),
                saved.getMilestoneId(),
                saved.getTaskId(),
                currentUser.getId(),
                displayName(currentUser) + " đã cập nhật báo cáo v" + saved.getVersion() + ".",
                saved.getResult(),
                ResearchLogVisibility.GROUP
        );

        auditLogService.log(
                currentUser,
                AuditAction.STUDENT_UPLOAD_REPORT,
                AuditModule.REPORT,
                "REPORT",
                saved.getId(),
                displayName(currentUser) + " đã cập nhật báo cáo v" + saved.getVersion() + "."
        );

        return enrich(ReportMapper.toResponse(saved, currentUser));
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
