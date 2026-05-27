package com.web.labportalbackend.research.service;

import com.web.labportalbackend.research.dto.request.SubmitReportRequest;
import com.web.labportalbackend.research.dto.request.LeaderReviewReportRequest;
import com.web.labportalbackend.research.dto.request.ManagerReviewReportRequest;
import com.web.labportalbackend.research.dto.response.ReportResponse;
import com.web.labportalbackend.research.dto.response.ReportFileDownload;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ReportService {
    ReportResponse submitReport(SubmitReportRequest request, MultipartFile file);

    List<ReportResponse> getReportsByTask(Long taskId);

    ReportFileDownload downloadReportFile(Long reportId);

    List<ReportResponse> getReportsByMilestone(Long milestoneId);

    List<ReportResponse> getMyReportsByMilestone(Long milestoneId);

    List<ReportResponse> getReportsByGroup(Long groupId);

    List<ReportResponse> getPendingManagerReviewByLab(Long labId);

    ReportResponse leaderReview(Long reportId, LeaderReviewReportRequest request);

    ReportResponse managerReview(Long reportId, ManagerReviewReportRequest request);
}
