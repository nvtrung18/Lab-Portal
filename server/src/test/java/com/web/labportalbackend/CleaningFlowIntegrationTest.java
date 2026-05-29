package com.web.labportalbackend;

import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.booking.dto.response.CleaningResponse;
import com.web.labportalbackend.booking.entity.CleaningEntity;
import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.booking.repository.CleaningRepository;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.booking.service.impl.CleaningServiceImpl;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.enums.CleaningStatus;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CleaningFlowIntegrationTest {

    @Test
    void cleaningFlow_createAssignCompleteTransitionsStatusCorrectly() {
        CleaningRepository cleaningRepository = mock(CleaningRepository.class);
        TimeSlotRepository timeSlotRepository = mock(TimeSlotRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        LaboratoryRepository laboratoryRepository = mock(LaboratoryRepository.class);
        BookingRepository bookingRepository = mock(BookingRepository.class);
        CleaningServiceImpl cleaningService = new CleaningServiceImpl(
                cleaningRepository,
                timeSlotRepository,
                userRepository,
                laboratoryRepository,
                bookingRepository
        );

        Map<Long, CleaningEntity> cleanings = new HashMap<>();
        TimeSlot slot = new TimeSlot();
        slot.setId(7L);
        Laboratory lab = new Laboratory();
        lab.setId(2L);
        slot.setLab(lab);
        User manager = new User();
        manager.setId(9L);
        manager.setUsername("manager");
        Role managerRole = new Role();
        managerRole.setName("LAB_MANAGER");
        manager.addRole(managerRole);
        User staff = new User();
        staff.setId(3L);
        staff.setUsername("staff");
        Role studentRole = new Role();
        studentRole.setName("STUDENT");
        staff.addRole(studentRole);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("manager", null)
        );
        when(timeSlotRepository.findById(7L)).thenReturn(Optional.of(slot));
        when(userRepository.findById(3L)).thenReturn(Optional.of(staff));
        when(userRepository.findByUsername("manager")).thenReturn(Optional.of(manager));
        when(userRepository.findByUsername("staff")).thenReturn(Optional.of(staff));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(9L)).thenReturn(Optional.of(lab));
        when(bookingRepository.existsBySlotIdAndUserIdAndStatusIn(any(Long.class), any(Long.class), any()))
                .thenReturn(true);
        when(bookingRepository.countActiveByTimeSlotIdAndStatusIn(any(Long.class), any()))
                .thenReturn(1L);
        when(cleaningRepository.findFirstBySlotId(7L)).thenAnswer(invocation ->
                cleanings.values().stream()
                        .filter(cleaning -> cleaning.getSlot().getId().equals(7L))
                        .findFirst()
        );
        when(cleaningRepository.findById(any(Long.class))).thenAnswer(invocation ->
                Optional.ofNullable(cleanings.get(invocation.getArgument(0)))
        );
        when(cleaningRepository.save(any(CleaningEntity.class))).thenAnswer(invocation -> {
            CleaningEntity cleaning = invocation.getArgument(0);
            if (cleaning.getId() == null) {
                cleaning.setId(1L);
            }
            cleanings.put(cleaning.getId(), cleaning);
            return cleaning;
        });

        CleaningResponse created = cleaningService.createCleaningTask(7L);
        assertEquals(CleaningStatus.PENDING, created.getStatus());
        assertNull(created.getStaffId());

        CleaningResponse assigned = cleaningService.assignStaff(created.getId(), 3L);
        assertEquals(CleaningStatus.ASSIGNED, assigned.getStatus());
        assertEquals(3L, assigned.getStaffId());
        assertNotNull(assigned.getStartedAt());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("staff", null)
        );
        CleaningResponse completed = cleaningService.confirmCompleted(created.getId());
        assertEquals(CleaningStatus.DONE, completed.getStatus());
        assertNotNull(completed.getCompletedAt());
        SecurityContextHolder.clearContext();
    }
}
