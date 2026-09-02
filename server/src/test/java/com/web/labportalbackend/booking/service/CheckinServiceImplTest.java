package com.web.labportalbackend.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.web.labportalbackend.admin.audit.enums.AuditAction;
import com.web.labportalbackend.admin.audit.enums.AuditModule;
import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.admin.systemconfig.dto.SystemConfigResponse;
import com.web.labportalbackend.admin.systemconfig.service.SystemConfigService;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.booking.entity.Booking;
import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.booking.service.impl.CheckinServiceImpl;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.enums.TimeSlotStatus;
import com.web.labportalbackend.face.entity.FaceCheckinLogEntity;
import com.web.labportalbackend.face.enums.FaceCheckinMethod;
import com.web.labportalbackend.face.enums.FaceFallbackReason;
import com.web.labportalbackend.face.repository.FaceCheckinLogRepository;
import com.web.labportalbackend.face.service.FaceFallbackPolicy;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.notification.service.NotificationEmitter;
import com.web.labportalbackend.notification.enums.NotificationEventType;
import com.web.labportalbackend.notification.enums.NotificationTargetModule;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CheckinServiceImplTest {

    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final LaboratoryRepository laboratoryRepository = mock(LaboratoryRepository.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    @SuppressWarnings("unchecked")
    private final SetOperations<String, String> setOperations = mock(SetOperations.class);
    private final SystemConfigService systemConfigService = mock(SystemConfigService.class);
    private final FaceFallbackPolicy fallbackPolicy = mock(FaceFallbackPolicy.class);
    private final FaceCheckinLogRepository logRepository = mock(FaceCheckinLogRepository.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final NotificationEmitter notificationEmitter = mock(NotificationEmitter.class);
    private final User actor = mock(User.class);
    private final User student = mock(User.class);
    private final Laboratory lab = mock(Laboratory.class);
    private final TimeSlot slot = mock(TimeSlot.class);
    private final Booking booking = mock(Booking.class);
    private CheckinServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CheckinServiceImpl(bookingRepository, userRepository, laboratoryRepository,
                redisTemplate, new CheckinWindowPolicy(systemConfigService), fallbackPolicy,
                logRepository, auditLogService, notificationEmitter);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(userRepository.findByUsername("actor")).thenReturn(Optional.of(actor));
        when(actor.getId()).thenReturn(3L);
        when(student.getId()).thenReturn(7L);
        when(lab.getId()).thenReturn(5L);
        when(booking.getId()).thenReturn(11L);
        when(booking.getUser()).thenReturn(student);
        when(booking.getLab()).thenReturn(lab);
        when(booking.getTimeSlot()).thenReturn(slot);
        when(booking.getStatus()).thenReturn(BookingStatus.APPROVED);
        when(booking.getStartTime()).thenReturn(Instant.now().minusSeconds(60));
        when(booking.getEndTime()).thenReturn(Instant.now().plus(Duration.ofHours(2)));
        when(slot.getStatus()).thenReturn(TimeSlotStatus.AVAILABLE);
        when(bookingRepository.findById(11L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);
        when(systemConfigService.getConfig()).thenReturn(new SystemConfigResponse(null, null,
                new SystemConfigResponse.BookingConfig(10, 0, false, false), null, null));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("actor", "n/a", List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void qrRequestDoesNotIssueTokenBeforeManagerApproval() {
        when(actor.hasRole("STUDENT")).thenReturn(true);
        when(booking.getUser()).thenReturn(actor);
        when(lab.getManager()).thenReturn(student);
        Instant sessionEndsAt = Instant.now().plus(Duration.ofHours(2));
        when(booking.getEndTime()).thenReturn(sessionEndsAt);

        var response = service.requestQrForCurrentStudent(11L, FaceFallbackReason.FACE_PROFILE_UNAVAILABLE, null);

        verify(fallbackPolicy).assertQrAllowed(booking, FaceFallbackReason.FACE_PROFILE_UNAVAILABLE);
        assertEquals("PENDING", response.getStatus());
        assertEquals(null, response.getToken());
        assertEquals(sessionEndsAt, response.getExpiresAt());
        verify(valueOperations, never()).set(org.mockito.ArgumentMatchers.startsWith("checkin:token:"), anyString(), any(Duration.class));
    }

    @Test
    void approvedQrTokenRemainsAvailableUntilTheLabSessionEnds() {
        when(actor.hasRole("LAB_MANAGER")).thenReturn(true);
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(3L)).thenReturn(Optional.of(lab));
        Instant sessionEndsAt = Instant.now().plus(Duration.ofHours(2));
        String encodedReason = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("Camera unavailable".getBytes(StandardCharsets.UTF_8));
        when(valueOperations.get("checkin:qr-request:req-until-end"))
                .thenReturn("11|7|5|FACE_SERVICE_UNAVAILABLE|" + encodedReason
                        + "|PENDING||" + sessionEndsAt);

        var response = service.reviewQrRequestByCurrentManager("req-until-end", true);

        assertEquals(sessionEndsAt, response.expiresAt());
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(org.mockito.ArgumentMatchers.startsWith("checkin:token:"),
                org.mockito.ArgumentMatchers.contains("11|FACE_SERVICE_UNAVAILABLE|"), ttlCaptor.capture());
        org.junit.jupiter.api.Assertions.assertTrue(ttlCaptor.getValue().compareTo(Duration.ofMinutes(100)) > 0);
    }

    @Test
    void qrConfirmationRechecksPolicyAndWritesAuditTrail() {
        when(actor.hasRole("LAB_MANAGER")).thenReturn(true);
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(3L)).thenReturn(Optional.of(lab));
        String encodedReason = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("FACE_SERVICE_UNAVAILABLE".getBytes(StandardCharsets.UTF_8));
        when(valueOperations.get("checkin:token:qr-token"))
                .thenReturn("11|FACE_SERVICE_UNAVAILABLE|" + encodedReason);
        Instant sessionEndsAt = Instant.now().plus(Duration.ofHours(2));
        when(valueOperations.get("checkin:qr-request:booking:11")).thenReturn("req-used");
        when(valueOperations.get("checkin:qr-request:req-used"))
                .thenReturn("11|7|5|FACE_SERVICE_UNAVAILABLE|" + encodedReason
                        + "|APPROVED|qr-token|" + sessionEndsAt);
        when(redisTemplate.delete("checkin:token:qr-token")).thenReturn(true);

        service.confirmByCurrentManager("qr-token");

        verify(fallbackPolicy).assertQrAllowed(booking, FaceFallbackReason.FACE_SERVICE_UNAVAILABLE);
        verify(booking).setStatus(BookingStatus.CHECKED_IN);
        verify(bookingRepository).save(booking);
        ArgumentCaptor<FaceCheckinLogEntity> logCaptor = ArgumentCaptor.forClass(FaceCheckinLogEntity.class);
        verify(logRepository).save(logCaptor.capture());
        assertEquals(FaceCheckinMethod.QR_FALLBACK, logCaptor.getValue().getCheckinMethod());
        assertEquals("FACE_SERVICE_UNAVAILABLE", logCaptor.getValue().getFallbackReason());
        verify(auditLogService).log(actor, AuditAction.QR_FALLBACK_CHECKIN, AuditModule.FACE,
                "BOOKING", 11L, "QR fallback check-in: FACE_SERVICE_UNAVAILABLE");
        verify(valueOperations).set(eq("checkin:qr-request:req-used"),
                org.mockito.ArgumentMatchers.contains("|USED||"), any(Duration.class));
    }

    @Test
    void approvedQrCanBeUsedAfterTheNormalCheckinWindowUntilSessionEnd() {
        when(actor.hasRole("LAB_MANAGER")).thenReturn(true);
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(3L)).thenReturn(Optional.of(lab));
        when(booking.getStartTime()).thenReturn(Instant.now().minus(Duration.ofMinutes(30)));
        when(booking.getEndTime()).thenReturn(Instant.now().plus(Duration.ofHours(1)));
        String encodedReason = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("FACE_SERVICE_UNAVAILABLE".getBytes(StandardCharsets.UTF_8));
        when(valueOperations.get("checkin:token:session-token"))
                .thenReturn("11|FACE_SERVICE_UNAVAILABLE|" + encodedReason);
        when(redisTemplate.delete("checkin:token:session-token")).thenReturn(true);

        service.confirmByCurrentManager("session-token");

        verify(booking).setStatus(BookingStatus.CHECKED_IN);
        verify(bookingRepository).save(booking);
    }

    @Test
    void qrReviewNotifiesTheRequestingStudent() {
        when(actor.hasRole("LAB_MANAGER")).thenReturn(true);
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(3L)).thenReturn(Optional.of(lab));
        Instant expiresAt = Instant.now().plusSeconds(300);
        String encodedReason = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("Camera unavailable".getBytes(StandardCharsets.UTF_8));
        when(valueOperations.get("checkin:qr-request:req-1"))
                .thenReturn("11|7|5|FACE_SERVICE_UNAVAILABLE|" + encodedReason
                        + "|PENDING||" + expiresAt);

        service.reviewQrRequestByCurrentManager("req-1", false);

        verify(notificationEmitter).emit(eq(7L), eq(NotificationEventType.QR_CHECKIN_REVIEWED),
                anyString(), anyString(), eq(NotificationTargetModule.FACE), eq(11L), eq(null));
    }

    @Test
    void qrHistoryReturnsOnlyTheCurrentStudentsUnexpiredRequests() {
        Instant sessionEndsAt = Instant.now().plus(Duration.ofHours(2));
        when(bookingRepository.findCurrentQrHistoryBookingsForUser(eq(3L), any(Instant.class)))
                .thenReturn(List.of(booking));
        when(valueOperations.get("checkin:qr-request:booking:11")).thenReturn("req-history");
        String encodedReason = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("Camera unavailable".getBytes(StandardCharsets.UTF_8));
        when(valueOperations.get("checkin:qr-request:req-history"))
                .thenReturn("11|3|5|FACE_SERVICE_UNAVAILABLE|" + encodedReason
                        + "|APPROVED|qr-token|" + sessionEndsAt);

        var history = service.getCurrentStudentQrHistory();

        assertEquals(1, history.size());
        assertEquals(11L, history.getFirst().bookingId());
        assertEquals("qr-token", history.getFirst().token());
        assertEquals(sessionEndsAt, history.getFirst().expiresAt());
    }

    @Test
    void manualCheckinRequiresManagerLabScope() {
        when(actor.hasRole("LAB_MANAGER")).thenReturn(true);
        Laboratory anotherLab = mock(Laboratory.class);
        when(lab.getId()).thenReturn(5L);
        when(anotherLab.getId()).thenReturn(6L);
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(3L)).thenReturn(Optional.of(anotherLab));

        assertThrows(AccessDeniedException.class,
                () -> service.manualCheckinByCurrentManager(11L, "Camera outage"));

        verify(bookingRepository, never()).save(any());
        verify(logRepository, never()).save(any());
    }

    @Test
    void manualCheckinRecordsNormalizedReasonAndAudit() {
        when(actor.hasRole("LAB_MANAGER")).thenReturn(true);
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(3L)).thenReturn(Optional.of(lab));

        service.manualCheckinByCurrentManager(11L, "  Camera maintenance  ");

        verify(fallbackPolicy).assertManualAllowed();
        verify(booking).setStatus(BookingStatus.CHECKED_IN);
        verify(bookingRepository).save(booking);
        ArgumentCaptor<FaceCheckinLogEntity> logCaptor = ArgumentCaptor.forClass(FaceCheckinLogEntity.class);
        verify(logRepository).save(logCaptor.capture());
        assertEquals(FaceCheckinMethod.MANUAL, logCaptor.getValue().getCheckinMethod());
        assertEquals("Camera maintenance", logCaptor.getValue().getFallbackReason());
        verify(auditLogService).log(actor, AuditAction.MANUAL_CHECKIN, AuditModule.FACE,
                "BOOKING", 11L, "Manual check-in override: Camera maintenance");
    }

    @Test
    void blankManualReasonIsRejectedBeforePersistence() {
        when(actor.hasRole("LAB_MANAGER")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> service.manualCheckinByCurrentManager(11L, "   "));

        verify(bookingRepository, never()).save(any());
        verify(logRepository, never()).save(any());
    }
}
