package com.web.labportalbackend.admin.operations.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.web.labportalbackend.admin.operations.dto.OperationalLogPageResponse;
import com.web.labportalbackend.admin.operations.service.OperationalLogService;
import com.web.labportalbackend.auth.security.JwtAuthenticationFilter;
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
        controllers = OperationalLogController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
)
@Import(OperationalLogControllerTest.MethodSecurityTestConfig.class)
class OperationalLogControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean OperationalLogService operationalLogService;

    @Test
    void adminCanFilterAiUsageAndPagingIsBounded() throws Exception {
        when(operationalLogService.getAiUsage(any(), any(), any(), any(), any(), any()))
                .thenReturn(new OperationalLogPageResponse<>(List.of(), 0, 100, 0, 0));

        mockMvc.perform(get("/api/admin/operational-logs/ai-usage").contextPath("/api")
                        .param("userId", "7").param("module", "research")
                        .param("page", "-1").param("size", "1000")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100));

        verify(operationalLogService).getAiUsage(
                org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq("research"),
                isNull(), isNull(), isNull(),
                argThat(pageable -> pageable.getPageNumber() == 0 && pageable.getPageSize() == 100));
    }

    @Test
    void laboratoryManagerCanRequestFaceLogsForServiceLevelScopeEnforcement() throws Exception {
        when(operationalLogService.getFaceCheckins(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new OperationalLogPageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/admin/operational-logs/face-checkins").contextPath("/api")
                        .param("labId", "3")
                        .with(user("manager").roles("LAB_MANAGER")))
                .andExpect(status().isOk());

        verify(operationalLogService).getFaceCheckins(
                isNull(), org.mockito.ArgumentMatchers.eq(3L), isNull(), isNull(), isNull(), isNull(), any());
    }

    @Test
    void laboratoryManagerCannotAccessAiOperationalLogs() throws Exception {
        mockMvc.perform(get("/api/admin/operational-logs/ai-actions").contextPath("/api")
                        .with(user("manager").roles("LAB_MANAGER")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(operationalLogService);
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }
}
