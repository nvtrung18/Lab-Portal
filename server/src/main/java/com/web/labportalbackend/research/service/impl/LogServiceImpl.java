package com.web.labportalbackend.research.service.impl;

import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.research.dto.response.LogResponse;
import com.web.labportalbackend.research.entity.LogEntity;
import com.web.labportalbackend.research.mapper.LogMapper;
import com.web.labportalbackend.research.repository.LogRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LogServiceImpl implements LogService {

    private final LogRepository logRepository;
    private final ProjectRepository projectRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAction(Long projectId, Long userId, String action, String details) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project", projectId);
        }
        LogEntity log = LogEntity.builder()
                .projectId(projectId)
                .userId(userId)
                .action(action)
                .details(details)
                .build();
        logRepository.save(log);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LogResponse> getLogs(Long projectId) {
        List<LogEntity> logs = projectId == null
                ? logRepository.findAllByOrderByCreatedAtDesc()
                : logRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        return logs.stream()
                .map(LogMapper::toResponse)
                .toList();
    }
}
