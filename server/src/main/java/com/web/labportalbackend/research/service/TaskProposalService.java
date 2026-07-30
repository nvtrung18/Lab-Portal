package com.web.labportalbackend.research.service;

import com.web.labportalbackend.research.dto.request.CreateTaskProposalRequest;
import com.web.labportalbackend.research.dto.request.RejectTaskProposalRequest;
import com.web.labportalbackend.research.dto.response.TaskProposalResponse;
import com.web.labportalbackend.research.dto.response.TaskProposalReviewResponse;

public interface TaskProposalService {
    TaskProposalResponse submit(CreateTaskProposalRequest request);

    TaskProposalReviewResponse approve(Long proposalId);

    TaskProposalReviewResponse reject(Long proposalId, RejectTaskProposalRequest request);
}
