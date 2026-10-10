package com.web.labportalbackend.common.exception;

import com.web.labportalbackend.ai.client.AiGatewayException;
import com.web.labportalbackend.ai.context.AiContextReadDeniedException;
import com.web.labportalbackend.ai.service.AiAssistantAvailabilityException;
import com.web.labportalbackend.ai.service.AiAssistantAvailabilityFailure;
import com.web.labportalbackend.ai.service.AiCapabilityDeniedException;
import com.web.labportalbackend.face.client.FaceServiceException;
import com.web.labportalbackend.common.dto.Response;
import com.web.labportalbackend.research.exception.TaskProposalReviewConflictException;
import lombok.RequiredArgsConstructor;
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
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;

/**
 * Global exception handler for the entire application.
 * Converts exceptions into consistent {@link Response} payloads.
 */
@Slf4j
@RequiredArgsConstructor
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Map<String, String> LOCALIZED_MESSAGE_KEYS = Map.ofEntries(
            Map.entry("Chỉ sinh viên mới được tự check-in.", "backend.checkin.studentOnly"),
            Map.entry("Only students can perform self check-in.", "backend.checkin.studentOnly"),
            Map.entry("Bạn không có quyền check-in cho đăng ký này.", "backend.checkin.bookingOwnerDenied"),
            Map.entry("You are not allowed to check in for this booking.", "backend.checkin.bookingOwnerDenied"),
            Map.entry("PTN chưa bật tự check-in bằng vị trí và mạng.", "backend.checkin.selfDisabled"),
            Map.entry("Self check-in by location and network is not enabled for this laboratory.", "backend.checkin.selfDisabled"),
            Map.entry("Sai IP/mạng: mạng hiện tại không được phép check-in tại PTN này.", "backend.checkin.networkDenied"),
            Map.entry("Network/IP check failed: the current network is not allowed at this laboratory.", "backend.checkin.networkDenied"),
            Map.entry("Sai GPS: độ chính xác GPS hiện tại chưa đạt yêu cầu của PTN.", "backend.checkin.gpsAccuracy"),
            Map.entry("GPS check failed: the current GPS accuracy does not meet the laboratory requirement.", "backend.checkin.gpsAccuracy"),
            Map.entry("Sai GPS: vị trí hiện tại nằm ngoài bán kính check-in của PTN.", "backend.checkin.gpsOutsideRadius"),
            Map.entry("GPS check failed: the current location is outside the laboratory check-in radius.", "backend.checkin.gpsOutsideRadius"),
            Map.entry("Chỉ sinh viên mới được tạo mã QR check-in.", "backend.checkin.qrStudentOnly"),
            Map.entry("Only students can create a check-in QR request.", "backend.checkin.qrStudentOnly"),
            Map.entry("Đã quá thời gian check-in.", "backend.checkin.windowExpired"),
            Map.entry("The check-in window has closed.", "backend.checkin.windowExpired"),
            Map.entry("PTN chưa có quản lý để duyệt yêu cầu QR.", "backend.checkin.managerMissing"),
            Map.entry("The laboratory has no manager to review the QR request.", "backend.checkin.managerMissing"),
            Map.entry("Không tìm thấy yêu cầu QR còn hiệu lực.", "backend.checkin.qrNotFound"),
            Map.entry("No active QR request was found.", "backend.checkin.qrNotFound"),
            Map.entry("Yêu cầu QR không tồn tại hoặc đã hết hạn.", "backend.checkin.qrMissingOrExpired"),
            Map.entry("The QR request does not exist or has expired.", "backend.checkin.qrMissingOrExpired"),
            Map.entry("Bạn không có quyền duyệt yêu cầu QR của PTN này.", "backend.checkin.qrReviewDenied"),
            Map.entry("You are not allowed to review QR requests for this laboratory.", "backend.checkin.qrReviewDenied"),
            Map.entry("Yêu cầu QR đã được xử lý.", "backend.checkin.qrProcessed"),
            Map.entry("The QR request has already been processed.", "backend.checkin.qrProcessed"),
            Map.entry("Yêu cầu QR đã hết hạn.", "backend.checkin.qrExpired"),
            Map.entry("The QR request has expired.", "backend.checkin.qrExpired"),
            Map.entry("Chỉ quản lý PTN mới được xác nhận có mặt.", "backend.checkin.managerOnly"),
            Map.entry("Only laboratory managers can confirm attendance.", "backend.checkin.managerOnly"),
            Map.entry("Chỉ quản lý PTN mới được check-in thủ công.", "backend.checkin.manualManagerOnly"),
            Map.entry("Only laboratory managers can perform manual check-in.", "backend.checkin.manualManagerOnly"),
            Map.entry("Bạn không có quyền tạo mã QR cho đăng ký này.", "backend.checkin.qrCreateDenied"),
            Map.entry("You are not allowed to create a QR request for this booking.", "backend.checkin.qrCreateDenied"),
            Map.entry("Không tìm thấy đăng ký sử dụng PTN.", "backend.checkin.bookingNotFound"),
            Map.entry("Booking not found", "backend.checkin.bookingNotFound"),
            Map.entry("Bạn không có quyền xác nhận cho PTN này.", "backend.checkin.bookingDenied"),
            Map.entry("You are not allowed to confirm attendance for this laboratory.", "backend.checkin.bookingDenied"),
            Map.entry("Chưa đến giờ check-in. Có thể check-in trước giờ bắt đầu 5 phút.", "backend.checkin.tooEarly"),
            Map.entry("The check-in window has not opened. Check-in is available from 5 minutes before the start.", "backend.checkin.tooEarly"),
            Map.entry("Ca sử dụng PTN đã kết thúc.", "backend.checkin.bookingEnded"),
            Map.entry("The laboratory session has ended.", "backend.checkin.bookingEnded"),
            Map.entry("Đã xác nhận có mặt.", "backend.checkin.alreadyCheckedIn"),
            Map.entry("Attendance has already been confirmed.", "backend.checkin.alreadyCheckedIn"),
            Map.entry("Đăng ký chưa được phê duyệt.", "backend.checkin.notApproved"),
            Map.entry("Only an approved booking can be checked in", "backend.checkin.notApproved"),
            Map.entry("Khung giờ sử dụng đã bị hủy.", "backend.checkin.slotCancelled"),
            Map.entry("Booking time slot is not available", "backend.checkin.slotCancelled"),
            Map.entry("Cấu hình vị trí và độ chính xác GPS của PTN chưa đầy đủ.", "backend.checkin.locationNotConfigured"),
            Map.entry("Mã QR không hợp lệ hoặc đã hết hạn.", "backend.checkin.invalidQrToken"),
            Map.entry("Yêu cầu đăng nhập.", "backend.auth.required"),
            Map.entry("Authentication is required", "backend.auth.required"),
            Map.entry("Face service returned an invalid guidance result", "backend.face.invalidGuidance"),
            Map.entry("Face service returned an invalid passive observation session", "backend.face.invalidObservation"),
            Map.entry("Face check-in is limited to the booking owner", "backend.face.ownerOnly"),
            Map.entry("Face check-in is disabled", "backend.face.disabled"),
            Map.entry("Ca đã chọn không thuộc PTN đang quản lý hoặc không có lượt đặt hợp lệ", "backend.face.managerSlotDenied"),
            Map.entry("Không có người đăng ký nào trong ca đã có hồ sơ khuôn mặt hoạt động", "backend.face.noCandidate"),
            Map.entry("Face service returned an invalid match result", "backend.face.invalidMatch"),
            Map.entry("Face check-in operation is limited to laboratory managers", "backend.face.managerOnly"),
            Map.entry("Student face check-in is limited to students", "backend.face.studentOnly"),
            Map.entry("Granted face consent is required", "backend.face.consentRequired"),
            Map.entry("Stored face embedding is invalid", "backend.face.invalidEmbedding"),
            Map.entry("Stored face embeddings have inconsistent dimensions", "backend.face.inconsistentEmbedding"),
            Map.entry("Face image size is invalid", "backend.face.imageSize"),
            Map.entry("Face image must be valid base64", "backend.face.imageBase64")
    );

    private final MessageSource messageSource;

    private String message(String key, Object... arguments) {
        return messageSource.getMessage(
                key,
                arguments,
                key,
                LocaleContextHolder.getLocale()
        );
    }

    private String localizedExceptionMessage(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return rawMessage;
        }
        String key = LOCALIZED_MESSAGE_KEYS.get(rawMessage);
        return key == null ? rawMessage : message(key);
    }

    @ExceptionHandler(AiAssistantAvailabilityException.class)
    public ResponseEntity<Response<Void>> handleAiAssistantAvailability(AiAssistantAvailabilityException ex) {
        AiAssistantAvailabilityFailure failure = ex.failure();
        HttpStatus status = switch (failure) {
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case QUOTA_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.FORBIDDEN;
        };
        String message = switch (failure) {
            case UNAUTHENTICATED -> message("common.unauthorized");
            case QUOTA_EXCEEDED -> message("ai.quotaExceeded");
            default -> message("ai.unavailable");
        };
        log.warn("AI assistant availability denied: failure={}", failure);
        return ResponseEntity.status(status).body(Response.error(status.value(), message));
    }

    @ExceptionHandler({AiCapabilityDeniedException.class, AiContextReadDeniedException.class})
    public ResponseEntity<Response<Void>> handleAiAssistantAuthorization(RuntimeException ex) {
        log.warn("AI assistant authorization or context projection denied");
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Response.error(HttpStatus.FORBIDDEN.value(),
                        message("ai.unavailable")));
    }

    @ExceptionHandler(AiGatewayException.class)
    public ResponseEntity<Response<Void>> handleAiGateway(AiGatewayException ex) {
        log.warn("AI gateway request failed: category={}, statusCode={}",
                ex.failure().category(), ex.failure().statusCode());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Response.error(HttpStatus.SERVICE_UNAVAILABLE.value(),
                        message("ai.temporarilyUnavailable")));
    }

    @ExceptionHandler(FaceServiceException.class)
    public ResponseEntity<Response<Void>> handleFaceService(FaceServiceException ex) {
        log.warn("Face service request failed");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Response.error(HttpStatus.SERVICE_UNAVAILABLE.value(),
                        message("face.temporarilyUnavailable")));
    }

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
                .body(Response.badRequest(message("common.validation"), fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Response<Void>> handleUnreadableRequest(HttpMessageNotReadableException ex) {
        log.warn("Request body cannot be parsed: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(Response.badRequest(message("common.requestBodyInvalid")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Response<Void>> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Request parameter has an invalid value: {}", ex.getName());
        return ResponseEntity.badRequest()
                .body(Response.badRequest(message("common.requestParameterInvalid")));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Response<Void>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        log.warn("Unsupported request media type: {}", ex.getContentType());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(Response.error(message("common.unsupportedMediaType")));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Response<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("Uploaded file exceeds configured size limit: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(Response.badRequest(message("common.fileTooLarge")));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Response<Void>> handleMultipart(MultipartException ex) {
        log.warn("Multipart request cannot be processed: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(Response.badRequest(message("common.uploadFailed")));
    }

    // ---- Not Found ----

    @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
    public ResponseEntity<Response<Void>> handleEntityNotFound(jakarta.persistence.EntityNotFoundException ex) {
        log.warn("Entity not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Response.notFound(localizedExceptionMessage(ex.getMessage())));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Response<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Response.notFound(localizedExceptionMessage(ex.getMessage())));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Response<Void>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Response.notFound(message("common.resourceNotFound", ex.getResourcePath())));
    }

    // ---- Auth ----

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Response<Void>> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Response.unauthorized(message("common.authenticationFailed")));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Response<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        String reason = localizedExceptionMessage(ex.getMessage());
        if (reason != null && !reason.isBlank() && !"Access Denied".equalsIgnoreCase(reason)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Response.forbidden(reason));
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Response.forbidden(message("common.forbidden")));
    }

    // ---- Business Rule ----

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Response<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Response.badRequest(localizedExceptionMessage(ex.getMessage())));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Response<Void>> handleIllegalState(IllegalStateException ex) {
        log.warn("Illegal state: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Response.badRequest(localizedExceptionMessage(ex.getMessage())));
    }

    @ExceptionHandler(InvalidDateRangeException.class)
    public ResponseEntity<Response<Void>> handleInvalidDateRange(InvalidDateRangeException ex) {
        log.warn("Invalid date range: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Response.badRequest(localizedExceptionMessage(ex.getMessage())));
    }

    @ExceptionHandler(InvalidAssigneeException.class)
    public ResponseEntity<Response<Void>> handleInvalidAssignee(InvalidAssigneeException ex) {
        log.warn("Invalid assignee: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Response.badRequest(localizedExceptionMessage(ex.getMessage())));
    }

    @ExceptionHandler(InvalidEvaluationScoreException.class)
    public ResponseEntity<Response<Void>> handleInvalidEvaluationScore(InvalidEvaluationScoreException ex) {
        log.warn("Invalid evaluation score: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Response.badRequest(localizedExceptionMessage(ex.getMessage())));
    }

    @ExceptionHandler(SlotFullException.class)
    public ResponseEntity<Response<Void>> handleSlotFull(SlotFullException ex) {
        log.warn("Slot full: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Response.conflict(localizedExceptionMessage(ex.getMessage())));
    }

    @ExceptionHandler(DuplicateBookingException.class)
    public ResponseEntity<Response<Void>> handleDuplicateBooking(DuplicateBookingException ex) {
        log.warn("Duplicate booking: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Response.conflict(localizedExceptionMessage(ex.getMessage())));
    }

    @ExceptionHandler(DuplicateMemberException.class)
    public ResponseEntity<Response<Void>> handleDuplicateMember(DuplicateMemberException ex) {
        log.warn("Duplicate group member: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Response.conflict(ex.getMessage()));
    }

    @ExceptionHandler(WebsiteRevisionConflictException.class)
    public ResponseEntity<Response<Void>> handleWebsiteRevisionConflict(WebsiteRevisionConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Response.conflict(ex.getMessage()));
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

    @ExceptionHandler(TaskProposalReviewConflictException.class)
    public ResponseEntity<Response<Void>> handleTaskProposalReviewConflict(
            TaskProposalReviewConflictException ex
    ) {
        log.warn("Task proposal review conflict: {}", ex.getMessage());
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
                .body(Response.badRequest(message("checkin.validationFailed", localizedExceptionMessage(ex.getMessage()))));
    }

    // ---- Catch-all ----

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response<Void>> handleGeneral(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Response.error(message("common.unexpectedError")));
    }
}
