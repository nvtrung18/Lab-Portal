package com.web.labportalbackend.booking.service;

import com.web.labportalbackend.booking.dto.response.CleaningResponse;

import java.util.List;

public interface CleaningService {
    CleaningResponse createCleaningTask(Long slotId);

    CleaningResponse assignStaff(Long cleaningId, Long staffId);

    CleaningResponse confirmCompleted(Long cleaningId);

    List<CleaningResponse> getPendingCleanings();
}
