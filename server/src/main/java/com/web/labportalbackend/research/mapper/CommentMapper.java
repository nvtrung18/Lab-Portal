package com.web.labportalbackend.research.mapper;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.research.dto.response.CommentResponse;
import com.web.labportalbackend.research.entity.CommentEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CommentMapper {

    public static CommentResponse toResponse(
            CommentEntity comment,
            User author,
            String authorRole,
            GroupRole groupRole
    ) {
        return CommentResponse.builder()
                .id(comment.getId())
                .reportId(comment.getReportId())
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
