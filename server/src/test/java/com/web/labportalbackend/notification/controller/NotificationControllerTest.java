package com.web.labportalbackend.notification.controller;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.web.labportalbackend.auth.security.JwtAuthenticationFilter;
import com.web.labportalbackend.notification.dto.NotificationPageResponse;
import com.web.labportalbackend.notification.dto.NotificationResponse;
import com.web.labportalbackend.notification.enums.NotificationEventType;
import com.web.labportalbackend.notification.enums.NotificationTargetModule;
import com.web.labportalbackend.notification.service.NotificationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = NotificationController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@Import(NotificationControllerTest.MethodSecurityTestConfig.class)
class NotificationControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean NotificationService notificationService;

    @Test
    void authenticatedUserCanListNotificationsWithBoundedPaging() throws Exception {
        NotificationResponse item = new NotificationResponse(
                11L, NotificationEventType.TASK_ASSIGNED, "Task assigned", "A task was assigned to you",
                NotificationTargetModule.TASK, 42L, null, false,
                Instant.parse("2026-08-31T00:00:00Z"));
        when(notificationService.getCurrentUserNotifications(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new NotificationPageResponse(List.of(item), 0, 100, 1, 1, 1));

        mockMvc.perform(get("/api/notifications").contextPath("/api")
                        .param("page", "-3").param("size", "500")
                        .with(user("student").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(11))
                .andExpect(jsonPath("$.data.unreadCount").value(1));

        verify(notificationService).getCurrentUserNotifications(
                argThat(pageable -> pageable.getPageNumber() == 0 && pageable.getPageSize() == 100));
    }

    @Test
    void authenticatedUserCanMarkOwnedNotificationRead() throws Exception {
        when(notificationService.markCurrentUserNotificationRead(11L)).thenReturn(new NotificationResponse(
                11L, NotificationEventType.TASK_ASSIGNED, "Task assigned", "A task was assigned to you",
                NotificationTargetModule.TASK, 42L, null, true,
                Instant.parse("2026-08-31T00:00:00Z")));

        mockMvc.perform(patch("/api/notifications/11/read").contextPath("/api")
                        .with(csrf()).with(user("student").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true));

        verify(notificationService).markCurrentUserNotificationRead(11L);
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/notifications").contextPath("/api"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(notificationService);
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }
}
