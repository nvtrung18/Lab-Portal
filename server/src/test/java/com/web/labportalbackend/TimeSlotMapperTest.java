package com.web.labportalbackend;

import com.web.labportalbackend.booking.dto.response.TimeSlotResponse;
import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.booking.mapper.TimeSlotMapper;
import com.web.labportalbackend.common.enums.TimeSlotStatus;
import com.web.labportalbackend.lab.entity.Laboratory;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TimeSlotMapperTest {

    @Test
    void mapsApprovedCheckedInPendingAndRemainingCapacity() {
        Laboratory lab = new Laboratory();
        lab.setId(1L);

        TimeSlot slot = TimeSlot.builder()
                .lab(lab)
                .startTime(Instant.parse("2026-05-22T13:30:00Z"))
                .endTime(Instant.parse("2026-05-22T16:30:00Z"))
                .capacity(30)
                .status(TimeSlotStatus.AVAILABLE)
                .build();
        slot.setId(10L);

        TimeSlotResponse response = TimeSlotMapper.toResponse(slot, 2L, 1L, 1L);

        assertThat(response.getBookedCount()).isEqualTo(2L);
        assertThat(response.getApprovedCount()).isEqualTo(2L);
        assertThat(response.getCheckedInCount()).isEqualTo(1L);
        assertThat(response.getPendingCount()).isEqualTo(1L);
        assertThat(response.getRemainingCapacity()).isEqualTo(28L);
    }
}
