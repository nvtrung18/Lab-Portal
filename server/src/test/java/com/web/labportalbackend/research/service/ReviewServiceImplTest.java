package com.web.labportalbackend.research.service;

import com.web.labportalbackend.research.dto.request.CreateCommentRequest;
import com.web.labportalbackend.research.dto.response.CommentResponse;
import com.web.labportalbackend.research.entity.CommentEntity;
import com.web.labportalbackend.research.repository.CommentRepository;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
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
    private GroupMemberRepository groupMemberRepository;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    @Test
    void addComment_createsCommentWhenAuthorIsProjectMember() {
        CreateCommentRequest request = request("Looks good, please add the appendix.");

        when(reportRepository.existsById(10L)).thenReturn(true);
        when(groupMemberRepository.existsByReportIdAndUserId(10L, 7L)).thenReturn(true);
        when(commentRepository.save(any(CommentEntity.class))).thenAnswer(invocation -> {
            CommentEntity comment = invocation.getArgument(0);
            comment.setId(100L);
            comment.setCreatedAt(Instant.parse("2026-05-14T01:00:00Z"));
            return comment;
        });

        CommentResponse response = reviewService.addComment(10L, 7L, request);

        assertEquals(100L, response.getId());
        assertEquals(7L, response.getAuthorId());
        assertEquals("Looks good, please add the appendix.", response.getContent());
        assertEquals(Instant.parse("2026-05-14T01:00:00Z"), response.getCreatedAt());

        ArgumentCaptor<CommentEntity> captor = ArgumentCaptor.forClass(CommentEntity.class);
        verify(commentRepository).save(captor.capture());
        assertEquals(10L, captor.getValue().getReportId());
        assertEquals(7L, captor.getValue().getAuthorId());
        assertEquals("Looks good, please add the appendix.", captor.getValue().getContent());
    }

    @Test
    void addComment_rejectsWhenAuthorIsNotProjectMember() {
        CreateCommentRequest request = request("I should not be able to comment.");

        when(reportRepository.existsById(10L)).thenReturn(true);
        when(groupMemberRepository.existsByReportIdAndUserId(10L, 7L)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> reviewService.addComment(10L, 7L, request));
        verify(commentRepository, never()).save(any(CommentEntity.class));
    }

    @Test
    void getByReport_returnsCommentsInCreatedAtAscendingOrder() {
        CommentEntity older = comment(1L, 10L, 7L, "First", "2026-05-14T01:00:00Z");
        CommentEntity newer = comment(2L, 10L, 8L, "Second", "2026-05-14T02:00:00Z");

        when(reportRepository.existsById(10L)).thenReturn(true);
        when(commentRepository.findByReportIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(older, newer));

        List<CommentResponse> responses = reviewService.getByReport(10L);

        assertEquals(2, responses.size());
        assertEquals(Instant.parse("2026-05-14T01:00:00Z"), responses.get(0).getCreatedAt());
        assertEquals(Instant.parse("2026-05-14T02:00:00Z"), responses.get(1).getCreatedAt());
        verify(commentRepository).findByReportIdOrderByCreatedAtAsc(10L);
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
}
