package com.web.labportalbackend.notification.service;

import com.web.labportalbackend.notification.dto.NotificationPageResponse;
import com.web.labportalbackend.notification.dto.NotificationResponse;
import org.springframework.data.domain.Pageable;

public interface NotificationService {

    NotificationPageResponse getCurrentUserNotifications(Pageable pageable);

    NotificationResponse markCurrentUserNotificationRead(Long notificationId);

    int markAllCurrentUserNotificationsRead();
}
