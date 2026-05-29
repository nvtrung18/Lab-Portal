package com.web.labportalbackend.research.service;

import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.dto.request.CreateCommentRequest;
import com.web.labportalbackend.research.dto.response.CommentResponse;
import com.web.labportalbackend.research.entity.CommentEntity;
import com.web.labportalbackend.research.entity.MilestoneEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.entity.ReportEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.repository.CommentRepository;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.MilestoneRepository;
import com.web.labportalbackend.research.repository.ReportRepository;
import com.web.labportalbackend.research.service.impl.ReviewServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private MilestoneRepository milestoneRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private LaboratoryRepository laboratoryRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    @Test
    void addComment_createsCommentWhenAuthorIsGroupLeader() {
        CreateCommentRequest request = request("Looks good, please add the appendix.");
        ReportEntity report = report(10L, 20L);

        when(reportRepository.findById(10L)).thenReturn(java.util.Optional.of(report));
        when(userRepository.findById(7L)).thenReturn(java.util.Optional.of(student(7L, "STUDENT")));
        when(groupMemberRepository.findActiveRoleByGroupIdAndUserId(30L, 7L))
                .thenReturn(java.util.Optional.of(GroupRole.LEADER));
        when(groupMemberRepository.findActiveRoleByReportIdAndUserId(10L, 7L))
                .thenReturn(java.util.Optional.of(GroupRole.LEADER));
        when(commentRepository.save(any(CommentEntity.class))).thenAnswer(invocation -> {
            CommentEntity comment = invocation.getArgument(0);
            comment.setId(100L);
            comment.setCreatedAt(Instant.parse("2026-05-14T01:00:00Z"));
            return comment;
        });

        CommentResponse response = reviewService.addComment(10L, 7L, request);

        assertEquals(100L, response.getId());
        assertEquals(10L, response.getReportId());
        assertEquals(7L, response.getAuthorId());
        assertEquals("User Seven", response.getAuthorName());
        assertEquals("user7@labportal.com", response.getAuthorEmail());
        assertEquals("STUDENT", response.getAuthorRole());
        assertEquals(GroupRole.LEADER, response.getGroupRole());
        assertEquals("Looks good, please add the appendix.", response.getContent());
        assertEquals(Instant.parse("2026-05-14T01:00:00Z"), response.getCreatedAt());

        ArgumentCaptor<CommentEntity> captor = ArgumentCaptor.forClass(CommentEntity.class);
        verify(commentRepository).save(captor.capture());
        assertEquals(10L, captor.getValue().getReportId());
        assertEquals(7L, captor.getValue().getAuthorId());
        assertEquals("Looks good, please add the appendix.", captor.getValue().getContent());
    }

    @Test
    void addComment_allowsReportSubmitterToReply() {
        CreateCommentRequest request = request("Tôi đã bổ sung nội dung.");
        ReportEntity report = report(10L, 20L);
        report.setSubmittedById(7L);

        when(reportRepository.findById(10L)).thenReturn(java.util.Optional.of(report));
        when(userRepository.findById(7L)).thenReturn(java.util.Optional.of(student(7L, "STUDENT")));
        when(groupMemberRepository.findActiveRoleByGroupIdAndUserId(30L, 7L))
                .thenReturn(java.util.Optional.of(GroupRole.MEMBER));
        when(commentRepository.save(any(CommentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        reviewService.addComment(10L, 7L, request);

        verify(commentRepository).save(any(CommentEntity.class));
    }

    @Test
    void addComment_rejectsStudentWhoIsNotSubmitterOrLeader() {
        CreateCommentRequest request = request("I should not be able to comment.");

        when(reportRepository.findById(10L)).thenReturn(java.util.Optional.of(report(10L, 20L)));
        when(userRepository.findById(7L)).thenReturn(java.util.Optional.of(student(7L, "STUDENT")));
        when(groupMemberRepository.findActiveRoleByGroupIdAndUserId(30L, 7L))
                .thenReturn(java.util.Optional.empty());

        assertThrows(AccessDeniedException.class, () -> reviewService.addComment(10L, 7L, request));
        verify(commentRepository, never()).save(any(CommentEntity.class));
    }

    @Test
    void getByReport_returnsCommentsInCreatedAtAscendingOrder() {
        CommentEntity older = comment(1L, 10L, 7L, "First", "2026-05-14T01:00:00Z");
        CommentEntity newer = comment(2L, 10L, 8L, "Second", "2026-05-14T02:00:00Z");
        ReportEntity report = report(10L, 20L);

        when(reportRepository.findById(10L)).thenReturn(java.util.Optional.of(report));
        when(commentRepository.findByReportIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(older, newer));
        when(userRepository.findById(7L)).thenReturn(java.util.Optional.of(student(7L, "STUDENT")));
        when(userRepository.findById(8L)).thenReturn(java.util.Optional.of(student(8L, "STUDENT")));
        when(groupMemberRepository.findActiveRoleByGroupIdAndUserId(30L, 7L))
                .thenReturn(java.util.Optional.of(GroupRole.LEADER));
        when(groupMemberRepository.findActiveRoleByReportIdAndUserId(10L, 7L))
                .thenReturn(java.util.Optional.of(GroupRole.LEADER));
        when(groupMemberRepository.findActiveRoleByReportIdAndUserId(10L, 8L))
                .thenReturn(java.util.Optional.of(GroupRole.MEMBER));

        List<CommentResponse> responses = reviewService.getByReport(10L, 7L);

        assertEquals(2, responses.size());
        assertEquals(Instant.parse("2026-05-14T01:00:00Z"), responses.get(0).getCreatedAt());
        assertEquals(Instant.parse("2026-05-14T02:00:00Z"), responses.get(1).getCreatedAt());
        assertEquals(GroupRole.LEADER, responses.get(0).getGroupRole());
        assertEquals(GroupRole.MEMBER, responses.get(1).getGroupRole());
        verify(commentRepository).findByReportIdOrderByCreatedAtAsc(10L);
    }

    @Test
    void addComment_allowsManagerAfterReportScopeCheck() {
        CreateCommentRequest request = request("Đề nghị bổ sung số liệu.");
        ReportEntity report = report(10L, null);
        when(reportRepository.findById(10L)).thenReturn(java.util.Optional.of(report));
        when(userRepository.findById(2L)).thenReturn(java.util.Optional.of(student(2L, "LAB_MANAGER")));
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(5L))
                .thenReturn(java.util.Optional.of(milestoneWithLab(99L)));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(2L))
                .thenReturn(java.util.Optional.of(lab(99L)));
        when(commentRepository.save(any(CommentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CommentResponse response = reviewService.addComment(10L, 2L, request);

        assertEquals("LAB_MANAGER", response.getAuthorRole());
        assertEquals(null, response.getGroupRole());
        verify(commentRepository).save(any(CommentEntity.class));
    }

    @Test
    void getByReport_rejectsReportNotIncludedInScopedReportList() {
        ReportEntity report = report(10L, null);

        when(reportRepository.findById(10L)).thenReturn(java.util.Optional.of(report));
        when(userRepository.findById(7L)).thenReturn(java.util.Optional.of(student(7L, "STUDENT")));
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(5L))
                .thenReturn(java.util.Optional.of(milestoneWithLab(99L)));
        when(groupMemberRepository.findActiveRoleByProjectIdAndUserId(40L, 7L))
                .thenReturn(java.util.Optional.empty());

        assertThrows(AccessDeniedException.class, () -> reviewService.getByReport(10L, 7L));
        verify(commentRepository, never()).findByReportIdOrderByCreatedAtAsc(10L);
    }

    private CreateCommentRequest request(String content) {
        CreateCommentRequest request = new CreateCommentRequest();
        request.setContent(content);
        return request;
    }

    private CommentEntity comment(Long id, Long reportId, Long authorId, String content, String createdAt) {
        CommentEntity comment = CommentEntity.builder()
                .reportId(reportId)
                .authorId(authorId)
                .content(content)
                .build();
        comment.setId(id);
        comment.setCreatedAt(Instant.parse(createdAt));
        return comment;
    }

    private ReportEntity report(Long id, Long taskId) {
        ReportEntity report = ReportEntity.builder()
                .milestoneId(5L)
                .taskId(taskId)
                .submittedById(3L)
                .version(1)
                .title("Report")
                .contentDone("Completed work")
                .result("Result")
                .difficulty("None")
                .nextPlan("Continue")
                .selfAssessment("Good")
                .fileUrl("report.pdf")
                .fileName("report.pdf")
                .submissionScope("M:5:T:" + (taskId == null ? "_" : taskId) + ":U:3")
                .build();
        report.setId(id);
        report.setGroupId(taskId == null ? null : 30L);
        return report;
    }

    private User student(Long id, String role) {
        User user = new User();
        user.setId(id);
        user.setFullName("User " + (id == 2L ? "Manager" : id == 7L ? "Seven" : "Eight"));
        user.setEmail("user" + id + "@labportal.com");
        user.addRole(new Role(role, role));
        return user;
    }

    private MilestoneEntity milestoneWithLab(Long labId) {
        MilestoneEntity milestone = new MilestoneEntity();
        milestone.setId(5L);
        ProjectEntity project = new ProjectEntity();
        project.setId(40L);
        project.setLab(lab(labId));
        milestone.setProject(project);
        return milestone;
    }

    private Laboratory lab(Long id) {
        Laboratory lab = new Laboratory();
        lab.setId(id);
        return lab;
    }
}
