package com.web.labportalbackend.booking.service;

import com.web.labportalbackend.booking.dto.response.CleaningResponse;
import com.web.labportalbackend.booking.dto.response.EligibleCleanerResponse;

import java.util.List;

public interface CleaningService {
    CleaningResponse createCleaningTask(Long slotId);

    CleaningResponse assignStaff(Long cleaningId, Long staffId);

    CleaningResponse confirmCompleted(Long cleaningId);

    List<CleaningResponse> getPendingCleanings();

    List<CleaningResponse> getLabCleaningTasks(Long labId);

    List<CleaningResponse> assignCleaningTasks(Long slotId, List<Long> assigneeIds);

    List<EligibleCleanerResponse> getEligibleCleaners(Long slotId);

    List<CleaningResponse> getMyCleaningTasks();

    CleaningResponse completeCleaningTask(Long cleaningId);

    CleaningResponse cancelCleaningTask(Long cleaningId);
}
