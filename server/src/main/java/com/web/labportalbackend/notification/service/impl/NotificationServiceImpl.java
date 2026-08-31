package com.web.labportalbackend.notification.service.impl;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.notification.dto.NotificationPageResponse;
import com.web.labportalbackend.notification.dto.NotificationResponse;
import com.web.labportalbackend.notification.entity.NotificationEntity;
import com.web.labportalbackend.notification.repository.NotificationRepository;
import com.web.labportalbackend.notification.service.NotificationService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public NotificationPageResponse getCurrentUserNotifications(Pageable pageable) {
        User recipient = getCurrentUser();
        Page<NotificationEntity> notifications = notificationRepository
                .findByRecipientIdOrderByCreatedAtDescIdDesc(recipient.getId(), pageable);
        return new NotificationPageResponse(
                notifications.getContent().stream().map(this::toResponse).toList(),
                notifications.getNumber(),
                notifications.getSize(),
                notifications.getTotalElements(),
                notifications.getTotalPages(),
                notificationRepository.countByRecipientIdAndReadFalse(recipient.getId())
        );
    }

    @Override
    @Transactional
    public NotificationResponse markCurrentUserNotificationRead(Long notificationId) {
        User recipient = getCurrentUser();
        NotificationEntity notification = notificationRepository.findByIdAndRecipientId(notificationId, recipient.getId())
                .orElseThrow(() -> new EntityNotFoundException("Notification not found"));
        notification.markRead();
        return toResponse(notification);
    }

    @Override
    @Transactional
    public int markAllCurrentUserNotificationsRead() {
        return notificationRepository.markAllReadByRecipientId(getCurrentUser().getId());
    }

    private NotificationResponse toResponse(NotificationEntity notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getEventType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getTargetModule(),
                notification.getTargetId(),
                notification.getAssistantKey(),
                Boolean.TRUE.equals(notification.getRead()),
                notification.getCreatedAt()
        );
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new AccessDeniedException("Authentication is required");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("Authenticated user was not found"));
    }
}
