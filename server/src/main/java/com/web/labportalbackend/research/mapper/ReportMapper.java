package com.web.labportalbackend.research.mapper;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.research.dto.response.ReportResponse;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.MilestoneEntity;
import com.web.labportalbackend.research.entity.ReportEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ReportMapper {

    public static ReportResponse toResponse(ReportEntity report) {
        return toResponse(report, null);
    }

    public static ReportResponse toResponse(ReportEntity report, User submitter) {
        return toResponse(report, submitter, null, null, null);
    }

    public static ReportResponse toResponse(
            ReportEntity report,
            User submitter,
            GroupEntity group,
            MilestoneEntity milestone,
            TaskEntity task
    ) {
        return ReportResponse.builder()
                .id(report.getId())
                .submittedByGroupRole(null)
                .isLatestVersion(null)
                .projectId(report.getProjectId())
                .groupId(report.getGroupId())
                .milestoneId(report.getMilestoneId())
                .taskId(report.getTaskId())
                .submittedById(report.getSubmittedById())
                .submittedByName(submitter == null ? null : submitter.getFullName())
                .submittedByEmail(submitter == null ? null : submitter.getEmail())
                .groupName(group == null ? null : group.getName())
                .milestoneTitle(milestone == null ? null : milestone.getTitle())
                .taskTitle(task == null ? null : task.getTitle())
                .version(report.getVersion())
                .title(report.getTitle())
                .contentDone(report.getContentDone())
                .result(report.getResult())
                .difficulty(report.getDifficulty())
                .nextPlan(report.getNextPlan())
                .selfAssessment(report.getSelfAssessment())
                .fileUrl(report.getFileUrl())
                .fileName(report.getFileName())
                .fileType(report.getFileType())
                .fileSize(report.getFileSize())
                .evidenceLink(report.getEvidenceLink())
                .status(report.getStatus())
                .leaderReviewerId(report.getLeaderReviewerId())
                .leaderReviewedAt(report.getLeaderReviewedAt())
                .leaderComment(report.getLeaderComment())
                .managerReviewerId(report.getManagerReviewerId())
                .managerReviewedAt(report.getManagerReviewedAt())
                .managerComment(report.getManagerComment())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .build();
    }
}
