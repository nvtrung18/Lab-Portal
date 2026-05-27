package com.web.labportalbackend.booking.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AssignCleaningRequest {

    private Long slotId;

    private List<Long> assigneeIds;

    private Long cleaningId;

    private Long staffId;
}
