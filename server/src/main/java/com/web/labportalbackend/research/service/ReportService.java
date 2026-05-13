package com.web.labportalbackend.research.service;

import com.web.labportalbackend.research.dto.request.SubmitReportRequest;
import com.web.labportalbackend.research.dto.response.ReportResponse;

import java.util.List;

public interface ReportService {
    ReportResponse submitReport(SubmitReportRequest request);

    List<ReportResponse> getReportsByTask(Long taskId);
}
