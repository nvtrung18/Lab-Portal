package com.web.labportalbackend.booking.service;

public interface BookingTaskService {
    int processNoShows();

    int expirePastCheckinSlots();

    int createCleaningTasksForEndedSlots();

    int completeEndedSessions();
}
