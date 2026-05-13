package com.web.labportalbackend.research.service;

import com.web.labportalbackend.research.dto.request.CreateCommentRequest;
import com.web.labportalbackend.research.dto.response.CommentResponse;

import java.util.List;

public interface ReviewService {
    CommentResponse addComment(Long reportId, Long authorId, CreateCommentRequest request);

    List<CommentResponse> getByReport(Long reportId);
}
