package com.web.labportalbackend.research.service.impl;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.research.dto.request.CreateCommentRequest;
import com.web.labportalbackend.research.dto.response.CommentResponse;
import com.web.labportalbackend.research.dto.response.ReportResponse;
import com.web.labportalbackend.research.entity.CommentEntity;
import com.web.labportalbackend.research.entity.ReportEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.mapper.CommentMapper;
import com.web.labportalbackend.research.repository.CommentRepository;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.ReportRepository;
import com.web.labportalbackend.research.service.ReportService;
import com.web.labportalbackend.research.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final CommentRepository commentRepository;
    private final ReportRepository reportRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ReportService reportService;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public CommentResponse addComment(Long reportId, Long authorId, CreateCommentRequest request) {
        ReportEntity report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report", reportId));
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new ResourceNotFoundException("User", authorId));
        if (author.hasRole("LAB_MANAGER")) {
            assertCanReadReport(report);
        } else if (author.hasRole("STUDENT")
                && (authorId.equals(report.getSubmittedById())
                || groupMemberRepository.existsLeaderByReportIdAndUserId(reportId, authorId))) {
            assertCanReadReport(report);
        } else {
            throw new AccessDeniedException("Only the report author, group leader or laboratory manager can comment");
        }

        CommentEntity comment = CommentEntity.builder()
                .reportId(reportId)
                .authorId(authorId)
                .content(request.getContent())
                .build();

        return toResponse(commentRepository.save(comment), report, author);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentResponse> getByReport(Long reportId) {
        ReportEntity report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report", reportId));
        assertCanReadReport(report);

        return commentRepository.findByReportIdOrderByCreatedAtAsc(reportId)
                .stream()
                .map(comment -> toResponse(comment, report))
                .toList();
    }

    private CommentResponse toResponse(CommentEntity comment, ReportEntity report) {
        User author = userRepository.findById(comment.getAuthorId())
                .orElseThrow(() -> new ResourceNotFoundException("User", comment.getAuthorId()));
        return toResponse(comment, report, author);
    }

    private CommentResponse toResponse(CommentEntity comment, ReportEntity report, User author) {
        boolean manager = author.hasRole("LAB_MANAGER");
        GroupRole groupRole = manager
                ? null
                : groupMemberRepository.findActiveRoleByReportIdAndUserId(report.getId(), author.getId())
                        .orElse(null);
        String authorRole = manager ? "LAB_MANAGER" : "STUDENT";
        return CommentMapper.toResponse(comment, author, authorRole, groupRole);
    }

    private void assertCanReadReport(ReportEntity report) {
        List<ReportResponse> visibleReports =
                report.getTaskId() != null
                        ? reportService.getReportsByTask(report.getTaskId())
                        : reportService.getReportsByMilestone(report.getMilestoneId());
        boolean reportVisible = visibleReports.stream()
                .anyMatch(visibleReport -> report.getId().equals(visibleReport.getId()));
        if (!reportVisible) {
            throw new AccessDeniedException("Cannot access comments for this report");
        }
    }
}
