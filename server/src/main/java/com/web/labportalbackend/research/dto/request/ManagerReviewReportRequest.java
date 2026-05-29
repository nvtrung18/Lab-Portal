package com.web.labportalbackend.research.dto.request;

import com.web.labportalbackend.research.enums.ManagerReportDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ManagerReviewReportRequest {

    @NotNull(message = "Quyết định duyệt báo cáo là bắt buộc.")
    private ManagerReportDecision decision;

    @NotBlank(message = "Nhận xét của quản lý không được để trống.")
    @Size(max = 5000, message = "Nhận xét của quản lý không được vượt quá 5000 ký tự.")
    private String comment;
}
