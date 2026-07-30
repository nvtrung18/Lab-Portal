package com.web.labportalbackend.research.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.labportalbackend.research.entity.TaskProposalEntity;
import com.web.labportalbackend.research.enums.TaskProposalStatus;
import com.web.labportalbackend.research.enums.TaskType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class TaskProposalRepositoryTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired TaskProposalRepository taskProposalRepository;
    @Autowired EntityManager entityManager;

    @Test
    void savesAndReloadsManualPendingProposalWithNullableFieldsAndBaseEntityFields() {
        TaskProposalEntity saved = taskProposalRepository.saveAndFlush(proposal(TaskType.TASK));
        entityManager.clear();

        TaskProposalEntity reloaded = taskProposalRepository.findByIdAndDeletedFalseAndActiveTrue(saved.getId()).orElseThrow();

        assertEquals(101L, reloaded.getProposedById());
        assertEquals(201L, reloaded.getProjectId());
        assertEquals(301L, reloaded.getGroupId());
        assertEquals(null, reloaded.getMilestoneId());
        assertEquals(null, reloaded.getReviewedById());
        assertEquals(null, reloaded.getAiActionSuggestionId());
        assertEquals(null, reloaded.getReason());
        assertEquals(null, reloaded.getReviewedAt());
        assertFalse(reloaded.getAssistedByAi());
        assertEquals(TaskProposalStatus.PENDING, reloaded.getStatus());
        assertJsonEquals(payload(TaskType.TASK), reloaded.getPayloadJson());
        assertNotNull(reloaded.getCreatedAt());
        assertNotNull(reloaded.getUpdatedAt());
        assertEquals(Boolean.TRUE, reloaded.getActive());
        assertEquals(Boolean.FALSE, reloaded.getDeleted());
    }

    @ParameterizedTest
    @EnumSource(TaskType.class)
    void roundTripsEveryCurrentTaskTypeInPayload(TaskType type) {
        TaskProposalEntity saved = taskProposalRepository.saveAndFlush(proposal(type));
        entityManager.clear();

        TaskProposalEntity reloaded = taskProposalRepository.findById(saved.getId()).orElseThrow();

        assertEquals(type.name(), json(reloaded.getPayloadJson()).path("type").asText());
    }

    @Test
    void savesAndReloadsAiAssistedRejectedProposalWithReviewMetadata() {
        Instant reviewedAt = Instant.parse("2026-07-30T12:34:56Z");
        TaskProposalEntity proposal = proposal(TaskType.REVIEW);
        proposal.setMilestoneId(401L);
        proposal.setAiActionSuggestionId(501L);
        proposal.setAssistedByAi(true);
        proposal.setStatus(TaskProposalStatus.REJECTED);
        proposal.setReason("Scope needs refinement");
        proposal.setReviewedById(102L);
        proposal.setReviewedAt(reviewedAt);
        proposal.setPayloadJson(payload(TaskType.REVIEW));
        TaskProposalEntity saved = taskProposalRepository.saveAndFlush(proposal);
        entityManager.clear();

        TaskProposalEntity reloaded = taskProposalRepository.findByIdAndDeletedFalseAndActiveTrue(saved.getId()).orElseThrow();

        assertTrue(reloaded.getAssistedByAi());
        assertEquals(501L, reloaded.getAiActionSuggestionId());
        assertEquals(401L, reloaded.getMilestoneId());
        assertEquals(TaskProposalStatus.REJECTED, reloaded.getStatus());
        assertEquals("Scope needs refinement", reloaded.getReason());
        assertEquals(102L, reloaded.getReviewedById());
        assertEquals(reviewedAt, reloaded.getReviewedAt());
        assertJsonEquals(payload(TaskType.REVIEW), reloaded.getPayloadJson());
    }

    @Test
    void activeLookupExcludesInactiveAndDeletedProposals() {
        TaskProposalEntity usable = taskProposalRepository.saveAndFlush(proposal(TaskType.TASK));
        TaskProposalEntity inactive = taskProposalRepository.saveAndFlush(proposal(TaskType.BUG));
        inactive.setActive(false);
        taskProposalRepository.saveAndFlush(inactive);
        TaskProposalEntity deleted = taskProposalRepository.saveAndFlush(proposal(TaskType.DOCUMENT));
        deleted.setDeleted(true);
        taskProposalRepository.saveAndFlush(deleted);

        assertTrue(taskProposalRepository.findByIdAndDeletedFalseAndActiveTrue(usable.getId()).isPresent());
        assertTrue(taskProposalRepository.findByIdAndDeletedFalseAndActiveTrue(inactive.getId()).isEmpty());
        assertTrue(taskProposalRepository.findByIdAndDeletedFalseAndActiveTrue(deleted.getId()).isEmpty());
    }

    private TaskProposalEntity proposal(TaskType type) {
        return TaskProposalEntity.builder()
                .proposedById(101L)
                .projectId(201L)
                .groupId(301L)
                .payloadJson(payload(type))
                .build();
    }

    private static String payload(TaskType type) {
        return """
                {"projectId":201,"groupId":301,"milestoneId":null,"parentTaskId":null,
                "title":"Proposal title","description":null,"priority":"MEDIUM","type":"%s","dueDate":null}
                """.formatted(type.name());
    }

    private static JsonNode json(String value) {
        try {
            return OBJECT_MAPPER.readTree(value);
        } catch (JsonProcessingException ex) {
            throw new AssertionError("Expected valid JSON", ex);
        }
    }

    private static void assertJsonEquals(String expected, String actual) {
        assertEquals(json(expected), json(actual));
    }
}
