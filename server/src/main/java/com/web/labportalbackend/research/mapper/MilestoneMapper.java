package com.web.labportalbackend.research.mapper;

import com.web.labportalbackend.research.dto.response.MilestoneResponse;
import com.web.labportalbackend.research.entity.MilestoneEntity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MilestoneMapper {

    public static MilestoneResponse toResponse(MilestoneEntity milestone) {
        return toResponse(milestone, null, null);
    }

    public static MilestoneResponse toResponse(MilestoneEntity milestone, Integer myTaskCount, Integer myCompletedTaskCount) {
        return MilestoneResponse.builder()
                .id(milestone.getId())
                .projectId(milestone.getProject().getId())
                .projectTitle(milestone.getProject().getTitle())
                .groupId(milestone.getGroup() != null ? milestone.getGroup().getId() : null)
                .groupName(milestone.getGroup() != null ? milestone.getGroup().getName() : null)
                .title(milestone.getTitle() != null ? milestone.getTitle() : milestone.getName())
                .description(milestone.getDescription())
                .assignedToStudentId(milestone.getAssignedToStudent() != null
                        ? milestone.getAssignedToStudent().getId()
                        : null)
                .assignedToStudentName(milestone.getAssignedToStudent() != null
                        ? milestone.getAssignedToStudent().getFullName() != null
                            ? milestone.getAssignedToStudent().getFullName()
                            : milestone.getAssignedToStudent().getEmail()
                        : null)
                .deadline(milestone.getDeadline() != null ? milestone.getDeadline() : milestone.getEndDate())
                .status(toPublicStatus(milestone.getStatus()))
                .progressPercent(milestone.getProgressPercent() != null ? milestone.getProgressPercent() : 0)
                .evidenceUrl(milestone.getEvidenceUrl())
                .managerComment(milestone.getManagerComment())
                .myTaskCount(myTaskCount)
                .myCompletedTaskCount(myCompletedTaskCount)
                .createdById(milestone.getCreatedBy() != null ? milestone.getCreatedBy().getId() : null)
                .createdByName(milestone.getCreatedBy() != null
                        ? milestone.getCreatedBy().getFullName() != null
                            ? milestone.getCreatedBy().getFullName()
                            : milestone.getCreatedBy().getEmail()
                        : null)
                .createdAt(milestone.getCreatedAt())
                .updatedAt(milestone.getUpdatedAt())
                .build();
    }

    private static com.web.labportalbackend.research.enums.MilestoneStatus toPublicStatus(
            com.web.labportalbackend.research.enums.MilestoneStatus status
    ) {
        if (status == com.web.labportalbackend.research.enums.MilestoneStatus.PLANNED) {
            return com.web.labportalbackend.research.enums.MilestoneStatus.NOT_STARTED;
        }
        if (status == com.web.labportalbackend.research.enums.MilestoneStatus.DELAYED) {
            return com.web.labportalbackend.research.enums.MilestoneStatus.OVERDUE;
        }
        return status;
    }
}
