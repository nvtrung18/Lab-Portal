package com.web.labportalbackend.research.service.impl;

import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.research.dto.ResearchAttendanceDTO;
import com.web.labportalbackend.research.dto.response.ProjectStatsOverviewResponse;
import com.web.labportalbackend.research.port.BookingStatsPort;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.repository.ReportRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
import com.web.labportalbackend.research.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final ReportRepository reportRepository;
    private final BookingStatsPort bookingStatsPort;

    @Override
    @Transactional(readOnly = true)
    public ProjectStatsOverviewResponse getProjectStats(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project", projectId);
        }

        long totalTasks = taskRepository.countByProjectId(projectId);
        long doneTasks = taskRepository.countDoneByProjectId(projectId);
        long reportCount = reportRepository.countByProjectId(projectId);
        ResearchAttendanceDTO attendance = bookingStatsPort.getAttendanceByProject(projectId);

        return ProjectStatsOverviewResponse.builder()
                .projectId(projectId)
                .totalTasks(totalTasks)
                .doneTasks(doneTasks)
                .completionRate(calculateCompletionRate(totalTasks, doneTasks))
                .reportCount(reportCount)
                .attendance(attendance)
                .build();
    }

    private BigDecimal calculateCompletionRate(long totalTasks, long doneTasks) {
        if (totalTasks == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(doneTasks)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalTasks), 2, RoundingMode.HALF_UP);
    }
}
