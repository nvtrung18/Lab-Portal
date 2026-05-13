package com.web.labportalbackend.research.service;

import com.web.labportalbackend.research.dto.request.EvaluationRequest;
import com.web.labportalbackend.research.dto.response.EvaluationResponse;

import java.util.List;

public interface EvaluationService {
    EvaluationResponse evaluateProject(Long projectId, EvaluationRequest request);

    List<EvaluationResponse> getByProject(Long projectId);
}
