package com.web.labportalbackend.research.service.impl;

import com.web.labportalbackend.common.exception.ReportVersionConflictException;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.research.dto.request.SubmitReportRequest;
import com.web.labportalbackend.research.dto.response.ReportResponse;
import com.web.labportalbackend.research.entity.ReportEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.mapper.ReportMapper;
import com.web.labportalbackend.research.repository.ReportRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
import com.web.labportalbackend.research.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final TaskRepository taskRepository;

    @Override
    @Transactional
    public ReportResponse submitReport(SubmitReportRequest request) {
        TaskEntity task = taskRepository.findByIdForUpdate(request.getTaskId())
                .orElseThrow(() -> new ResourceNotFoundException("Task", request.getTaskId()));

        // TODO: Enforce submit permission: task assignee or project leader only.
        Integer currentMaxVersion = reportRepository.findMaxVersionByTaskId(task.getId()).orElse(0);
        ReportEntity report = ReportEntity.builder()
                .taskId(task.getId())
                .version(currentMaxVersion + 1)
                .fileUrl(request.getFileUrl())
                .build();

        try {
            return ReportMapper.toResponse(reportRepository.saveAndFlush(report));
        } catch (DataIntegrityViolationException ex) {
            throw new ReportVersionConflictException(task.getId());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportResponse> getReportsByTask(Long taskId) {
        if (!taskRepository.existsById(taskId)) {
            throw new ResourceNotFoundException("Task", taskId);
        }
        return reportRepository.findByTaskIdOrderByVersionDesc(taskId)
                .stream()
                .map(ReportMapper::toResponse)
                .toList();
    }
}
