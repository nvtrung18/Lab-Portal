package com.web.labportalbackend.research.mapper;

import com.web.labportalbackend.research.dto.response.ResearchLogResponse;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.MilestoneEntity;
import com.web.labportalbackend.research.entity.ResearchLogEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ResearchLogMapper {

    public static ResearchLogResponse toResponse(
            ResearchLogEntity log,
            GroupEntity group,
            MilestoneEntity milestone,
            TaskEntity task,
            String authorRole,
            GroupRole groupRole
    ) {
        return ResearchLogResponse.builder()
                .id(log.getId())
                .projectId(log.getProjectId())
                .groupId(log.getGroupId())
                .groupName(group == null ? null : group.getName())
                .milestoneId(log.getMilestoneId())
                .milestoneTitle(milestone == null ? null : milestone.getTitle())
                .taskId(log.getTaskId())
                .taskTitle(task == null ? null : task.getTitle())
                .authorId(log.getAuthorId())
                .authorName(log.getAuthorName())
                .authorRole(authorRole)
                .groupRole(groupRole)
                .logType(log.getLogType())
                .workDate(log.getWorkDate())
                .durationMinutes(log.getDurationMinutes())
                .content(log.getContent())
                .result(log.getResult())
                .problem(log.getProblem())
                .nextPlan(log.getNextPlan())
                .evidenceLink(log.getEvidenceLink())
                .visibility(log.getVisibility())
                .createdAt(log.getCreatedAt())
                .updatedAt(log.getUpdatedAt())
                .build();
    }
}
