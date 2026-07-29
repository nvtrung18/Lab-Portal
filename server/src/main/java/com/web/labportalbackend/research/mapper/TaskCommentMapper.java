package com.web.labportalbackend.research.mapper;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.research.dto.response.TaskCommentResponse;
import com.web.labportalbackend.research.entity.TaskCommentEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TaskCommentMapper {

    public static TaskCommentResponse toResponse(
            TaskCommentEntity comment,
            User author,
            String authorRole,
            GroupRole groupRole
    ) {
        return TaskCommentResponse.builder()
                .id(comment.getId())
                .taskId(comment.getTaskId())
                .authorId(comment.getAuthorId())
                .authorName(author.getFullName())
                .authorEmail(author.getEmail())
                .authorRole(authorRole)
                .groupRole(groupRole)
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
