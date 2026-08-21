package com.web.labportalbackend.ai.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.labportalbackend.admin.audit.entity.AuditLog;
import com.web.labportalbackend.admin.audit.enums.AuditAction;
import com.web.labportalbackend.admin.audit.enums.AuditModule;
import com.web.labportalbackend.admin.audit.repository.AuditLogRepository;
import com.web.labportalbackend.admin.audit.service.impl.AuditLogServiceImpl;
import com.web.labportalbackend.ai.entity.AiUsageLogEntity;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiAssistantSystemRole;
import com.web.labportalbackend.ai.enums.AiCapability;
import com.web.labportalbackend.ai.enums.AiResourceType;
import com.web.labportalbackend.ai.enums.AiToolId;
import com.web.labportalbackend.ai.repository.AiUsageLogRepository;
import com.web.labportalbackend.ai.service.AiAssistantAuditEvent;
import com.web.labportalbackend.ai.service.AiAuditExecutionResult;
import com.web.labportalbackend.ai.service.AiAuditFailureCode;
import com.web.labportalbackend.ai.service.AiAuditGateStatus;
import com.web.labportalbackend.ai.service.AiAuditUsageService;
import com.web.labportalbackend.ai.service.AiToolAuditEvent;
import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.enums.UserStatus;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@DataJpaTest
@Import({AiAuditUsageServiceImpl.class, AuditLogServiceImpl.class,
        AiAuditUsageServiceImplTest.JacksonTestConfiguration.class})
class AiAuditUsageServiceImplTest {

    private static final String USERNAME = "p9-t6-student";

    @Autowired EntityManager entityManager;
    @Autowired UserRepository userRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired AiUsageLogRepository usageLogRepository;
    @Autowired AiAuditUsageService auditUsageService;
    @Autowired ObjectMapper objectMapper;

    private User actor;

