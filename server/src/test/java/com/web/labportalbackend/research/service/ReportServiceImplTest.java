package com.web.labportalbackend.research.service;

import com.web.labportalbackend.common.exception.ReportVersionConflictException;
import com.web.labportalbackend.research.dto.request.SubmitReportRequest;
import com.web.labportalbackend.research.dto.response.ReportResponse;
import com.web.labportalbackend.research.entity.ReportEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.enums.TaskStatus;
import com.web.labportalbackend.research.repository.ReportRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
import com.web.labportalbackend.research.service.impl.ReportServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private ReportServiceImpl reportService;

    @Test
    void submitReport_createsVersionOneWhenTaskHasNoPreviousReport() {
        SubmitReportRequest request = request(10L, "https://files.local/report-v1.pdf");
        TaskEntity task = task(10L);

        when(taskRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(task));
        when(reportRepository.findMaxVersionByTaskId(10L)).thenReturn(Optional.empty());
        when(reportRepository.saveAndFlush(any(ReportEntity.class))).thenAnswer(invocation -> {
            ReportEntity report = invocation.getArgument(0);
            report.setId(100L);
            return report;
        });

        ReportResponse response = reportService.submitReport(request);

        assertEquals(100L, response.getId());
        assertEquals(10L, response.getTaskId());
        assertEquals(1, response.getVersion());
        assertEquals("https://files.local/report-v1.pdf", response.getFileUrl());

        ArgumentCaptor<ReportEntity> captor = ArgumentCaptor.forClass(ReportEntity.class);
        verify(reportRepository).saveAndFlush(captor.capture());
        assertEquals(1, captor.getValue().getVersion());
    }

    @Test
    void submitReport_createsNextVersionWhenTaskHasPreviousReport() {
        SubmitReportRequest request = request(10L, "https://files.local/report-v2.pdf");
        TaskEntity task = task(10L);

        when(taskRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(task));
        when(reportRepository.findMaxVersionByTaskId(10L)).thenReturn(Optional.of(1));
        when(reportRepository.saveAndFlush(any(ReportEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReportResponse response = reportService.submitReport(request);

        assertEquals(2, response.getVersion());

        ArgumentCaptor<ReportEntity> captor = ArgumentCaptor.forClass(ReportEntity.class);
        verify(reportRepository).saveAndFlush(captor.capture());
        assertEquals(2, captor.getValue().getVersion());
    }

    @Test
    void submitReport_throwsConflictWhenUniqueVersionConstraintFails() {
        SubmitReportRequest request = request(10L, "https://files.local/report-v2.pdf");
        TaskEntity task = task(10L);

        when(taskRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(task));
        when(reportRepository.findMaxVersionByTaskId(10L)).thenReturn(Optional.of(1));
        when(reportRepository.saveAndFlush(any(ReportEntity.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate report version"));

        assertThrows(ReportVersionConflictException.class, () -> reportService.submitReport(request));
    }

    @Test
    void getReportsByTask_returnsReportsInRepositoryOrder() {
        ReportEntity newer = report(2L, 10L, 2);
        ReportEntity older = report(1L, 10L, 1);

        when(taskRepository.existsById(10L)).thenReturn(true);
        when(reportRepository.findByTaskIdOrderByVersionDesc(10L)).thenReturn(List.of(newer, older));

        List<ReportResponse> responses = reportService.getReportsByTask(10L);

        assertEquals(2, responses.size());
        assertEquals(2, responses.get(0).getVersion());
        assertEquals(1, responses.get(1).getVersion());
        verify(reportRepository).findByTaskIdOrderByVersionDesc(10L);
    }

    private SubmitReportRequest request(Long taskId, String fileUrl) {
        SubmitReportRequest request = new SubmitReportRequest();
        request.setTaskId(taskId);
        request.setFileUrl(fileUrl);
        return request;
    }

    private TaskEntity task(Long id) {
        TaskEntity task = TaskEntity.builder()
                .milestoneId(5L)
                .title("Analyze result")
                .status(TaskStatus.TODO)
                .build();
        task.setId(id);
        return task;
    }

    private ReportEntity report(Long id, Long taskId, Integer version) {
        ReportEntity report = ReportEntity.builder()
                .taskId(taskId)
                .version(version)
                .fileUrl("https://files.local/report-v" + version + ".pdf")
                .build();
        report.setId(id);
        report.setCreatedAt(Instant.parse("2026-01-0" + version + "T00:00:00Z"));
        return report;
    }
}
