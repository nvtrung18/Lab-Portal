package com.web.labportalbackend.research.dto.response;

import lombok.Builder;
import lombok.Getter;
import com.web.labportalbackend.research.enums.ReportStatus;

import java.time.Instant;

@Getter
@Builder
public class ReportResponse {
    private Long id;
    private Long projectId;
    private Long groupId;
    private Long milestoneId;
    private Long taskId;
    private Long submittedById;
    private String submittedByName;
    private String submittedByEmail;
    private String groupName;
    private String milestoneTitle;
    private String taskTitle;
    private Integer version;
    private String title;
    private String contentDone;
    private String result;
    private String difficulty;
    private String nextPlan;
    private String selfAssessment;
    private String fileUrl;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String evidenceLink;
    private ReportStatus status;
    private Instant leaderReviewedAt;
    private String leaderComment;
    private Instant managerReviewedAt;
    private String managerComment;
    private Instant createdAt;
    private Instant updatedAt;
}
