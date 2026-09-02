package com.web.labportalbackend.notification.service;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.notification.dto.RealtimeEventResponse;
import com.web.labportalbackend.notification.event.NotificationCreatedEvent;
import com.web.labportalbackend.notification.enums.RealtimeEventType;
import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
public class RealtimeEventService {

    private static final long CONNECTION_TIMEOUT_MS = 30 * 60 * 1000L;

    private final UserRepository userRepository;
    private final ConcurrentHashMap<Long, CopyOnWriteArraySet<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribeCurrentUser() {
        User currentUser = getCurrentUser();
        SseEmitter emitter = new SseEmitter(CONNECTION_TIMEOUT_MS);
        emitters.computeIfAbsent(currentUser.getId(), ignored -> new CopyOnWriteArraySet<>()).add(emitter);
        emitter.onCompletion(() -> remove(currentUser.getId(), emitter));
        emitter.onTimeout(() -> remove(currentUser.getId(), emitter));
        emitter.onError(ignored -> remove(currentUser.getId(), emitter));

        send(currentUser.getId(), emitter, new RealtimeEventResponse(
                UUID.randomUUID().toString(), RealtimeEventType.CONNECTED,
                null, null, null, null, null, Instant.now()));
        return emitter;
    }

    public void publish(NotificationCreatedEvent event) {
        RealtimeEventResponse response = new RealtimeEventResponse(
                UUID.randomUUID().toString(), RealtimeEventType.NOTIFICATION_CREATED,
                event.eventType(), event.title(), event.message(),
                event.targetModule(), event.targetId(), event.occurredAt());
        for (SseEmitter emitter : emitters.getOrDefault(event.recipientId(), new CopyOnWriteArraySet<>())) {
            send(event.recipientId(), emitter, response);
        }
    }

    @Scheduled(fixedRateString = "${realtime.sse.heartbeat-ms:25000}")
    public void sendHeartbeats() {
        emitters.forEach((userId, userEmitters) -> userEmitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException | IllegalStateException ex) {
                remove(userId, emitter);
            }
        }));
    }

    private void send(Long userId, SseEmitter emitter, RealtimeEventResponse event) {
        try {
            emitter.send(SseEmitter.event()
                    .id(event.eventId())
                    .name("realtime")
                    .data(event));
        } catch (IOException | IllegalStateException ex) {
            remove(userId, emitter);
        }
    }

    private void remove(Long userId, SseEmitter emitter) {
        Set<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null) {
            return;
        }
        userEmitters.remove(emitter);
        if (userEmitters.isEmpty()) {
            emitters.remove(userId, userEmitters);
        }
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
