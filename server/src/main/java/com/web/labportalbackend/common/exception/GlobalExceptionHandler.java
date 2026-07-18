package com.web.labportalbackend.common.exception;

import com.web.labportalbackend.common.dto.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Global exception handler for the entire application.
 * Converts exceptions into consistent {@link Response} payloads.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ---- Validation ----

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Response<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        log.warn("Validation failed: {}", fieldErrors);
        return ResponseEntity.badRequest()
                .body(Response.badRequest("Validation failed", fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Response<Void>> handleUnreadableRequest(HttpMessageNotReadableException ex) {
        log.warn("Request body cannot be parsed: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(Response.badRequest("Request body contains an invalid value"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Response<Void>> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Request parameter has an invalid value: {}", ex.getName());
        return ResponseEntity.badRequest()
                .body(Response.badRequest("Request contains an invalid parameter value"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Response<Void>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        log.warn("Unsupported request media type: {}", ex.getContentType());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(Response.error("Content-Type không được hỗ trợ. Vui lòng gửi multipart/form-data khi tải file."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Response<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("Uploaded file exceeds configured size limit: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(Response.badRequest("File quá lớn. Vui lòng chọn file nhỏ hơn giới hạn cho phép."));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Response<Void>> handleMultipart(MultipartException ex) {
        log.warn("Multipart request cannot be processed: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(Response.badRequest("Không thể xử lý file tải lên. Vui lòng kiểm tra lại file."));
    }

    // ---- Not Found ----

    @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
    public ResponseEntity<Response<Void>> handleEntityNotFound(jakarta.persistence.EntityNotFoundException ex) {
        log.warn("Entity not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Response.notFound(ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Response<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Response.notFound(ex.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Response<Void>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Response.notFound("Resource not found: " + ex.getResourcePath()));
    }

    // ---- Auth ----

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Response<Void>> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Response.unauthorized("Authentication failed: " + ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Response<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        String message = ex.getMessage() == null || ex.getMessage().isBlank()
                ? "Access denied: insufficient permissions"
                : ex.getMessage();
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Response.forbidden(message));
    }

    // ---- Business Rule ----

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Response<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Response.badRequest(ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Response<Void>> handleIllegalState(IllegalStateException ex) {
        log.warn("Illegal state: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Response.badRequest(ex.getMessage()));
    }

    @ExceptionHandler(InvalidDateRangeException.class)
    public ResponseEntity<Response<Void>> handleInvalidDateRange(InvalidDateRangeException ex) {
        log.warn("Invalid date range: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Response.badRequest(ex.getMessage()));
    }

    @ExceptionHandler(InvalidAssigneeException.class)
    public ResponseEntity<Response<Void>> handleInvalidAssignee(InvalidAssigneeException ex) {
        log.warn("Invalid assignee: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Response.badRequest(ex.getMessage()));
    }

    @ExceptionHandler(InvalidEvaluationScoreException.class)
    public ResponseEntity<Response<Void>> handleInvalidEvaluationScore(InvalidEvaluationScoreException ex) {
        log.warn("Invalid evaluation score: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Response.badRequest(ex.getMessage()));
    }

    @ExceptionHandler(SlotFullException.class)
    public ResponseEntity<Response<Void>> handleSlotFull(SlotFullException ex) {
        log.warn("Slot full: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Response.conflict(ex.getMessage()));
    }

    @ExceptionHandler(DuplicateBookingException.class)
    public ResponseEntity<Response<Void>> handleDuplicateBooking(DuplicateBookingException ex) {
        log.warn("Duplicate booking: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Response.conflict(ex.getMessage()));
    }

    @ExceptionHandler(DuplicateMemberException.class)
    public ResponseEntity<Response<Void>> handleDuplicateMember(DuplicateMemberException ex) {
        log.warn("Duplicate group member: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Response.conflict(ex.getMessage()));
    }

    @ExceptionHandler(ReportVersionConflictException.class)
    public ResponseEntity<Response<Void>> handleReportVersionConflict(ReportVersionConflictException ex) {
        log.warn("Report version conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Response.conflict(ex.getMessage()));
    }

    @ExceptionHandler(DuplicateApplicationException.class)
    public ResponseEntity<Response<Void>> handleDuplicateApplication(DuplicateApplicationException ex) {
        log.warn("Duplicate application: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Response.conflict(ex.getMessage()));
    }

    @ExceptionHandler(ApplicationAlreadyReviewedException.class)
    public ResponseEntity<Response<Void>> handleAlreadyReviewed(ApplicationAlreadyReviewedException ex) {
        log.warn("Already reviewed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Response.conflict(ex.getMessage()));
    }

    @ExceptionHandler(WaitlistDuplicateException.class)
    public ResponseEntity<Response<Void>> handleWaitlistDuplicate(WaitlistDuplicateException ex) {
        log.warn("Waitlist duplicate: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Response.conflict(ex.getMessage()));
    }

    @ExceptionHandler(InvalidCheckinTimeException.class)
    public ResponseEntity<Response<Void>> handleInvalidCheckinTime(InvalidCheckinTimeException ex) {
        log.warn("Check-in validation failed: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(Response.badRequest("Check-in validation failed: " + ex.getMessage()));
    }

    // ---- Catch-all ----

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response<Void>> handleGeneral(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Response.error("An unexpected error occurred. Please try again later."));
    }
}
