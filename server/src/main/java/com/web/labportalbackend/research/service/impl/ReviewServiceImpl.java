package com.web.labportalbackend.research.service.impl;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.dto.request.CreateCommentRequest;
import com.web.labportalbackend.research.dto.response.CommentResponse;
import com.web.labportalbackend.research.entity.CommentEntity;
import com.web.labportalbackend.research.entity.MilestoneEntity;
import com.web.labportalbackend.research.entity.ReportEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.mapper.CommentMapper;
import com.web.labportalbackend.research.repository.CommentRepository;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.MilestoneRepository;
import com.web.labportalbackend.research.repository.ReportRepository;
import com.web.labportalbackend.research.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private static final String VIEW_DENIED_MESSAGE = "Bạn không có quyền xem góp ý của báo cáo này.";
    private static final String COMMENT_DENIED_MESSAGE = "Bạn không có quyền gửi góp ý cho báo cáo này.";
    private static final boolean ALLOW_GROUP_COMMENT_EXCHANGE = true;

    private final CommentRepository commentRepository;
    private final ReportRepository reportRepository;
    private final MilestoneRepository milestoneRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public CommentResponse addComment(Long reportId, Long authorId, CreateCommentRequest request) {
        ReportEntity report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report", reportId));
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new ResourceNotFoundException("User", authorId));
        if (!canViewReport(author, report)) {
            throw new AccessDeniedException(COMMENT_DENIED_MESSAGE);
        }
        if (request == null || !StringUtils.hasText(request.getContent())) {
            throw new IllegalArgumentException("Nội dung góp ý không được để trống.");
        }
        String content = request.getContent().trim();
        if (content.length() < 2 || content.length() > 2000) {
            throw new IllegalArgumentException("Nội dung góp ý phải từ 2 đến 2000 ký tự.");
        }

        CommentEntity comment = CommentEntity.builder()
                .reportId(reportId)
                .authorId(authorId)
                .content(content)
                .build();

        return toResponse(commentRepository.save(comment), report, author);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentResponse> getByReport(Long reportId, Long currentUserId) {
        ReportEntity report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report", reportId));
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", currentUserId));
        if (!canViewReport(currentUser, report)) {
            throw new AccessDeniedException(VIEW_DENIED_MESSAGE);
        }

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

    private boolean canViewReport(User user, ReportEntity report) {
        if (user.hasRole("LAB_MANAGER")) {
            return managerOwnsReportLab(user.getId(), report);
        }
        if (!user.hasRole("STUDENT")) {
            return false;
        }
        Optional<GroupRole> groupRole = findReportGroupRole(report, user.getId());
        if (groupRole.isEmpty()) {
            return false;
        }
        if (groupRole.get() == GroupRole.LEADER) {
            return true;
        }
        return user.getId().equals(report.getSubmittedById()) || ALLOW_GROUP_COMMENT_EXCHANGE;
    }

    private boolean managerOwnsReportLab(Long managerId, ReportEntity report) {
        MilestoneEntity milestone = milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(report.getMilestoneId())
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", report.getMilestoneId()));
        Long reportLabId = milestone.getProject().getLab() == null
                ? null
                : milestone.getProject().getLab().getId();
        if (reportLabId == null) {
            return false;
        }
        return laboratoryRepository.findFirstByManagerIdAndDeletedFalse(managerId)
                .map(Laboratory::getId)
                .filter(reportLabId::equals)
                .isPresent();
    }

    private Optional<GroupRole> findReportGroupRole(ReportEntity report, Long userId) {
        if (report.getGroupId() != null) {
            return groupMemberRepository.findActiveRoleByGroupIdAndUserId(report.getGroupId(), userId);
        }
        MilestoneEntity milestone = milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(report.getMilestoneId())
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", report.getMilestoneId()));
        return groupMemberRepository.findActiveRoleByProjectIdAndUserId(milestone.getProject().getId(), userId);
    }
}
