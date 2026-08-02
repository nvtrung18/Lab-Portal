package com.web.labportalbackend.research.service;

import com.web.labportalbackend.research.dto.request.CreateTaskProposalRequest;
import com.web.labportalbackend.research.dto.request.RejectTaskProposalRequest;
import com.web.labportalbackend.research.dto.response.TaskProposalResponse;
import com.web.labportalbackend.research.dto.response.TaskProposalReviewResponse;
import com.web.labportalbackend.research.dto.response.TaskProposalPageResponse;
import com.web.labportalbackend.research.enums.TaskProposalStatus;

public interface TaskProposalService {
    TaskProposalResponse submit(CreateTaskProposalRequest request);

    TaskProposalReviewResponse approve(Long proposalId);

    TaskProposalReviewResponse reject(Long proposalId, RejectTaskProposalRequest request);

    TaskProposalPageResponse list(Long projectId, Long groupId, TaskProposalStatus status, int page, int size);
}
