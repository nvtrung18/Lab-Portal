package com.web.labportalbackend.research.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.web.labportalbackend.research.enums.TaskProposalStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public class TaskProposalReviewResponse {
    private Long proposalId;
    private TaskProposalStatus status;
    private Long reviewedById;
    private String reason;
    private Instant reviewedAt;
    private TaskResponse createdTask;
}
