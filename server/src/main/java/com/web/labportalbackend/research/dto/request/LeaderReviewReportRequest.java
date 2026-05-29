package com.web.labportalbackend.research.dto.request;

import com.web.labportalbackend.research.enums.LeaderReportDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LeaderReviewReportRequest {

    @NotNull(message = "Quyết định kiểm tra là bắt buộc.")
    private LeaderReportDecision decision;

    @NotBlank(message = "Ghi chú kiểm tra không được để trống.")
    @Size(max = 5000, message = "Ghi chú kiểm tra không được vượt quá 5000 ký tự.")
    private String comment;
}
