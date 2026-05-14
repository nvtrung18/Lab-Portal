package com.web.labportalbackend.research.service.impl;

import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.research.dto.request.CreateCommentRequest;
import com.web.labportalbackend.research.dto.response.CommentResponse;
import com.web.labportalbackend.research.entity.CommentEntity;
import com.web.labportalbackend.research.mapper.CommentMapper;
import com.web.labportalbackend.research.repository.CommentRepository;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.ReportRepository;
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

    @Override
    @Transactional
    public CommentResponse addComment(Long reportId, Long authorId, CreateCommentRequest request) {
        if (!reportRepository.existsById(reportId)) {
            throw new ResourceNotFoundException("Report", reportId);
        }

        if (!groupMemberRepository.existsByReportIdAndUserId(reportId, authorId)) {
            throw new AccessDeniedException("Only project members can comment on this report");
        }

        CommentEntity comment = CommentEntity.builder()
                .reportId(reportId)
                .authorId(authorId)
                .content(request.getContent())
                .build();

        return CommentMapper.toResponse(commentRepository.save(comment));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentResponse> getByReport(Long reportId) {
        if (!reportRepository.existsById(reportId)) {
            throw new ResourceNotFoundException("Report", reportId);
        }

        return commentRepository.findByReportIdOrderByCreatedAtAsc(reportId)
                .stream()
                .map(CommentMapper::toResponse)
                .toList();
    }
}
