package com.web.labportalbackend.research.service;

import com.web.labportalbackend.research.dto.request.CreateTaskCommentRequest;
import com.web.labportalbackend.research.dto.response.TaskCommentResponse;

public interface TaskCommentService {
    TaskCommentResponse addComment(Long taskId, CreateTaskCommentRequest request);
}
