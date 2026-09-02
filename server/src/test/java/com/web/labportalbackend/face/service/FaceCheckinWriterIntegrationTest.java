package com.web.labportalbackend.face.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.web.labportalbackend.admin.systemconfig.dto.SystemConfigResponse;
import com.web.labportalbackend.admin.systemconfig.service.SystemConfigService;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.booking.entity.Booking;
import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.booking.service.CheckinWindowPolicy;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.enums.LabStatus;
import com.web.labportalbackend.common.enums.TimeSlotStatus;
import com.web.labportalbackend.common.enums.UserStatus;
import com.web.labportalbackend.face.enums.FaceCheckinMethod;
import com.web.labportalbackend.face.enums.FaceCheckinResult;
import com.web.labportalbackend.face.repository.FaceCheckinLogRepository;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.web.labportalbackend.notification.service.NotificationEmitter;

@DataJpaTest
@Import({FaceCheckinWriter.class, CheckinWindowPolicy.class})
class FaceCheckinWriterIntegrationTest {

    @Autowired FaceCheckinWriter writer;
    @Autowired UserRepository userRepository;
    @Autowired LaboratoryRepository laboratoryRepository;
    @Autowired TimeSlotRepository timeSlotRepository;
    @Autowired BookingRepository bookingRepository;
    @Autowired FaceCheckinLogRepository checkinLogRepository;
    @Autowired EntityManager entityManager;
    @MockitoBean SystemConfigService systemConfigService;
    @MockitoBean NotificationEmitter notificationEmitter;

    @Test
    void successfulFaceMatchAtomicallyUpdatesBookingAndWritesLog() {
        User student = userRepository.save(new User("student@example.test", "student-face", "password",
                "Student", null, UserStatus.ACTIVE, new HashSet<>()));
        User manager = userRepository.save(new User("manager@example.test", "manager-face", "password",
                "Manager", null, UserStatus.ACTIVE, new HashSet<>()));
        Laboratory lab = laboratoryRepository.save(new Laboratory("Face Lab", null, "A-101", 20,
                "AI", LabStatus.AVAILABLE, manager));
        Instant start = Instant.now().minusSeconds(60);
        TimeSlot slot = timeSlotRepository.save(TimeSlot.builder().lab(lab).startTime(start)
                .endTime(start.plusSeconds(3600)).capacity(10).status(TimeSlotStatus.AVAILABLE).build());
        Booking booking = new Booking(student, lab, slot, start, start.plusSeconds(3600),
                BookingStatus.APPROVED, "Face check-in", 1);
        booking = bookingRepository.saveAndFlush(booking);
        when(systemConfigService.getConfig()).thenReturn(new SystemConfigResponse(null, null,
                new SystemConfigResponse.BookingConfig(10, 0, false, false), null, null));

        assertEquals(1, bookingRepository.findFaceCheckinCandidatesForManager(
                manager.getId(), start.minusSeconds(60), start.plusSeconds(60)).size());
        assertEquals(booking.getId(), bookingRepository
                .findManagerFaceCheckinBooking(manager.getId(), booking.getId()).orElseThrow().getId());

        writer.complete(student.getId(), manager.getId(), booking.getId(), 0.91, 0.88);
        entityManager.flush();
        entityManager.clear();

        assertEquals(BookingStatus.CHECKED_IN, bookingRepository.findById(booking.getId()).orElseThrow().getStatus());
        var logs = checkinLogRepository.findByUserIdOrderByCreatedAtDescIdDesc(student.getId());
        assertEquals(1, logs.size());
        assertEquals(FaceCheckinMethod.FACE, logs.getFirst().getCheckinMethod());
        assertEquals(FaceCheckinResult.SUCCESS, logs.getFirst().getResult());
        assertEquals(manager.getId(), logs.getFirst().getCheckedInBy().getId());
    }
}