    @BeforeEach
    void authenticateActor() {
        Role role = new Role("STUDENT", "Student");
        entityManager.persist(role);
        User user = new User();
        user.setEmail("p9-t6-student@example.test");
        user.setUsername(USERNAME);
        user.setPassword("jwt-raw-prompt-must-never-be-audited");
        user.setFullName("P9 T6 Student");
        user.setStatus(UserStatus.ACTIVE);
        user.addRole(role);
        actor = userRepository.saveAndFlush(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USERNAME, "Authorization: Bearer secret",
                        List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void completedAssistantOperationPersistsRequiredAuditFieldsAndExactlyOneUsageRow() throws Exception {
        auditUsageService.recordAssistantRequest(new AiAssistantAuditEvent(
                actor.getId(), AiAssistantKey.LAB_ASSISTANT, AiCapability.LAB_SLOT_READ,
                AiResourceType.TIME_SLOT, 17L, "lab-model-v1", null, "prompt-v3",
                "request-p9-t6", AiAuditGateStatus.NOT_REQUIRED,
                AiAuditExecutionResult.SUCCEEDED, null, AiAssistantSystemRole.STUDENT,
                10L, null, null, 12, 7, true));
        entityManager.flush();
        entityManager.clear();

        AuditLog audit = auditLogRepository.findAll().getFirst();
        assertEquals(actor.getId(), audit.getActorId());
        assertEquals(AuditAction.AI_ASSISTANT_REQUEST, audit.getAction());
        assertEquals(AuditModule.AI, audit.getModule());
        assertEquals("TIME_SLOT", audit.getTargetType());
        assertEquals(17L, audit.getTargetId());

        JsonNode metadata = objectMapper.readTree(audit.getMetadataJson());
        assertEquals("LAB_ASSISTANT", metadata.path("assistant").asText());
        assertTrue(metadata.path("toolId").isNull());
        assertEquals("LAB_SLOT_READ", metadata.path("action").asText());
        assertEquals("TIME_SLOT", metadata.path("resourceType").asText());
        assertEquals(17L, metadata.path("resourceId").asLong());
        assertEquals("lab-model-v1", metadata.path("modelVersion").asText());
        assertTrue(metadata.path("adapterVersion").isNull());
        assertEquals("prompt-v3", metadata.path("promptVersion").asText());
        assertEquals("request-p9-t6", metadata.path("requestId").asText());
        assertEquals("NOT_REQUIRED", metadata.path("confirmation").asText());
        assertEquals("SUCCEEDED", metadata.path("executionResult").asText());
        assertTrue(metadata.path("failureCode").isNull());

        List<AiUsageLogEntity> usage = usageLogRepository.findAll();
        assertEquals(1, usage.size());
        assertEquals(actor.getId(), usage.getFirst().getUserId());
        assertEquals(AiAssistantKey.LAB_ASSISTANT, usage.getFirst().getAssistantKey());
        assertEquals("STUDENT", usage.getFirst().getRole());
        assertEquals("lab", usage.getFirst().getModule());
        assertEquals(10L, usage.getFirst().getLabId());
        assertEquals(12, usage.getFirst().getPromptTokens());
        assertEquals(7, usage.getFirst().getCompletionTokens());
        assertEquals("SUCCESS", usage.getFirst().getStatus());
        assertNull(usage.getFirst().getErrorMessage());

        String persisted = audit.getDescription() + audit.getMetadataJson() + usage.getFirst().getErrorMessage();
        assertFalse(persisted.contains("Authorization"));
        assertFalse(persisted.contains("Bearer"));
        assertFalse(persisted.contains("raw-prompt"));
        assertFalse(persisted.contains("authorizedContext"));
        assertFalse(persisted.contains("stackTrace"));
    }

    @Test
    void deniedOrGatedToolOutcomesAreAuditedWithoutUsageConsumption() throws Exception {
        auditUsageService.recordToolOutcome(new AiToolAuditEvent(
                actor.getId(), AiAssistantKey.LAB_ASSISTANT, AiToolId.LAB_BOOKING_DRAFT,
                null, AiCapability.LAB_BOOKING_DRAFT, AiResourceType.TIME_SLOT, 17L,
                "lab-model-v1", null, "prompt-v3", "request-gated",
                AiAuditGateStatus.CONFIRMATION_REQUIRED, AiAuditExecutionResult.GATE_REQUIRED,
                AiAuditFailureCode.TOOL_CONFIRMATION_REQUIRED));
        entityManager.flush();
        entityManager.clear();

        AuditLog audit = auditLogRepository.findAll().getFirst();
        JsonNode metadata = objectMapper.readTree(audit.getMetadataJson());
        assertEquals(AuditAction.AI_TOOL_OUTCOME, audit.getAction());
        assertEquals("lab.booking.draft", metadata.path("toolId").asText());
        assertEquals("CONFIRMATION_REQUIRED", metadata.path("confirmation").asText());
        assertEquals("GATE_REQUIRED", metadata.path("executionResult").asText());
        assertEquals("TOOL_CONFIRMATION_REQUIRED", metadata.path("failureCode").asText());
        assertTrue(usageLogRepository.findAll().isEmpty());
    }

    @Test
    void failedAssistantOperationPersistsAuditOnlyWithoutFabricatedTokens() throws Exception {
        auditUsageService.recordAssistantRequest(new AiAssistantAuditEvent(
                actor.getId(), AiAssistantKey.LAB_ASSISTANT, AiCapability.LAB_SLOT_READ,
                AiResourceType.TIME_SLOT, 17L, "lab-model-v1", null, "prompt-v3",
                "request-model-failed", AiAuditGateStatus.NOT_REQUIRED,
                AiAuditExecutionResult.FAILED, AiAuditFailureCode.GATEWAY_FAILED, null,
                null, null, null, null, null, false));
        entityManager.flush();
        entityManager.clear();

        JsonNode metadata = objectMapper.readTree(auditLogRepository.findAll().getFirst().getMetadataJson());
        assertEquals("lab-model-v1", metadata.path("modelVersion").asText());
        assertEquals("prompt-v3", metadata.path("promptVersion").asText());
        assertEquals("FAILED", metadata.path("executionResult").asText());
        assertEquals("GATEWAY_FAILED", metadata.path("failureCode").asText());
        assertFalse(metadata.has("promptTokens"));
        assertFalse(metadata.has("completionTokens"));
        assertTrue(usageLogRepository.findAll().isEmpty());
    }

    @Test
    void deniedAvailabilityUsesTheAuthoritativeCurrentActorAndExplicitNullAssistant() throws Exception {
        auditUsageService.recordAssistantRequest(new AiAssistantAuditEvent(
                null, null, AiCapability.LAB_SLOT_READ, AiResourceType.TIME_SLOT, 17L,
                null, null, null, "request-unavailable", AiAuditGateStatus.NOT_REQUIRED,
                AiAuditExecutionResult.DENIED, AiAuditFailureCode.ASSISTANT_UNAVAILABLE,
                null, null, null, null, null, null, false));
        entityManager.flush();
        entityManager.clear();

        AuditLog audit = auditLogRepository.findAll().getFirst();
        JsonNode metadata = objectMapper.readTree(audit.getMetadataJson());
        assertEquals(actor.getId(), audit.getActorId());
        assertTrue(metadata.path("assistant").isNull());
        assertEquals("ASSISTANT_UNAVAILABLE", metadata.path("failureCode").asText());
        assertTrue(usageLogRepository.findAll().isEmpty());
    }

    @Test
    void mismatchedClaimedActorFailsClosedWithoutPersistingAuditOrUsage() {
        AiAssistantAuditEvent event = new AiAssistantAuditEvent(
                actor.getId() + 1, AiAssistantKey.LAB_ASSISTANT, AiCapability.LAB_SLOT_READ,
                AiResourceType.TIME_SLOT, 17L, "lab-model-v1", null, "prompt-v3",
                "request-actor-mismatch", AiAuditGateStatus.NOT_REQUIRED,
                AiAuditExecutionResult.SUCCEEDED, null, AiAssistantSystemRole.STUDENT,
                10L, null, null, 1, 1, true);

        assertThrows(AccessDeniedException.class, () -> auditUsageService.recordAssistantRequest(event));

        assertTrue(auditLogRepository.findAll().isEmpty());
        assertTrue(usageLogRepository.findAll().isEmpty());
    }

    @TestConfiguration
    static class JacksonTestConfiguration {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }
}
