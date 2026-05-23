package com.web.labportalbackend.booking.dto.response;

import com.web.labportalbackend.common.enums.BookingStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EligibleCleanerResponse {
    private Long userId;
    private String fullName;
    private String email;
    private Long bookingId;
    private BookingStatus bookingStatus;
    private Boolean checkedIn;
}
