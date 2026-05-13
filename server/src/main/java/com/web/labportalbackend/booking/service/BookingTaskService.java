package com.web.labportalbackend.booking.service;

public interface BookingTaskService {
    int processNoShows();

    int createCleaningTasksForEndedSlots();
}
