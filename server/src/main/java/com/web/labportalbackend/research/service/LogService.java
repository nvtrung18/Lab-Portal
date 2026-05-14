package com.web.labportalbackend.research.service;

import com.web.labportalbackend.research.dto.response.LogResponse;

import java.util.List;

public interface LogService {
    void logAction(Long projectId, Long userId, String action, String details);

    List<LogResponse> getLogs(Long projectId);
}
