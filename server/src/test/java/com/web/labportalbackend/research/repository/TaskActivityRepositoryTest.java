package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.research.entity.TaskActivityEntity;
import com.web.labportalbackend.research.enums.TaskAuditAction;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest
class TaskActivityRepositoryTest {

    @Autowired TaskActivityRepository taskActivityRepository;
    @Autowired EntityManager entityManager;

    @Test
    void savesAndReloadsActivityPayloadIncludingNullableValuesAndBaseEntityFields() {
        TaskActivityEntity saved = taskActivityRepository.saveAndFlush(TaskActivityEntity.builder()
                .taskId(10L)
                .userId(20L)
                .action(TaskAuditAction.TASK_STATUS_CHANGED)
                .oldValue(null)
                .newValue("IN_PROGRESS")
                .build());
        TaskActivityEntity removal = taskActivityRepository.saveAndFlush(TaskActivityEntity.builder()
                .taskId(10L)
                .userId(20L)
                .action(TaskAuditAction.TASK_ASSIGNED)
                .oldValue("20")
                .newValue(null)
                .build());
        entityManager.clear();

        TaskActivityEntity reloaded = taskActivityRepository.findById(saved.getId()).orElseThrow();
        assertEquals(10L, reloaded.getTaskId());
        assertEquals(20L, reloaded.getUserId());
        assertEquals(TaskAuditAction.TASK_STATUS_CHANGED, reloaded.getAction());
        assertEquals(null, reloaded.getOldValue());
        assertEquals("IN_PROGRESS", reloaded.getNewValue());
        assertNotNull(reloaded.getCreatedAt());
        assertNotNull(reloaded.getUpdatedAt());
        assertEquals(Boolean.TRUE, reloaded.getActive());
        assertEquals(Boolean.FALSE, reloaded.getDeleted());
        assertEquals(null, taskActivityRepository.findById(removal.getId()).orElseThrow().getNewValue());
    }

    @Test
    void findsOnlyRequestedTaskActivitiesInDeterministicNewestFirstOrder() {
        Instant older = Instant.parse("2026-07-01T00:00:00Z");
        Instant newer = Instant.parse("2026-07-02T00:00:00Z");
        TaskActivityEntity firstAtNewerTime = save(10L, older, "TODO", "IN_PROGRESS");
        TaskActivityEntity secondAtNewerTime = save(10L, newer, "IN_PROGRESS", null);
        TaskActivityEntity thirdAtNewerTime = save(10L, newer, null, "DONE");
        save(11L, newer, "TODO", "CANCELLED");
        entityManager.clear();

        List<Long> activityIds = taskActivityRepository.findByTaskIdOrderByCreatedAtDescIdDesc(10L).stream()
                .map(TaskActivityEntity::getId)
                .toList();

        assertEquals(List.of(thirdAtNewerTime.getId(), secondAtNewerTime.getId(), firstAtNewerTime.getId()), activityIds);
    }

    private TaskActivityEntity save(Long taskId, Instant createdAt, String oldValue, String newValue) {
        TaskActivityEntity activity = TaskActivityEntity.builder()
                .taskId(taskId)
                .userId(20L)
                .action(TaskAuditAction.TASK_STATUS_CHANGED)
                .oldValue(oldValue)
                .newValue(newValue)
                .build();
        activity.setCreatedAt(createdAt);
        activity.setUpdatedAt(createdAt);
        return taskActivityRepository.saveAndFlush(activity);
    }
}
