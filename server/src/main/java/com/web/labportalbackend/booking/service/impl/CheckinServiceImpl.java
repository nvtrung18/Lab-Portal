package com.web.labportalbackend.booking.service.impl;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.admin.systemconfig.dto.SystemConfigResponse;
import com.web.labportalbackend.admin.systemconfig.service.SystemConfigService;
import com.web.labportalbackend.booking.dto.response.BookingResponse;
import com.web.labportalbackend.booking.dto.response.CheckinQrResponse;
import com.web.labportalbackend.booking.entity.Booking;
import com.web.labportalbackend.booking.mapper.BookingMapper;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.booking.service.CheckinService;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.enums.TimeSlotStatus;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
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

@Service
@RequiredArgsConstructor
public class CheckinServiceImpl implements CheckinService {

    private static final long CHECKIN_AFTER_START_MINUTES = 10;
    private static final Duration QR_TOKEN_TTL = Duration.ofMinutes(2);
    private static final String TOKEN_PREFIX = "checkin:token:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final StringRedisTemplate redisTemplate;
    private final SystemConfigService systemConfigService;

    @Override
    @Transactional(readOnly = true)
    public CheckinQrResponse createQrForCurrentStudent(Long bookingId) {
        User currentUser = getCurrentUser();
        if (!currentUser.hasRole("STUDENT")) {
            throw new AccessDeniedException("Chỉ sinh viên mới được tạo mã QR check-in.");
        }

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy đăng ký sử dụng PTN."));
        validateStudentCanCreateQr(booking, currentUser);

        Instant now = Instant.now();
        Instant checkinEnd = checkinEnd(booking);
        Duration remainingWindow = Duration.between(now, checkinEnd);
        Duration ttl = remainingWindow.compareTo(QR_TOKEN_TTL) < 0 ? remainingWindow : QR_TOKEN_TTL;
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalStateException("Đã quá thời gian check-in.");
        }

        String token = createToken();
        redisTemplate.opsForValue().set(tokenKey(token), booking.getId().toString(), ttl);
        return CheckinQrResponse.builder()
                .token(token)
                .expiresAt(now.plus(ttl))
                .message("Mã QR check-in đã được tạo.")
                .build();
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
        String bookingIdValue = redisTemplate.opsForValue().get(redisKey);
        if (bookingIdValue == null) {
            throw new IllegalArgumentException("Mã QR không hợp lệ hoặc đã hết hạn.");
        }

        Booking booking = bookingRepository.findById(Long.valueOf(bookingIdValue))
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy đăng ký sử dụng PTN."));
        validateManagerCanConfirm(booking, currentUser);
        if (!Boolean.TRUE.equals(redisTemplate.delete(redisKey))) {
            throw new IllegalArgumentException("Mã QR không hợp lệ hoặc đã hết hạn.");
        }

        booking.setStatus(BookingStatus.CHECKED_IN);
        Booking saved = bookingRepository.save(booking);
        return BookingMapper.toResponse(saved);
    }

    private void validateStudentCanCreateQr(Booking booking, User student) {
        if (!booking.getUser().getId().equals(student.getId())) {
            throw new AccessDeniedException("Bạn không có quyền tạo mã QR cho đăng ký này.");
        }
        validateBookingCanCheckIn(booking);
        validateCheckinWindow(booking);
    }

    private void validateManagerCanConfirm(Booking booking, User manager) {
        Laboratory managedLab = laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())
                .orElseThrow(() -> new AccessDeniedException("Quản lý PTN chưa được phân công phòng thí nghiệm."));
        if (!booking.getLab().getId().equals(managedLab.getId())) {
            throw new AccessDeniedException("Bạn không có quyền xác nhận cho PTN này.");
        }
        validateBookingCanCheckIn(booking);
        validateCheckinWindow(booking);
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

    private void validateCheckinWindow(Booking booking) {
        Instant now = Instant.now();
        if (now.isBefore(booking.getStartTime())) {
            throw new IllegalStateException("Chưa đến giờ check-in.");
        }
        if (now.isAfter(checkinEnd(booking))) {
            throw new IllegalStateException("Đã quá thời gian check-in.");
        }
    }

    private Instant checkinEnd(Booking booking) {
        return booking.getStartTime().plus(Duration.ofMinutes(systemConfig().booking().checkinWindowMinutes()));
    }

    private String createToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String tokenKey(String token) {
        return TOKEN_PREFIX + token;
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Yêu cầu đăng nhập.");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng hiện tại."));
    }

    private SystemConfigResponse systemConfig() {
        return systemConfigService.getConfig();
    }
}
