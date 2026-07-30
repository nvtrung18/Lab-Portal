package com.web.labportalbackend.research.service;

import com.web.labportalbackend.research.dto.request.CreateTaskProposalRequest;
import com.web.labportalbackend.research.dto.response.TaskProposalResponse;

public interface TaskProposalService {
    TaskProposalResponse submit(CreateTaskProposalRequest request);
}
