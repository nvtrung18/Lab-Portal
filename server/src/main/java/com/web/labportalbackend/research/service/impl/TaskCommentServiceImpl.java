package com.web.labportalbackend.research.service.impl;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.research.dto.request.CreateTaskCommentRequest;
import com.web.labportalbackend.research.dto.response.TaskCommentResponse;
import com.web.labportalbackend.research.entity.TaskCommentEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.mapper.TaskCommentMapper;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.TaskCommentRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
import com.web.labportalbackend.research.security.TaskPermissionHelper;
import com.web.labportalbackend.research.service.TaskCommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TaskCommentServiceImpl implements TaskCommentService {

    private final TaskCommentRepository taskCommentRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final TaskPermissionHelper taskPermissionHelper;

    @Override
    @Transactional
    public TaskCommentResponse addComment(Long taskId, CreateTaskCommentRequest request) {
        String content = validateAndNormalize(request);
        User author = currentAuthor();
        if (!author.hasRole("LAB_MANAGER") && !author.hasRole("STUDENT")) {
            throw new AccessDeniedException("Authenticated user role cannot create task comments");
        }

        TaskEntity task = taskRepository.findByIdAndDeletedFalseAndActiveTrue(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
        if (!taskPermissionHelper.canViewTask(author.getId(), task)) {
            throw new AccessDeniedException("Cannot comment on this task");
        }

        TaskCommentEntity saved = taskCommentRepository.save(TaskCommentEntity.builder()
                .taskId(taskId)
                .authorId(author.getId())
                .content(content)
                .build());
        String authorRole = author.hasRole("LAB_MANAGER") ? "LAB_MANAGER" : "STUDENT";
        GroupRole groupRole = author.hasRole("LAB_MANAGER") || task.getGroupId() == null
                ? null
                : groupMemberRepository.findActiveRoleByGroupIdAndUserId(task.getGroupId(), author.getId())
                        .orElse(null);
        return TaskCommentMapper.toResponse(saved, author, authorRole, groupRole);
    }

    private String validateAndNormalize(CreateTaskCommentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Task comment request is required");
        }
        if (!request.getUnknownFields().isEmpty()) {
            throw new IllegalArgumentException("Unknown task comment fields: " + request.getUnknownFields());
        }
        if (!StringUtils.hasText(request.getContent())) {
            throw new IllegalArgumentException("Task comment content is required");
        }
        String content = request.getContent().trim();
        if (content.length() < 2 || content.length() > 2000) {
            throw new IllegalArgumentException("Task comment content must be between 2 and 2000 characters");
        }
        return content;
    }

    private User currentAuthor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !StringUtils.hasText(authentication.getName())) {
            throw new AccessDeniedException("Authentication is required to create task comments");
        }
        return userRepository.findByUsername(authentication.getName())
                .filter(user -> Boolean.TRUE.equals(user.getActive()))
                .filter(user -> !Boolean.TRUE.equals(user.getDeleted()))
                .orElseThrow(() -> new AccessDeniedException("Authenticated user cannot create task comments"));
    }
}
