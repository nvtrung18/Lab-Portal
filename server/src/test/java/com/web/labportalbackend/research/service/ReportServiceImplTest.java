package com.web.labportalbackend.research.service;

import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.admin.systemconfig.dto.SystemConfigResponse;
import com.web.labportalbackend.admin.systemconfig.service.SystemConfigService;
import com.web.labportalbackend.common.exception.ReportVersionConflictException;
import com.web.labportalbackend.notification.service.NotificationEmitter;
import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.research.dto.request.SubmitReportRequest;
import com.web.labportalbackend.research.dto.request.ReplaceReportRequest;
import com.web.labportalbackend.research.dto.request.LeaderReviewReportRequest;
import com.web.labportalbackend.research.dto.request.ManagerReviewReportRequest;
import com.web.labportalbackend.research.dto.response.ReportResponse;
import com.web.labportalbackend.research.dto.response.ReportFileDownload;
import com.web.labportalbackend.research.entity.MilestoneEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.ReportEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.enums.ReportStatus;
import com.web.labportalbackend.research.enums.LeaderReportDecision;
import com.web.labportalbackend.research.enums.ManagerReportDecision;
import com.web.labportalbackend.research.enums.MilestoneStatus;
import com.web.labportalbackend.research.enums.TaskStatus;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.CommentRepository;
import com.web.labportalbackend.research.repository.MilestoneRepository;
import com.web.labportalbackend.research.repository.ReportRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
import com.web.labportalbackend.research.service.impl.ReportServiceImpl;
import com.web.labportalbackend.research.service.ResearchLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock
    NotificationEmitter notificationEmitter;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private ReportServiceImpl reportService;

    @Mock
    private MilestoneRepository milestoneRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private LaboratoryRepository laboratoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ResearchLogService researchLogService;

    @Mock
    private SystemConfigService systemConfigService;

    @Mock
    private AuditLogService auditLogService;

    private final List<Path> storedFiles = new ArrayList<>();
    private Path testReportStorageDir;

    @BeforeEach
    void cleanStorageDirectory() throws IOException {
        deleteDirectoryIfExists(Paths.get("storage/reports").toAbsolutePath().normalize());
        deleteDirectoryIfExists(Paths.get("../storage/reports").toAbsolutePath().normalize());
        testReportStorageDir = Files.createTempDirectory("report-service-test-");
        ReflectionTestUtils.setField(reportService, "reportStoragePath", testReportStorageDir.toString());
    }

    @BeforeEach
    void stubSystemConfig() {
        SystemConfigResponse config = new SystemConfigResponse(
                null,
                null,
                null,
                new SystemConfigResponse.UploadConfig(
                        10,
                        10,
                        List.of("pdf", "doc", "docx"),
                        List.of("pdf", "zip")
                ),
                new SystemConfigResponse.ResearchConfig(10, false, false, false)
        );
        lenient().when(systemConfigService.getConfig()).thenReturn(config);
    }

    @BeforeEach
    void authenticateAssignedMember() {
        User student = new User();
        student.setId(3L);
        student.setUsername("student");
        student.addRole(new Role("STUDENT", "STUDENT"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("student", null, List.of())
        );
        lenient().when(userRepository.findByUsername("student")).thenReturn(Optional.of(student));
        lenient().when(userRepository.findById(3L)).thenReturn(Optional.of(student));

        ProjectEntity project = new ProjectEntity();
        project.setId(50L);
        MilestoneEntity milestone = new MilestoneEntity();
        milestone.setId(5L);
        milestone.setTitle("Implementation milestone");
        milestone.setProject(project);
        milestone.setAssignedToStudent(student);
        lenient().when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(5L)).thenReturn(Optional.of(milestone));
        lenient().when(groupMemberRepository.findActiveRoleByProjectIdAndUserId(50L, 3L))
                .thenReturn(Optional.of(GroupRole.MEMBER));
        lenient().when(reportRepository
                .findTopByTaskIdAndSubmittedByIdAndDeletedFalseAndActiveTrueOrderByVersionDescCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        storedFiles.forEach(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (java.io.IOException ignored) {
                // Best-effort removal of files created by the unit test.
            }
        });
        storedFiles.clear();
        if (testReportStorageDir != null) {
            try {
                deleteDirectoryIfExists(testReportStorageDir);
            } catch (java.io.IOException ignored) {
                // Best-effort cleanup of the temporary report storage directory.
            }
        }
    }

    @Test
    void submitReport_createsVersionOneWhenTaskHasNoPreviousReport() {
        SubmitReportRequest request = request(10L);
        TaskEntity task = task(10L);

        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(reportRepository.findMaxVersionByTaskIdAndSubmittedById(10L, 3L)).thenReturn(Optional.empty());
        when(reportRepository.saveAndFlush(any(ReportEntity.class))).thenAnswer(invocation -> {
            ReportEntity report = invocation.getArgument(0);
            report.setId(100L);
            trackStoredFile(report);
            return report;
        });

        ReportResponse response = reportService.submitReport(request, file("report-v1.pdf"));

        assertEquals(100L, response.getId());
        assertEquals(10L, response.getTaskId());
        assertEquals(1, response.getVersion());
        assertEquals("report-v1.pdf", response.getFileName());
        assertEquals("/storage/reports/10/1.pdf", response.getFileUrl());
        assertEquals(ReportStatus.SUBMITTED, response.getStatus());

        ArgumentCaptor<ReportEntity> captor = ArgumentCaptor.forClass(ReportEntity.class);
        verify(reportRepository).saveAndFlush(captor.capture());
        assertEquals(1, captor.getValue().getVersion());
        assertEquals(5L, captor.getValue().getMilestoneId());
        assertEquals(3L, captor.getValue().getSubmittedById());
    }

    @Test
    void submitReport_rejectsTaskWithoutMilestoneBeforeRepositoryNullLookup() {
        TaskEntity task = task(10L);
        task.setMilestoneId(null);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> reportService.submitReport(request(10L), file("report.pdf")));

        assertEquals("Report operation requires the task to have a milestone", error.getMessage());
        verify(milestoneRepository, never()).findByIdAndDeletedFalseAndActiveTrue(null);
        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void submitReport_createsNextVersionWhenTaskHasPreviousReport() {
        SubmitReportRequest request = request(10L);
        TaskEntity task = task(10L);

        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(reportRepository.findMaxVersionByTaskIdAndSubmittedById(10L, 3L)).thenReturn(Optional.of(1));
        when(reportRepository.saveAndFlush(any(ReportEntity.class))).thenAnswer(invocation -> {
            ReportEntity report = invocation.getArgument(0);
            trackStoredFile(report);
            return report;
        });

        ReportResponse response = reportService.submitReport(request, file("report-v2.pdf"));

        assertEquals(2, response.getVersion());

        ArgumentCaptor<ReportEntity> captor = ArgumentCaptor.forClass(ReportEntity.class);
        verify(reportRepository).saveAndFlush(captor.capture());
        assertEquals(2, captor.getValue().getVersion());
    }

    @Test
    void submitReport_acceptsDocxAndPreservesOriginalFileName() {
        SubmitReportRequest request = request(10L);
        TaskEntity task = task(10L);
        MockMultipartFile document = new MockMultipartFile(
                "file",
                "report-week-2.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "docx-content".getBytes()
        );

        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(reportRepository.findMaxVersionByTaskIdAndSubmittedById(10L, 3L)).thenReturn(Optional.empty());
        when(reportRepository.saveAndFlush(any(ReportEntity.class))).thenAnswer(invocation -> {
            ReportEntity report = invocation.getArgument(0);
            trackStoredFile(report);
            return report;
        });

        ReportResponse response = reportService.submitReport(request, document);

        assertEquals("report-week-2.docx", response.getFileName());
        assertEquals("/storage/reports/10/1.docx", response.getFileUrl());
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", response.getFileType());
    }

    @Test
    void submitReport_throwsConflictWhenUniqueVersionConstraintFails() {
        SubmitReportRequest request = request(10L);
        TaskEntity task = task(10L);

        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(reportRepository.findMaxVersionByTaskIdAndSubmittedById(10L, 3L)).thenReturn(Optional.of(1));
        when(reportRepository.saveAndFlush(any(ReportEntity.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate report version"));

        assertThrows(ReportVersionConflictException.class, () -> reportService.submitReport(request, file("report-v2.pdf")));
    }

    @Test
    void getReportsByTask_returnsReportsInRepositoryOrder() {
        ReportEntity newer = report(2L, 10L, 2);
        ReportEntity older = report(1L, 10L, 1);

        when(taskRepository.findById(10L)).thenReturn(Optional.of(task(10L)));
        when(reportRepository.findByTaskIdOrderByVersionDesc(10L)).thenReturn(List.of(newer, older));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user(3L, "student", "Nguyễn Văn A", "a@labportal.com")));

        List<ReportResponse> responses = reportService.getReportsByTask(10L);

        assertEquals(2, responses.size());
        assertEquals(2, responses.get(0).getVersion());
        assertEquals(1, responses.get(1).getVersion());
        assertEquals("Nguyễn Văn A", responses.get(0).getSubmittedByName());
        verify(reportRepository).findByTaskIdOrderByVersionDesc(10L);
    }

    @Test
    void downloadReportFile_returnsStoredAttachmentForAssignedMember() {
        SubmitReportRequest request = request(10L);
        TaskEntity task = task(10L);
        ReportEntity[] storedReport = new ReportEntity[1];

        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(reportRepository.findMaxVersionByTaskIdAndSubmittedById(10L, 3L)).thenReturn(Optional.empty());
        when(reportRepository.saveAndFlush(any(ReportEntity.class))).thenAnswer(invocation -> {
            ReportEntity report = invocation.getArgument(0);
            report.setId(100L);
            storedReport[0] = report;
            trackStoredFile(report);
            return report;
        });

        reportService.submitReport(request, file("member-report.pdf"));
        when(reportRepository.findById(100L)).thenReturn(Optional.of(storedReport[0]));

        ReportFileDownload download = reportService.downloadReportFile(100L);

        assertEquals("member-report.pdf", download.fileName());
        assertEquals("application/pdf", download.fileType());
        assertTrue(download.resource().exists());
    }

    @Test
    void downloadReportFile_rejectsMemberForAnotherStudentsTask() {
        TaskEntity otherTask = task(10L);
        otherTask.setAssigneeId(7L);
        ReportEntity report = report(100L, 10L, 1);
        report.setSubmittedById(7L);

        when(reportRepository.findById(100L)).thenReturn(Optional.of(report));
        when(taskRepository.findById(10L)).thenReturn(Optional.of(otherTask));

        assertThrows(AccessDeniedException.class, () -> reportService.downloadReportFile(100L));
    }

    @Test
    void getReportsByMilestone_returnsOnlyOwnHistoryForMember() {
        when(reportRepository.findByMilestoneIdAndSubmittedByIdOrderByCreatedAtDescVersionDesc(5L, 3L))
                .thenReturn(List.of(report(1L, null, 1)));

        List<ReportResponse> responses = reportService.getReportsByMilestone(5L);

        assertEquals(1, responses.size());
        assertEquals("Implementation milestone", responses.get(0).getMilestoneTitle());
        verify(reportRepository).findByMilestoneIdAndSubmittedByIdOrderByCreatedAtDescVersionDesc(5L, 3L);
        verify(reportRepository, never()).findByMilestoneIdOrderByCreatedAtDescVersionDesc(any());
    }

    @Test
    void getMyReportsByMilestone_returnsOnlyCurrentStudentsVersions() {
        ReportEntity versionTwo = report(2L, null, 2);
        ReportEntity versionOne = report(1L, null, 1);
        when(reportRepository.findByMilestoneIdAndSubmittedByIdOrderByCreatedAtDescVersionDesc(5L, 3L))
                .thenReturn(List.of(versionTwo, versionOne));

        List<ReportResponse> responses = reportService.getMyReportsByMilestone(5L);

        assertEquals(2, responses.size());
        assertEquals(2, responses.get(0).getVersion());
        assertEquals(1, responses.get(1).getVersion());
        verify(reportRepository).findByMilestoneIdAndSubmittedByIdOrderByCreatedAtDescVersionDesc(5L, 3L);
    }

    @Test
    void getMyReportsByMilestone_rejectsUnassignedMemberMilestone() {
        MilestoneEntity milestone = milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(5L).orElseThrow();
        milestone.setAssignedToStudent(null);

        assertThrows(AccessDeniedException.class, () -> reportService.getMyReportsByMilestone(5L));

        verify(reportRepository, never())
                .findByMilestoneIdAndSubmittedByIdOrderByCreatedAtDescVersionDesc(any(), any());
    }

    @Test
    void getReportsByGroup_returnsAllMemberReportsWithSubmitterForLeader() {
        User leader = authenticate(7L, "leader", "STUDENT");
        GroupEntity group = new GroupEntity();
        group.setId(90L);
        group.setName("Nhóm dữ liệu");
        User firstMember = user(3L, "student", "Thành viên A", "a@labportal.com");
        User secondMember = user(4L, "student2", "Thành viên B", "b@labportal.com");
        ReportEntity first = report(100L, 10L, 1);
        ReportEntity second = report(101L, 11L, 1);
        second.setSubmittedById(4L);

        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(90L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findActiveRoleByGroupIdAndUserId(90L, leader.getId()))
                .thenReturn(Optional.of(GroupRole.LEADER));
        when(reportRepository.findReportsByGroupScope(90L))
                .thenReturn(List.of(first, second));
        when(userRepository.findById(3L)).thenReturn(Optional.of(firstMember));
        when(userRepository.findById(4L)).thenReturn(Optional.of(secondMember));
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task(10L)));
        when(taskRepository.findById(11L)).thenReturn(Optional.of(task(11L)));

        List<ReportResponse> responses = reportService.getReportsByGroup(90L);

        assertEquals(2, responses.size());
        assertEquals("Thành viên A", responses.get(0).getSubmittedByName());
        assertEquals("Nhóm dữ liệu", responses.get(0).getGroupName());
        assertEquals("Analyze result", responses.get(0).getTaskTitle());
        assertEquals("b@labportal.com", responses.get(1).getSubmittedByEmail());
    }

    @Test
    void getReportsByGroup_rejectsRegularMember() {
        GroupEntity group = new GroupEntity();
        group.setId(90L);
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(90L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findActiveRoleByGroupIdAndUserId(90L, 3L))
                .thenReturn(Optional.of(GroupRole.MEMBER));

        assertThrows(AccessDeniedException.class, () -> reportService.getReportsByGroup(90L));

        verify(reportRepository, never()).findReportsByGroupScope(any());
    }

    @Test
    void getPendingManagerReviewByLab_returnsLeaderReviewedReportsForManagedLab() {
        User manager = authenticate(2L, "manager", "LAB_MANAGER");
        Laboratory lab = new Laboratory();
        lab.setId(1L);
        ReportEntity report = report(100L, 10L, 1);
        report.setStatus(ReportStatus.LEADER_REVIEWED);
        User submitter = user(3L, "student", "Thành viên A", "a@labportal.com");
        GroupEntity group = new GroupEntity();
        group.setId(90L);
        group.setName("Nhóm AI");
        MilestoneEntity milestone = managerMilestone(lab);
        milestone.setTitle("Mốc kiểm thử");
        TaskEntity task = task(10L);

        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())).thenReturn(Optional.of(lab));
        when(reportRepository.findPendingManagerReviewByLabId(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(List.of(report));
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(5L)).thenReturn(Optional.of(milestone));
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(90L)).thenReturn(Optional.of(group));
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(userRepository.findById(3L)).thenReturn(Optional.of(submitter));

        List<ReportResponse> responses = reportService.getPendingManagerReviewByLab(1L);

        assertEquals(1, responses.size());
        assertEquals("Nhóm AI", responses.get(0).getGroupName());
        assertEquals("Mốc kiểm thử", responses.get(0).getMilestoneTitle());
        assertEquals("Analyze result", responses.get(0).getTaskTitle());
    }

    @Test
    void getPendingManagerReviewByLab_rejectsAnotherManagedLab() {
        User manager = authenticate(2L, "manager", "LAB_MANAGER");
        Laboratory managedLab = new Laboratory();
        managedLab.setId(1L);
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId()))
                .thenReturn(Optional.of(managedLab));

        assertThrows(AccessDeniedException.class, () -> reportService.getPendingManagerReviewByLab(2L));

        verify(reportRepository, never()).findPendingManagerReviewByLabId(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void submitReport_rejectsSubmissionWithoutTask() {
        SubmitReportRequest request = request(null);

        assertThrows(IllegalArgumentException.class, () -> reportService.submitReport(request, file("milestone.pdf")));
        verify(taskRepository, never()).findById(any());
        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void submitReport_rejectsTaskAssignedToAnotherStudent() {
        SubmitReportRequest request = request(10L);
        TaskEntity task = task(10L);
        task.setAssigneeId(7L);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));

        assertThrows(
                AccessDeniedException.class,
                () -> reportService.submitReport(request, file("another-student.pdf"))
        );

        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void submitReport_rejectsTodoTaskBeforeStart() {
        SubmitReportRequest request = request(10L);
        TaskEntity task = task(10L);
        task.setStatus(TaskStatus.TODO);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> reportService.submitReport(request, file("todo-task.pdf"))
        );

        assertEquals("Bạn cần bắt đầu thực hiện nhiệm vụ trước khi nộp báo cáo.", exception.getMessage());
        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void submitReport_rejectsResubmitWhenLatestReportSubmitted() {
        assertSubmitRejectedByLatestStatus(
                ReportStatus.SUBMITTED,
                "Báo cáo đang chờ trưởng nhóm kiểm tra, chưa thể nộp lại."
        );
    }

    @Test
    void submitReport_rejectsResubmitWhenLatestReportLeaderReviewed() {
        assertSubmitRejectedByLatestStatus(
                ReportStatus.LEADER_REVIEWED,
                "Báo cáo đang chờ quản lý duyệt, chưa thể nộp lại."
        );
    }

    @Test
    void submitReport_rejectsResubmitWhenLatestReportApproved() {
        assertSubmitRejectedByLatestStatus(
                ReportStatus.APPROVED,
                "Báo cáo đã được duyệt, không thể nộp lại."
        );
    }

    @Test
    void submitReport_allowsResubmitWhenLatestReportNeedsRevision() {
        SubmitReportRequest request = request(10L);
        TaskEntity task = task(10L);
        task.setStatus(TaskStatus.NEEDS_REVISION);
        ReportEntity latest = report(99L, 10L, 1);
        latest.setStatus(ReportStatus.NEEDS_REVISION);

        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(reportRepository.findTopByTaskIdAndSubmittedByIdAndDeletedFalseAndActiveTrueOrderByVersionDescCreatedAtDesc(10L, 3L))
                .thenReturn(Optional.of(latest));
        when(reportRepository.findMaxVersionByTaskIdAndSubmittedById(10L, 3L)).thenReturn(Optional.of(1));
        when(reportRepository.saveAndFlush(any(ReportEntity.class))).thenAnswer(invocation -> {
            ReportEntity report = invocation.getArgument(0);
            trackStoredFile(report);
            return report;
        });

        ReportResponse response = reportService.submitReport(request, file("report-v2.pdf"));

        assertEquals(2, response.getVersion());
        assertEquals(ReportStatus.SUBMITTED, response.getStatus());
        assertEquals(TaskStatus.IN_REVIEW, task.getStatus());
    }

    @Test
    void submitReport_rejectsExtensionAndContentTypeMismatch() {
        SubmitReportRequest request = request(10L);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task(10L)));
        when(reportRepository.findMaxVersionByTaskIdAndSubmittedById(10L, 3L)).thenReturn(Optional.empty());
        MockMultipartFile invalidFile = new MockMultipartFile(
                "file",
                "report.pdf",
                "application/msword",
                "invalid".getBytes()
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> reportService.submitReport(request, invalidFile)
        );

        assertEquals("Chỉ hỗ trợ file PDF, DOC hoặc DOCX.", exception.getMessage());
        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void submitReport_rejectsFileLargerThanTenMegabytes() {
        SubmitReportRequest request = request(10L);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task(10L)));
        when(reportRepository.findMaxVersionByTaskIdAndSubmittedById(10L, 3L)).thenReturn(Optional.empty());
        MockMultipartFile tooLargeFile = new MockMultipartFile(
                "file",
                "large.pdf",
                "application/pdf",
                new byte[10 * 1024 * 1024 + 1]
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> reportService.submitReport(request, tooLargeFile)
        );

        assertEquals("Dung lượng file không được vượt quá 10MB.", exception.getMessage());
        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void leaderReview_marksLatestSubmittedReportAsReviewed() {
        User leader = authenticate(7L, "leader", "STUDENT");
        ReportEntity report = report(100L, 10L, 1);
        LeaderReviewReportRequest request = new LeaderReviewReportRequest();
        request.setDecision(LeaderReportDecision.ACCEPT);
        request.setComment("Báo cáo đã đủ nội dung nhóm yêu cầu.");

        when(reportRepository.findById(100L)).thenReturn(Optional.of(report));
        when(groupMemberRepository.findActiveRoleByGroupIdAndUserId(90L, leader.getId()))
                .thenReturn(Optional.of(GroupRole.LEADER));
        when(groupMemberRepository.findActiveRoleByProjectIdAndUserId(50L, leader.getId()))
                .thenReturn(Optional.of(GroupRole.LEADER));
        when(reportRepository.save(report)).thenReturn(report);

        ReportResponse response = reportService.leaderReview(100L, request);

        assertEquals(ReportStatus.LEADER_REVIEWED, response.getStatus());
        assertNotNull(response.getLeaderReviewedAt());
        assertEquals(request.getComment(), response.getLeaderComment());
        ArgumentCaptor<com.web.labportalbackend.research.entity.CommentEntity> commentCaptor =
                ArgumentCaptor.forClass(com.web.labportalbackend.research.entity.CommentEntity.class);
        verify(commentRepository).save(commentCaptor.capture());
        assertEquals("Trưởng nhóm đã chấp nhận báo cáo: " + request.getComment(), commentCaptor.getValue().getContent());
        verify(reportRepository).save(report);
    }

    @Test
    void leaderReview_rejectsReportWithNeedsRevisionStatus() {
        User leader = authenticate(7L, "leader", "STUDENT");
        ReportEntity report = report(100L, 10L, 2);
        report.setStatus(ReportStatus.NEEDS_REVISION);
        LeaderReviewReportRequest request = new LeaderReviewReportRequest();
        request.setDecision(LeaderReportDecision.ACCEPT);
        request.setComment("Bản sửa đổi.");

        when(reportRepository.findById(100L)).thenReturn(Optional.of(report));
        when(groupMemberRepository.findActiveRoleByGroupIdAndUserId(90L, leader.getId()))
                .thenReturn(Optional.of(GroupRole.LEADER));
        when(groupMemberRepository.findActiveRoleByProjectIdAndUserId(50L, leader.getId()))
                .thenReturn(Optional.of(GroupRole.LEADER));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> reportService.leaderReview(100L, request));
        assertEquals("Chỉ có thể xử lý báo cáo đang chờ trưởng nhóm kiểm tra.", exception.getMessage());
        verify(reportRepository, never()).save(any());
    }

    @Test
    void leaderReview_rejectsLeadersOwnReport() {
        User leader = authenticate(7L, "leader", "STUDENT");
        ReportEntity report = report(100L, 10L, 1);
        report.setSubmittedById(leader.getId());
        LeaderReviewReportRequest request = new LeaderReviewReportRequest();
        request.setDecision(LeaderReportDecision.ACCEPT);
        request.setComment("Tự kiểm tra.");

        when(reportRepository.findById(100L)).thenReturn(Optional.of(report));
        when(groupMemberRepository.findActiveRoleByGroupIdAndUserId(90L, leader.getId()))
                .thenReturn(Optional.of(GroupRole.LEADER));

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> reportService.leaderReview(100L, request));
        assertEquals("Bạn không thể tự xử lý báo cáo của chính mình. Báo cáo này sẽ do quản lý PTN duyệt.", exception.getMessage());
        verify(reportRepository, never()).save(any());
    }

    @Test
    void leaderReview_requestRevision_transitionsReportAndTaskStatus() {
        User leader = authenticate(7L, "leader", "STUDENT");
        ReportEntity report = report(100L, 10L, 1);
        LeaderReviewReportRequest request = new LeaderReviewReportRequest();
        request.setDecision(LeaderReportDecision.REQUEST_REVISION);
        request.setComment("Cần bổ sung kết quả.");

        TaskEntity task = task(10L);
        task.setStatus(TaskStatus.IN_REVIEW);

        when(reportRepository.findById(100L)).thenReturn(Optional.of(report));
        when(groupMemberRepository.findActiveRoleByGroupIdAndUserId(90L, leader.getId()))
                .thenReturn(Optional.of(GroupRole.LEADER));
        when(groupMemberRepository.findActiveRoleByProjectIdAndUserId(50L, leader.getId()))
                .thenReturn(Optional.of(GroupRole.LEADER));
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(reportRepository.save(report)).thenReturn(report);

        ReportResponse response = reportService.leaderReview(100L, request);

        assertEquals(ReportStatus.NEEDS_REVISION, response.getStatus());
        assertEquals(TaskStatus.NEEDS_REVISION, task.getStatus());
        verify(taskRepository).save(task);
        ArgumentCaptor<com.web.labportalbackend.research.entity.CommentEntity> commentCaptor =
                ArgumentCaptor.forClass(com.web.labportalbackend.research.entity.CommentEntity.class);
        verify(commentRepository).save(commentCaptor.capture());
        assertEquals("Trưởng nhóm yêu cầu nộp lại báo cáo: " + request.getComment(), commentCaptor.getValue().getContent());
    }

    @Test
    void leaderReview_reject_transitionsReportAndTaskStatus() {
        User leader = authenticate(7L, "leader", "STUDENT");
        ReportEntity report = report(100L, 10L, 1);
        LeaderReviewReportRequest request = new LeaderReviewReportRequest();
        request.setDecision(LeaderReportDecision.REJECT);
        request.setComment("Sai nhiệm vụ.");

        TaskEntity task = task(10L);
        task.setStatus(TaskStatus.IN_REVIEW);

        when(reportRepository.findById(100L)).thenReturn(Optional.of(report));
        when(groupMemberRepository.findActiveRoleByGroupIdAndUserId(90L, leader.getId()))
                .thenReturn(Optional.of(GroupRole.LEADER));
        when(groupMemberRepository.findActiveRoleByProjectIdAndUserId(50L, leader.getId()))
                .thenReturn(Optional.of(GroupRole.LEADER));
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(reportRepository.save(report)).thenReturn(report);

        ReportResponse response = reportService.leaderReview(100L, request);

        assertEquals(ReportStatus.LEADER_REJECTED, report.getStatus());
        assertEquals(TaskStatus.NEEDS_REVISION, task.getStatus());
        verify(taskRepository).save(task);
        ArgumentCaptor<com.web.labportalbackend.research.entity.CommentEntity> commentCaptor =
                ArgumentCaptor.forClass(com.web.labportalbackend.research.entity.CommentEntity.class);
        verify(commentRepository).save(commentCaptor.capture());
        assertEquals("Trưởng nhóm từ chối báo cáo: " + request.getComment(), commentCaptor.getValue().getContent());
    }

    @Test
    void managerReview_approveMilestoneReportCompletesMilestone() {
        User manager = authenticate(2L, "manager", "LAB_MANAGER");
        Laboratory lab = new Laboratory();
        lab.setId(1L);
        MilestoneEntity milestone = managerMilestone(lab);
        ReportEntity report = report(100L, null, 1);
        report.setStatus(ReportStatus.LEADER_REVIEWED);
        ManagerReviewReportRequest request = managerRequest(ManagerReportDecision.APPROVE);

        when(reportRepository.findById(100L)).thenReturn(Optional.of(report));
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(5L)).thenReturn(Optional.of(milestone));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())).thenReturn(Optional.of(lab));
        when(reportRepository.saveAndFlush(report)).thenReturn(report);

        ReportResponse response = reportService.managerReview(100L, request);

        assertEquals(ReportStatus.APPROVED, response.getStatus());
        assertNotNull(response.getManagerReviewedAt());
        assertEquals(request.getComment(), response.getManagerComment());
        assertEquals(MilestoneStatus.COMPLETED, milestone.getStatus());
        assertEquals(100, milestone.getProgressPercent());
        verify(milestoneRepository).save(milestone);
        ArgumentCaptor<com.web.labportalbackend.research.entity.CommentEntity> commentCaptor =
                ArgumentCaptor.forClass(com.web.labportalbackend.research.entity.CommentEntity.class);
        verify(commentRepository).save(commentCaptor.capture());
        assertEquals("Quản lý PTN đã chấp nhận báo cáo: " + request.getComment(), commentCaptor.getValue().getContent());
    }

    @Test
    void managerReview_rejectsReportFromAnotherLab() {
        User manager = authenticate(2L, "manager", "LAB_MANAGER");
        Laboratory myLab = new Laboratory();
        myLab.setId(1L);
        Laboratory otherLab = new Laboratory();
        otherLab.setId(2L);
        MilestoneEntity milestone = managerMilestone(otherLab);
        ReportEntity report = report(100L, null, 1);
        report.setStatus(ReportStatus.LEADER_REVIEWED);

        when(reportRepository.findById(100L)).thenReturn(Optional.of(report));
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(5L)).thenReturn(Optional.of(milestone));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())).thenReturn(Optional.of(myLab));

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> reportService.managerReview(100L, managerRequest(ManagerReportDecision.APPROVE))
        );
        assertEquals("Bạn không có quyền duyệt báo cáo này.", exception.getMessage());
        verify(reportRepository, never()).save(any());
    }

    @Test
    void managerReview_rejectsSubmittedReportWhenLeaderReviewRequired() {
        User manager = authenticate(2L, "manager", "LAB_MANAGER");
        Laboratory lab = new Laboratory();
        lab.setId(1L);
        MilestoneEntity milestone = managerMilestone(lab);
        ReportEntity report = report(100L, null, 1);
        report.setStatus(ReportStatus.SUBMITTED);

        when(reportRepository.findById(100L)).thenReturn(Optional.of(report));
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(5L)).thenReturn(Optional.of(milestone));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())).thenReturn(Optional.of(lab));

        SystemConfigResponse config = mock(SystemConfigResponse.class);
        SystemConfigResponse.ResearchConfig researchConfig = mock(SystemConfigResponse.ResearchConfig.class);
        when(systemConfigService.getConfig()).thenReturn(config);
        when(config.research()).thenReturn(researchConfig);
        when(researchConfig.requireLeaderReviewBeforeManagerReview()).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> reportService.managerReview(100L, managerRequest(ManagerReportDecision.APPROVE))
        );
        assertEquals("Báo cáo cần được trưởng nhóm kiểm tra trước.", exception.getMessage());
        verify(reportRepository, never()).save(any());
    }

    @Test
    void managerReview_requestRevisionRequiresNewSubmissionAndDoesNotCompleteMilestone() {
        User manager = authenticate(2L, "manager", "LAB_MANAGER");
        Laboratory lab = new Laboratory();
        lab.setId(1L);
        MilestoneEntity milestone = managerMilestone(lab);
        milestone.setProgressPercent(100);
        ReportEntity report = report(100L, null, 1);
        report.setStatus(ReportStatus.LEADER_REVIEWED);

        when(reportRepository.findById(100L)).thenReturn(Optional.of(report));
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(5L)).thenReturn(Optional.of(milestone));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())).thenReturn(Optional.of(lab));
        when(reportRepository.saveAndFlush(report)).thenReturn(report);

        ReportResponse response = reportService.managerReview(
                100L,
                managerRequest(ManagerReportDecision.REQUEST_REVISION)
        );

        assertEquals(ReportStatus.NEEDS_REVISION, response.getStatus());
        assertEquals(MilestoneStatus.IN_PROGRESS, milestone.getStatus());
        assertEquals(0, milestone.getProgressPercent());
        ArgumentCaptor<com.web.labportalbackend.research.entity.CommentEntity> commentCaptor =
                ArgumentCaptor.forClass(com.web.labportalbackend.research.entity.CommentEntity.class);
        verify(commentRepository).save(commentCaptor.capture());
        assertEquals("Quản lý PTN yêu cầu nộp lại báo cáo: Nhận xét duyệt báo cáo.", commentCaptor.getValue().getContent());
    }

    @Test
    void managerReview_rejectsOlderVersionWhenANewerSubmissionExists() {
        User manager = authenticate(2L, "manager", "LAB_MANAGER");
        Laboratory lab = new Laboratory();
        lab.setId(1L);
        MilestoneEntity milestone = managerMilestone(lab);
        ReportEntity report = report(100L, null, 1);
        report.setStatus(ReportStatus.LEADER_REVIEWED);

        when(reportRepository.findById(100L)).thenReturn(Optional.of(report));
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(5L)).thenReturn(Optional.of(milestone));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())).thenReturn(Optional.of(lab));
        when(reportRepository.existsBySubmissionScopeAndVersionGreaterThan(report.getSubmissionScope(), report.getVersion()))
                .thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> reportService.managerReview(100L, managerRequest(ManagerReportDecision.APPROVE))
        );
        assertEquals("Chỉ có thể xử lý phiên bản báo cáo mới nhất.", exception.getMessage());

        verify(reportRepository, never()).save(any());
        verify(milestoneRepository, never()).save(any());
    }

    @Test
    void managerReview_approveTaskReportCompletesTaskButKeepsMilestoneOpenWhenOtherTasksRemain() {
        User manager = authenticate(2L, "manager", "LAB_MANAGER");
        Laboratory lab = new Laboratory();
        lab.setId(1L);
        MilestoneEntity milestone = managerMilestone(lab);
        ReportEntity report = report(100L, 10L, 1);
        report.setStatus(ReportStatus.LEADER_REVIEWED);
        TaskEntity task = task(10L);
        task.setStatus(TaskStatus.IN_REVIEW);
        task.setProgressPercent(90);

        when(reportRepository.findById(100L)).thenReturn(Optional.of(report));
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(5L)).thenReturn(Optional.of(milestone));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())).thenReturn(Optional.of(lab));
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        TaskEntity outstanding = task(11L);
        when(taskRepository.findByMilestoneIdAndDeletedFalseAndActiveTrueOrderByDeadlineAscCreatedAtAsc(5L))
                .thenReturn(List.of(task, outstanding));
        when(reportRepository.saveAndFlush(report)).thenReturn(report);

        ReportResponse response = reportService.managerReview(100L, managerRequest(ManagerReportDecision.APPROVE));

        assertEquals(ReportStatus.APPROVED, response.getStatus());
        assertEquals(TaskStatus.DONE, task.getStatus());
        assertEquals(100, task.getProgressPercent());
        assertEquals(MilestoneStatus.IN_PROGRESS, milestone.getStatus());
        assertEquals(50, milestone.getProgressPercent());
        verify(taskRepository).save(task);
        verify(milestoneRepository).save(milestone);
    }

    @Test
    void managerReview_doesNotCompleteMilestoneWhileLatestReportForAnotherTaskIsPending() {
        User manager = authenticate(2L, "manager", "LAB_MANAGER");
        Laboratory lab = new Laboratory();
        lab.setId(1L);
        MilestoneEntity milestone = managerMilestone(lab);
        ReportEntity report = report(100L, 10L, 1);
        report.setStatus(ReportStatus.LEADER_REVIEWED);
        TaskEntity reviewedTask = task(10L);
        TaskEntity completedTask = task(11L);
        completedTask.setStatus(TaskStatus.DONE);
        completedTask.setProgressPercent(100);
        ReportEntity pendingLatest = report(101L, 11L, 2);
        pendingLatest.setSubmissionScope("M:5:T:11:U:3");
        pendingLatest.setStatus(ReportStatus.LEADER_REVIEWED);

        when(reportRepository.findById(100L)).thenReturn(Optional.of(report));
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(5L)).thenReturn(Optional.of(milestone));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())).thenReturn(Optional.of(lab));
        when(taskRepository.findById(10L)).thenReturn(Optional.of(reviewedTask));
        when(taskRepository.findByMilestoneIdAndDeletedFalseAndActiveTrueOrderByDeadlineAscCreatedAtAsc(5L))
                .thenReturn(List.of(reviewedTask, completedTask));
        when(reportRepository.findByMilestoneIdOrderByCreatedAtDescVersionDesc(5L))
                .thenReturn(List.of(pendingLatest));
        when(reportRepository.saveAndFlush(report)).thenReturn(report);

        reportService.managerReview(100L, managerRequest(ManagerReportDecision.APPROVE));

        assertEquals(100, milestone.getProgressPercent());
        assertEquals(MilestoneStatus.IN_PROGRESS, milestone.getStatus());
    }

    @Test
    void managerReview_requestRevisionMovesReportedTaskBackForCorrection() {
        User manager = authenticate(2L, "manager", "LAB_MANAGER");
        Laboratory lab = new Laboratory();
        lab.setId(1L);
        MilestoneEntity milestone = managerMilestone(lab);
        ReportEntity report = report(100L, 10L, 1);
        report.setStatus(ReportStatus.LEADER_REVIEWED);
        TaskEntity task = task(10L);
        task.setStatus(TaskStatus.IN_REVIEW);

        when(reportRepository.findById(100L)).thenReturn(Optional.of(report));
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(5L)).thenReturn(Optional.of(milestone));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())).thenReturn(Optional.of(lab));
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(taskRepository.findByMilestoneIdAndDeletedFalseAndActiveTrueOrderByDeadlineAscCreatedAtAsc(5L))
                .thenReturn(List.of(task));
        when(reportRepository.saveAndFlush(report)).thenReturn(report);

        ReportResponse response = reportService.managerReview(
                100L,
                managerRequest(ManagerReportDecision.REQUEST_REVISION)
        );

        assertEquals(ReportStatus.NEEDS_REVISION, response.getStatus());
        assertEquals(TaskStatus.NEEDS_REVISION, task.getStatus());
        assertEquals(MilestoneStatus.IN_PROGRESS, milestone.getStatus());
        assertEquals(0, milestone.getProgressPercent());
        verify(taskRepository).save(task);
    }

    private ReplaceReportRequest replaceRequest() {
        ReplaceReportRequest request = new ReplaceReportRequest();
        request.setTitle("Updated Title");
        request.setContentDone("Updated Content Done");
        request.setResult("Updated Result");
        request.setDifficulty("Updated Difficulty");
        request.setNextPlan("Updated Next Plan");
        request.setSelfAssessment("Updated Self Assessment");
        request.setEvidenceLink("http://evidence.link");
        return request;
    }

    @Test
    void replaceReport_successWithoutNewFile() {
        ReplaceReportRequest request = replaceRequest();
        ReportEntity report = report(100L, 10L, 1);
        report.setStatus(ReportStatus.SUBMITTED);

        when(reportRepository.findById(100L)).thenReturn(Optional.of(report));
        when(reportRepository.existsBySubmissionScopeAndVersionGreaterThan(report.getSubmissionScope(), 1)).thenReturn(false);
        when(reportRepository.save(any(ReportEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReportResponse response = reportService.replaceReport(100L, request, null);

        assertEquals(100L, response.getId());
        assertEquals("Updated Title", response.getTitle());
        assertEquals("Updated Content Done", response.getContentDone());
        assertEquals(1, response.getVersion());
        assertEquals(ReportStatus.SUBMITTED, response.getStatus());
        verify(reportRepository).save(any(ReportEntity.class));
    }

    @Test
    void replaceReport_successWithNewFile() {
        ReplaceReportRequest request = replaceRequest();
        ReportEntity report = report(100L, 10L, 1);
        report.setStatus(ReportStatus.SUBMITTED);

        when(reportRepository.findById(100L)).thenReturn(Optional.of(report));
        when(reportRepository.existsBySubmissionScopeAndVersionGreaterThan(report.getSubmissionScope(), 1)).thenReturn(false);
        when(reportRepository.save(any(ReportEntity.class))).thenAnswer(invocation -> {
            ReportEntity saved = invocation.getArgument(0);
            trackStoredFile(saved);
            return saved;
        });

        MockMultipartFile newFile = file("report-updated.pdf");
        ReportResponse response = reportService.replaceReport(100L, request, newFile);

        assertEquals(100L, response.getId());
        assertEquals("Updated Title", response.getTitle());
        assertEquals("report-updated.pdf", response.getFileName());
        assertEquals(1, response.getVersion());
        verify(reportRepository).save(any(ReportEntity.class));
    }

    @Test
    void replaceReport_throwsAccessDeniedWhenNotSubmittedByCurrentUser() {
        ReplaceReportRequest request = replaceRequest();
        ReportEntity report = report(100L, 10L, 1);
        report.setSubmittedById(999L); // Different user

        when(reportRepository.findById(100L)).thenReturn(Optional.of(report));

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> reportService.replaceReport(100L, request, null)
        );

        assertEquals("Bạn không có quyền cập nhật báo cáo này.", exception.getMessage());
        verify(reportRepository, never()).save(any());
    }

    @Test
    void replaceReport_throwsIllegalArgumentExceptionWhenStatusNotSubmitted() {
        ReplaceReportRequest request = replaceRequest();
        ReportEntity report = report(100L, 10L, 1);
        report.setStatus(ReportStatus.APPROVED); // Handled

        when(reportRepository.findById(100L)).thenReturn(Optional.of(report));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> reportService.replaceReport(100L, request, null)
        );

        assertEquals("Báo cáo đã được xử lý, không thể cập nhật phiên bản này.", exception.getMessage());
        verify(reportRepository, never()).save(any());
    }

    @Test
    void replaceReport_throwsIllegalArgumentExceptionWhenNotLatestVersion() {
        ReplaceReportRequest request = replaceRequest();
        ReportEntity report = report(100L, 10L, 1);

        when(reportRepository.findById(100L)).thenReturn(Optional.of(report));
        when(reportRepository.existsBySubmissionScopeAndVersionGreaterThan(report.getSubmissionScope(), 1)).thenReturn(true); // There is a newer version

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> reportService.replaceReport(100L, request, null)
        );

        assertEquals("Chỉ có thể xử lý phiên bản báo cáo mới nhất.", exception.getMessage());
        verify(reportRepository, never()).save(any());
    }

    private SubmitReportRequest request(Long taskId) {
        SubmitReportRequest request = new SubmitReportRequest();
        request.setTaskId(taskId);
        request.setTitle("Weekly progress");
        request.setContentDone("Completed data analysis.");
        request.setResult("Baseline result available.");
        request.setDifficulty("Dataset noise.");
        request.setNextPlan("Clean the data.");
        request.setSelfAssessment("On schedule.");
        return request;
    }

    private TaskEntity task(Long id) {
        TaskEntity task = TaskEntity.builder()
                .milestoneId(5L)
                .assigneeId(3L)
                .title("Analyze result")
                .status(TaskStatus.IN_PROGRESS)
                .build();
        task.setId(id);
        return task;
    }

    private void assertSubmitRejectedByLatestStatus(ReportStatus latestStatus, String expectedMessage) {
        SubmitReportRequest request = request(10L);
        TaskEntity task = task(10L);
        ReportEntity latest = report(99L, 10L, 1);
        latest.setStatus(latestStatus);

        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(reportRepository.findTopByTaskIdAndSubmittedByIdAndDeletedFalseAndActiveTrueOrderByVersionDescCreatedAtDesc(10L, 3L))
                .thenReturn(Optional.of(latest));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> reportService.submitReport(request, file("blocked-resubmit.pdf"))
        );

        assertEquals(expectedMessage, exception.getMessage());
        verify(reportRepository, never()).saveAndFlush(any());
    }

    private ReportEntity report(Long id, Long taskId, Integer version) {
        ReportEntity report = ReportEntity.builder()
                .groupId(90L)
                .milestoneId(5L)
                .taskId(taskId)
                .submittedById(3L)
                .version(version)
                .title("Report")
                .contentDone("Completed work")
                .result("Result")
                .difficulty("None")
                .nextPlan("Continue")
                .selfAssessment("Good")
                .fileUrl("https://files.local/report-v" + version + ".pdf")
                .fileName("report.pdf")
                .status(ReportStatus.SUBMITTED)
                .submissionScope("M:5:T:" + (taskId == null ? "_" : taskId) + ":U:3")
                .build();
        report.setId(id);
        report.setCreatedAt(Instant.parse("2026-01-0" + version + "T00:00:00Z"));
        return report;
    }

    private User user(Long id, String username, String fullName, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setFullName(fullName);
        user.setEmail(email);
        return user;
    }

    private MockMultipartFile file(String filename) {
        return new MockMultipartFile("file", filename, "application/pdf", "report".getBytes());
    }

    private void deleteDirectoryIfExists(Path storageDir) throws IOException {
        if (Files.exists(storageDir)) {
            try (var walk = Files.walk(storageDir)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
            }
        }
    }

    private User authenticate(Long id, String username, String roleName) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.addRole(new Role(roleName, roleName));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of())
        );
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        return user;
    }

    private MilestoneEntity managerMilestone(Laboratory lab) {
        ProjectEntity project = new ProjectEntity();
        project.setId(50L);
        project.setLab(lab);
        MilestoneEntity milestone = new MilestoneEntity();
        milestone.setId(5L);
        milestone.setProject(project);
        milestone.setStatus(MilestoneStatus.IN_PROGRESS);
        milestone.setProgressPercent(90);
        return milestone;
    }

    private ManagerReviewReportRequest managerRequest(ManagerReportDecision decision) {
        ManagerReviewReportRequest request = new ManagerReviewReportRequest();
        request.setDecision(decision);
        request.setComment("Nhận xét duyệt báo cáo.");
        return request;
    }

    private void trackStoredFile(ReportEntity report) {
        storedFiles.add(Path.of(report.getFileUrl().substring(1)));
    }
}
