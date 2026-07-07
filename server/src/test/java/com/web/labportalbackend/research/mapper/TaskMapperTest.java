package com.web.labportalbackend.research.mapper;

import com.web.labportalbackend.research.dto.response.TaskResponse;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.enums.TaskPriority;
import com.web.labportalbackend.research.enums.TaskType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskMapperTest {

    @Test
    void taskEntity_defaultsPriorityAndType() {
        TaskEntity task = TaskEntity.builder()
                .milestoneId(10L)
                .title("Prepare dataset")
                .build();

        assertEquals(TaskPriority.MEDIUM, task.getPriority());
        assertEquals(TaskType.TASK, task.getType());
    }

    @Test
    void toResponse_mapsResearchBoardFields() {
        TaskEntity task = TaskEntity.builder()
                .projectId(50L)
                .groupId(100L)
                .milestoneId(10L)
                .parentTaskId(5L)
                .epicId(7L)
                .title("Prepare dataset")
                .deadline(LocalDate.of(2026, 8, 1))
                .dueDate(LocalDate.of(2026, 8, 2))
                .priority(TaskPriority.URGENT)
                .type(TaskType.REVIEW)
                .blockedReason("Waiting for lab result")
                .createdBy(2L)
                .build();
        task.setId(20L);

        TaskResponse response = TaskMapper.toResponse(task, 999L, 888L, "Milestone 1", "APPROVED");

        assertEquals(20L, response.getId());
        assertEquals(50L, response.getProjectId());
        assertEquals(100L, response.getGroupId());
        assertEquals(5L, response.getParentTaskId());
        assertEquals(7L, response.getEpicId());
        assertEquals(LocalDate.of(2026, 8, 1), response.getDeadline());
        assertEquals(LocalDate.of(2026, 8, 2), response.getDueDate());
        assertEquals(TaskPriority.URGENT, response.getPriority());
        assertEquals(TaskType.REVIEW, response.getType());
        assertEquals("Waiting for lab result", response.getBlockedReason());
        assertEquals(2L, response.getCreatedBy());
    }
}
