package com.web.labportalbackend;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.booking.dto.response.CleaningResponse;
import com.web.labportalbackend.booking.entity.CleaningEntity;
import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.booking.repository.CleaningRepository;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.booking.service.impl.CleaningServiceImpl;
import com.web.labportalbackend.common.enums.CleaningStatus;
import org.junit.jupiter.api.Test;

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
        CleaningServiceImpl cleaningService = new CleaningServiceImpl(
                cleaningRepository,
                timeSlotRepository,
                userRepository
        );

        Map<Long, CleaningEntity> cleanings = new HashMap<>();
        TimeSlot slot = new TimeSlot();
        slot.setId(7L);
        User staff = new User();
        staff.setId(3L);

        when(timeSlotRepository.findById(7L)).thenReturn(Optional.of(slot));
        when(userRepository.findById(3L)).thenReturn(Optional.of(staff));
        when(cleaningRepository.findBySlotId(7L)).thenAnswer(invocation ->
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

        CleaningResponse completed = cleaningService.confirmCompleted(created.getId());
        assertEquals(CleaningStatus.COMPLETED, completed.getStatus());
        assertNotNull(completed.getCompletedAt());
    }
}
