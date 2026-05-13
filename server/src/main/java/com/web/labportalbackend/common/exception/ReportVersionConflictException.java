package com.web.labportalbackend.common.exception;

public class ReportVersionConflictException extends RuntimeException {

    public ReportVersionConflictException(Long taskId) {
        super("Report version conflict for task " + taskId + ". Please retry the submission.");
    }
}
