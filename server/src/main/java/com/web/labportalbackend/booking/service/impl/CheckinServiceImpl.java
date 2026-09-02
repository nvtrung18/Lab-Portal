package com.web.labportalbackend.booking.service.impl;

import com.web.labportalbackend.admin.audit.enums.AuditAction;
import com.web.labportalbackend.admin.audit.enums.AuditModule;
import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.booking.dto.response.BookingResponse;
import com.web.labportalbackend.booking.dto.response.CheckinQrResponse;
import com.web.labportalbackend.booking.dto.response.CheckinQrRequestResponse;
import com.web.labportalbackend.booking.entity.Booking;
import com.web.labportalbackend.booking.mapper.BookingMapper;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.booking.service.CheckinService;
import com.web.labportalbackend.booking.service.CheckinWindowPolicy;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.enums.TimeSlotStatus;
import com.web.labportalbackend.face.entity.FaceCheckinLogEntity;
import com.web.labportalbackend.face.enums.FaceCheckinMethod;
import com.web.labportalbackend.face.enums.FaceCheckinResult;
import com.web.labportalbackend.face.enums.FaceFallbackReason;
import com.web.labportalbackend.face.repository.FaceCheckinLogRepository;
import com.web.labportalbackend.face.service.FaceFallbackPolicy;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.notification.enums.NotificationEventType;
import com.web.labportalbackend.notification.enums.NotificationTargetModule;
import com.web.labportalbackend.notification.service.NotificationEmitter;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class CheckinServiceImpl implements CheckinService {

    private static final Duration QR_TOKEN_TTL = Duration.ofMinutes(2);
    private static final String TOKEN_PREFIX = "checkin:token:";
    private static final String QR_REQUEST_PREFIX = "checkin:qr-request:";
    private static final String QR_REQUEST_BOOKING_PREFIX = "checkin:qr-request:booking:";
    private static final String QR_REQUEST_LAB_PREFIX = "checkin:qr-request:lab:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final StringRedisTemplate redisTemplate;
    private final CheckinWindowPolicy checkinWindowPolicy;
    private final FaceFallbackPolicy faceFallbackPolicy;
    private final FaceCheckinLogRepository faceCheckinLogRepository;
    private final AuditLogService auditLogService;
    private final NotificationEmitter notificationEmitter;

    @Override
    @Transactional
    public CheckinQrResponse requestQrForCurrentStudent(Long bookingId, FaceFallbackReason fallbackReason, String customReason) {
        User currentUser = getCurrentUser();
        if (!currentUser.hasRole("STUDENT")) {
            throw new AccessDeniedException("Chỉ sinh viên mới được tạo mã QR check-in.");
        }

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy đăng ký sử dụng PTN."));
        validateStudentCanCreateQr(booking, currentUser);
        faceFallbackPolicy.assertQrAllowed(booking, fallbackReason);
        String normalizedReason = fallbackReason == FaceFallbackReason.OTHER
                ? normalizeCustomReason(customReason) : fallbackReason.name();

        Instant now = Instant.now();
        Instant requestExpiresAt = checkinWindowPolicy.closesAt(booking);
        Duration ttl = Duration.between(now, requestExpiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalStateException("Đã quá thời gian check-in.");
        }

        User manager = booking.getLab().getManager();
        if (manager == null) {
            throw new IllegalStateException("PTN chưa có quản lý để duyệt yêu cầu QR.");
        }
        String existingRequestId = redisTemplate.opsForValue().get(qrRequestBookingKey(bookingId));
        QrRequest existing = existingRequestId == null ? null : readQrRequest(existingRequestId);
        if (existing != null && existing.studentId().equals(currentUser.getId()) && !"REJECTED".equals(existing.status())) {
            return toStudentResponse(existing, "Yêu cầu QR đã được gửi trước đó.");
        }

        String requestId = UUID.randomUUID().toString();
        QrRequest request = new QrRequest(requestId, bookingId, currentUser.getId(), booking.getLab().getId(),
                fallbackReason, normalizedReason, "PENDING", null, requestExpiresAt);
        notificationEmitter.emit(manager.getId(), NotificationEventType.QR_CHECKIN_REQUESTED,
                "Yêu cầu QR check-in", currentUser.getFullName() + " xin QR cho ca #" + booking.getId()
                        + ". Lý do: " + normalizedReason, NotificationTargetModule.FACE, booking.getId(), null);
        writeQrRequest(request, ttl);
        redisTemplate.opsForValue().set(qrRequestBookingKey(bookingId), requestId, ttl);
        redisTemplate.opsForSet().add(qrRequestLabKey(booking.getLab().getId()), requestId);
        redisTemplate.expire(qrRequestLabKey(booking.getLab().getId()), ttl);
        return CheckinQrResponse.builder()
                .requestId(requestId)
                .status("PENDING")
                .expiresAt(requestExpiresAt)
                .message("Đã gửi yêu cầu QR tới quản lý PTN.")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CheckinQrResponse getCurrentStudentQrRequest(Long bookingId) {
        User student = getCurrentUser();
        String requestId = redisTemplate.opsForValue().get(qrRequestBookingKey(bookingId));
        QrRequest request = requestId == null ? null : readQrRequest(requestId);
        if (request == null || !request.studentId().equals(student.getId())) {
            throw new EntityNotFoundException("Không tìm thấy yêu cầu QR còn hiệu lực.");
        }
        String message = switch (request.status()) {
            case "APPROVED" -> "Quản lý đã duyệt yêu cầu QR.";
            case "REJECTED" -> "Quản lý đã từ chối yêu cầu QR.";
            default -> "Yêu cầu QR đang chờ quản lý duyệt.";
        };
        return toStudentResponse(request, message);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CheckinQrRequestResponse> getPendingQrRequestsForCurrentManager() {
        Laboratory lab = getManagedLab(getCurrentUser());
        Set<String> requestIds = redisTemplate.opsForSet().members(qrRequestLabKey(lab.getId()));
        if (requestIds == null) return List.of();
        return requestIds.stream().map(this::readQrRequest).filter(Objects::nonNull)
                .filter(request -> "PENDING".equals(request.status()))
                .map(this::toManagerResponse).toList();
    }

    @Override
    @Transactional
    public CheckinQrRequestResponse reviewQrRequestByCurrentManager(String requestId, boolean approved) {
        Laboratory lab = getManagedLab(getCurrentUser());
        QrRequest request = readQrRequest(requestId);
        if (request == null) throw new EntityNotFoundException("Yêu cầu QR không tồn tại hoặc đã hết hạn.");
        if (!request.labId().equals(lab.getId())) throw new AccessDeniedException("Bạn không có quyền duyệt yêu cầu QR của PTN này.");
        if (!"PENDING".equals(request.status())) throw new IllegalStateException("Yêu cầu QR đã được xử lý.");
        Duration remaining = Duration.between(Instant.now(), request.expiresAt());
        if (remaining.isNegative() || remaining.isZero()) throw new IllegalStateException("Yêu cầu QR đã hết hạn.");

        String token = null;
        Instant reviewedExpiresAt = request.expiresAt();
        if (approved) {
            Duration tokenTtl = remaining.compareTo(QR_TOKEN_TTL) < 0 ? remaining : QR_TOKEN_TTL;
            token = createToken();
            redisTemplate.opsForValue().set(tokenKey(token), request.bookingId() + "|"
                    + request.fallbackReason().name() + "|" + encode(request.reason()), tokenTtl);
            remaining = tokenTtl;
            reviewedExpiresAt = Instant.now().plus(tokenTtl);
        }
        QrRequest reviewed = new QrRequest(request.requestId(), request.bookingId(), request.studentId(), request.labId(),
                request.fallbackReason(), request.reason(), approved ? "APPROVED" : "REJECTED", token, reviewedExpiresAt);
        writeQrRequest(reviewed, remaining);
        return toManagerResponse(reviewed);
    }

    @Override
    @Transactional
    public BookingResponse confirmByCurrentManager(String token) {
        User currentUser = getCurrentUser();
        if (!currentUser.hasRole("LAB_MANAGER")) {
            throw new AccessDeniedException("Chỉ quản lý PTN mới được xác nhận có mặt.");
        }

        String normalizedToken = token == null ? "" : token.trim();
        String redisKey = tokenKey(normalizedToken);
        String tokenValue = redisTemplate.opsForValue().get(redisKey);
        if (tokenValue == null) {
            throw invalidQrToken();
        }
        QrFallbackToken fallbackToken = parseTokenValue(tokenValue);

        Booking booking = bookingRepository.findById(fallbackToken.bookingId())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy đăng ký sử dụng PTN."));
        validateManagerCanConfirm(booking, currentUser);
        faceFallbackPolicy.assertQrAllowed(booking, fallbackToken.reason());
        if (!Boolean.TRUE.equals(redisTemplate.delete(redisKey))) {
            throw invalidQrToken();
        }

        booking.setStatus(BookingStatus.CHECKED_IN);
        Booking saved = bookingRepository.save(booking);
        recordFallback(saved, currentUser, FaceCheckinMethod.QR_FALLBACK, fallbackToken.reasonText());
        auditLogService.log(currentUser, AuditAction.QR_FALLBACK_CHECKIN, AuditModule.FACE,
                "BOOKING", saved.getId(), "QR fallback check-in: " + fallbackToken.reasonText());
        return BookingMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public BookingResponse manualCheckinByCurrentManager(Long bookingId, String reason) {
        User currentUser = getCurrentUser();
        if (!currentUser.hasRole("LAB_MANAGER")) {
            throw new AccessDeniedException("Chỉ quản lý PTN mới được check-in thủ công.");
        }
        String normalizedReason = normalizeManualReason(reason);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy đăng ký sử dụng PTN."));
        validateManagerCanConfirm(booking, currentUser);
        faceFallbackPolicy.assertManualAllowed();

        booking.setStatus(BookingStatus.CHECKED_IN);
        Booking saved = bookingRepository.save(booking);
        recordFallback(saved, currentUser, FaceCheckinMethod.MANUAL, normalizedReason);
        auditLogService.log(currentUser, AuditAction.MANUAL_CHECKIN, AuditModule.FACE,
                "BOOKING", saved.getId(), "Manual check-in override: " + normalizedReason);
        return BookingMapper.toResponse(saved);
    }

    private void validateStudentCanCreateQr(Booking booking, User student) {
        if (!booking.getUser().getId().equals(student.getId())) {
            throw new AccessDeniedException("Bạn không có quyền tạo mã QR cho đăng ký này.");
        }
        validateBookingCanCheckIn(booking);
        checkinWindowPolicy.validate(booking, Instant.now());
    }

    private void validateManagerCanConfirm(Booking booking, User manager) {
        Laboratory managedLab = laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())
                .orElseThrow(() -> new AccessDeniedException("Quản lý PTN chưa được phân công phòng thí nghiệm."));
        if (!booking.getLab().getId().equals(managedLab.getId())) {
            throw new AccessDeniedException("Bạn không có quyền xác nhận cho PTN này.");
        }
        validateBookingCanCheckIn(booking);
        checkinWindowPolicy.validate(booking, Instant.now());
    }

    private void validateBookingCanCheckIn(Booking booking) {
        if (booking.getStatus() == BookingStatus.CHECKED_IN) {
            throw new IllegalStateException("Đã xác nhận có mặt.");
        }
        if (booking.getStatus() != BookingStatus.APPROVED) {
            throw new IllegalStateException("Đăng ký chưa được phê duyệt.");
        }
        if (booking.getTimeSlot() == null || booking.getTimeSlot().getStatus() == TimeSlotStatus.CANCELLED) {
            throw new IllegalStateException("Khung giờ sử dụng đã bị hủy.");
        }
    }

    private String createToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String tokenKey(String token) {
        return TOKEN_PREFIX + token;
    }

    private String qrRequestKey(String requestId) {
        return QR_REQUEST_PREFIX + requestId;
    }

    private String qrRequestBookingKey(Long bookingId) {
        return QR_REQUEST_BOOKING_PREFIX + bookingId;
    }

    private String qrRequestLabKey(Long labId) {
        return QR_REQUEST_LAB_PREFIX + labId;
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private void writeQrRequest(QrRequest request, Duration ttl) {
        String value = String.join("|", request.bookingId().toString(), request.studentId().toString(),
                request.labId().toString(), request.fallbackReason().name(), encode(request.reason()),
                request.status(), request.token() == null ? "" : request.token(), request.expiresAt().toString());
        redisTemplate.opsForValue().set(qrRequestKey(request.requestId()), value, ttl);
    }

    private QrRequest readQrRequest(String requestId) {
        String value = redisTemplate.opsForValue().get(qrRequestKey(requestId));
        if (value == null) return null;
        String[] parts = value.split("\\|", -1);
        if (parts.length != 8) return null;
        try {
            return new QrRequest(requestId, Long.valueOf(parts[0]), Long.valueOf(parts[1]), Long.valueOf(parts[2]),
                    FaceFallbackReason.valueOf(parts[3]), decode(parts[4]), parts[5],
                    parts[6].isBlank() ? null : parts[6], Instant.parse(parts[7]));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private CheckinQrResponse toStudentResponse(QrRequest request, String message) {
        return CheckinQrResponse.builder().requestId(request.requestId()).status(request.status())
                .token(request.token()).expiresAt(request.expiresAt()).message(message).build();
    }

    private CheckinQrRequestResponse toManagerResponse(QrRequest request) {
        Booking booking = bookingRepository.findById(request.bookingId())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy đăng ký sử dụng PTN."));
        return new CheckinQrRequestResponse(request.requestId(), request.bookingId(), request.studentId(),
                booking.getUser().getFullName(), request.fallbackReason(), request.reason(), request.status(), request.expiresAt());
    }

    private Laboratory getManagedLab(User manager) {
        if (!manager.hasRole("LAB_MANAGER")) throw new AccessDeniedException("Chỉ quản lý PTN mới được xử lý yêu cầu QR.");
        return laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())
                .orElseThrow(() -> new AccessDeniedException("Quản lý PTN chưa được phân công phòng thí nghiệm."));
    }

    private QrFallbackToken parseTokenValue(String tokenValue) {
        String[] parts = tokenValue.split("\\|", -1);
        if (parts.length != 3) {
            throw invalidQrToken();
        }
        try {
            String reason = new String(Base64.getUrlDecoder().decode(parts[2]), java.nio.charset.StandardCharsets.UTF_8);
            return new QrFallbackToken(Long.valueOf(parts[0]), FaceFallbackReason.valueOf(parts[1]), reason);
        } catch (IllegalArgumentException exception) {
            throw invalidQrToken();
        }
    }

    private String normalizeCustomReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("Vui lòng cung cấp lý do khác.");
        if (normalized.length() > 1000) throw new IllegalArgumentException("Lý do khác không được vượt quá 1000 ký tự.");
        return normalized;
    }

    private IllegalArgumentException invalidQrToken() {
        return new IllegalArgumentException("Mã QR không hợp lệ hoặc đã hết hạn.");
    }

    private String normalizeManualReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng cung cấp lý do check-in thủ công.");
        }
        if (normalized.length() > 1000) {
            throw new IllegalArgumentException("Lý do check-in thủ công không được vượt quá 1000 ký tự.");
        }
        return normalized;
    }

    private void recordFallback(Booking booking, User actor, FaceCheckinMethod method, String reason) {
        faceCheckinLogRepository.save(FaceCheckinLogEntity.builder()
                .booking(booking)
                .user(booking.getUser())
                .lab(booking.getLab())
                .checkedInBy(actor)
                .checkinMethod(method)
                .result(FaceCheckinResult.SUCCESS)
                .fallbackReason(reason)
                .build());
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Yêu cầu đăng nhập.");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng hiện tại."));
    }

    private record QrFallbackToken(Long bookingId, FaceFallbackReason reason, String reasonText) {
    }

    private record QrRequest(String requestId, Long bookingId, Long studentId, Long labId,
                             FaceFallbackReason fallbackReason, String reason, String status,
                             String token, Instant expiresAt) {
    }
}
