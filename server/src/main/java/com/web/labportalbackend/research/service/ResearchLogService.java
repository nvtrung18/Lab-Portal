package com.web.labportalbackend.research.service;

import com.web.labportalbackend.research.dto.request.CreateResearchLogRequest;
import com.web.labportalbackend.research.dto.response.ResearchLogResponse;
import com.web.labportalbackend.research.enums.ResearchLogType;
import com.web.labportalbackend.research.enums.ResearchLogVisibility;

import java.util.List;

public interface ResearchLogService {
    List<ResearchLogResponse> getProjectLogs(
            Long projectId,
            Long groupId,
            Long milestoneId,
            Long taskId,
            Long authorId,
            ResearchLogType logType,
            Integer page,
            Integer size
    );

    ResearchLogResponse createManualLog(CreateResearchLogRequest request);

    void createSystemLog(
            Long projectId,
            Long groupId,
            Long milestoneId,
            Long taskId,
            Long authorId,
            String content,
            String result,
            ResearchLogVisibility visibility
    );
}
