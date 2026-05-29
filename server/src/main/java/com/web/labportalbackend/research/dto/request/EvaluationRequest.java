package com.web.labportalbackend.research.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Schema(description = "Request body for evaluating a student's research result")
public class EvaluationRequest {

    @NotNull(message = "Project ID is required")
    @JsonAlias("project_id")
    @Schema(description = "Research project ID", example = "10")
    private Long projectId;

    @NotNull(message = "Student ID is required")
    @JsonAlias("student_id")
    @Schema(description = "Evaluated student user ID", example = "12")
    private Long studentId;

    @NotNull(message = "Contribution score is required")
    @DecimalMin(value = "0.0", message = "Contribution score must be at least 0.0")
    @DecimalMax(value = "10.0", message = "Contribution score must not exceed 10.0")
    @JsonAlias("contribution_score")
    private BigDecimal contributionScore;

    @NotNull(message = "Task score is required")
    @DecimalMin(value = "0.0", message = "Task score must be at least 0.0")
    @DecimalMax(value = "10.0", message = "Task score must not exceed 10.0")
    @JsonAlias("task_score")
    private BigDecimal taskScore;

    @NotNull(message = "Report score is required")
    @DecimalMin(value = "0.0", message = "Report score must be at least 0.0")
    @DecimalMax(value = "10.0", message = "Report score must not exceed 10.0")
    @JsonAlias("report_score")
    private BigDecimal reportScore;

    @NotNull(message = "Product score is required")
    @DecimalMin(value = "0.0", message = "Product score must be at least 0.0")
    @DecimalMax(value = "10.0", message = "Product score must not exceed 10.0")
    @JsonAlias("product_score")
    private BigDecimal productScore;

    @NotNull(message = "Attitude score is required")
    @DecimalMin(value = "0.0", message = "Attitude score must be at least 0.0")
    @DecimalMax(value = "10.0", message = "Attitude score must not exceed 10.0")
    @JsonAlias("attitude_score")
    private BigDecimal attitudeScore;

    @Size(max = 5000, message = "Lecturer comment must not exceed 5000 characters")
    @JsonAlias({"lecturer_comment", "comments"})
    @Schema(description = "Lecturer comment", example = "Sinh viên hoàn thành tốt nhiệm vụ.")
    private String lecturerComment;
}
