package com.web.labportalbackend.research.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.research.entity.TaskActivityEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.enums.TaskAuditAction;
import com.web.labportalbackend.research.enums.TaskPriority;
import com.web.labportalbackend.research.enums.TaskStatus;
import com.web.labportalbackend.research.enums.TaskType;
import com.web.labportalbackend.research.repository.TaskActivityRepository;
import com.web.labportalbackend.research.service.impl.TaskActivityRecorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskActivityRecorderTest {

    @Mock TaskActivityRepository taskActivityRepository;

    @Test
    void recordsCreationWithExactActorAndCompleteVersionedSnapshot() {
        TaskActivityRecorder recorder = recorder();

        recorder.recordCreation(task(), actor(7L));

        TaskActivityEntity activity = capturedActivities(1).get(0);
        assertActivity(activity, TaskAuditAction.TASK_CREATED, null, fullSnapshot(TaskStatus.IN_PROGRESS, 8L));
    }

    @Test
    void recordsEveryApprovedMutationActionWithExactOldAndNewSnapshots() {
        TaskActivityRecorder recorder = recorder();
        TaskEntity assignedOnly = task();
        TaskActivityRecorder.TaskSnapshot assignedBefore = recorder.capture(assignedOnly);

        assignedOnly.setAssigneeId(9L);
        recorder.recordMutation(assignedBefore, assignedOnly, actor(7L));
        TaskEntity mixed = task();
        TaskActivityRecorder.TaskSnapshot mixedBefore = recorder.capture(mixed);
        mixed.setAssigneeId(9L);
        mixed.setTitle("Renamed");
        recorder.recordMutation(mixedBefore, mixed, actor(7L));
        recordStatusChange(recorder, TaskStatus.IN_PROGRESS, TaskStatus.TODO);
        recordStatusChange(recorder, TaskStatus.TODO, TaskStatus.BLOCKED);
        recordStatusChange(recorder, TaskStatus.BLOCKED, TaskStatus.DONE);
        recordStatusChange(recorder, TaskStatus.DONE, TaskStatus.CANCELLED);

        List<TaskActivityEntity> activities = capturedActivities(6);
        assertEquals(List.of(TaskAuditAction.TASK_ASSIGNED, TaskAuditAction.TASK_METADATA_UPDATED,
                        TaskAuditAction.TASK_STATUS_CHANGED, TaskAuditAction.TASK_BLOCKED,
                        TaskAuditAction.TASK_COMPLETED, TaskAuditAction.TASK_CANCELLED),
                activities.stream().map(TaskActivityEntity::getAction).toList());
        assertActivity(activities.get(0), TaskAuditAction.TASK_ASSIGNED,
                fullSnapshot(TaskStatus.IN_PROGRESS, 8L), fullSnapshot(TaskStatus.IN_PROGRESS, 9L));
        assertActivity(activities.get(1), TaskAuditAction.TASK_METADATA_UPDATED,
                fullSnapshot(TaskStatus.IN_PROGRESS, 8L), fullSnapshot(TaskStatus.IN_PROGRESS, 9L, "Renamed"));
        assertActivity(activities.get(2), TaskAuditAction.TASK_STATUS_CHANGED,
                fullSnapshot(TaskStatus.IN_PROGRESS, 8L), fullSnapshot(TaskStatus.TODO, 8L));
        assertActivity(activities.get(3), TaskAuditAction.TASK_BLOCKED,
                fullSnapshot(TaskStatus.TODO, 8L), fullSnapshot(TaskStatus.BLOCKED, 8L));
        assertActivity(activities.get(4), TaskAuditAction.TASK_COMPLETED,
                fullSnapshot(TaskStatus.BLOCKED, 8L), fullSnapshot(TaskStatus.DONE, 8L));
        assertActivity(activities.get(5), TaskAuditAction.TASK_CANCELLED,
                fullSnapshot(TaskStatus.DONE, 8L), fullSnapshot(TaskStatus.CANCELLED, 8L));
    }

    @Test
    void preservesNullToValueValueToNullAndBothNullAsOmittedSnapshotProperties() {
        TaskActivityRecorder recorder = recorder();
        TaskEntity changed = task();
        changed.setDescription(null);
        changed.setDueDate(null);
        changed.setBlockedReason(null);
        TaskActivityRecorder.TaskSnapshot allNullBefore = recorder.capture(changed);
        changed.setDescription("Added");
        changed.setDueDate(LocalDate.of(2026, 9, 1));
        recorder.recordMutation(allNullBefore, changed, actor(7L));
        TaskActivityRecorder.TaskSnapshot valueBefore = recorder.capture(changed);
        changed.setDescription(null);
        changed.setDueDate(null);
        recorder.recordMutation(valueBefore, changed, actor(7L));

        List<TaskActivityEntity> activities = capturedActivities(2);
        assertActivity(activities.get(0), TaskAuditAction.TASK_METADATA_UPDATED,
                nullableSnapshot(null, null), nullableSnapshot("Added", "2026-09-01"));
        assertActivity(activities.get(1), TaskAuditAction.TASK_METADATA_UPDATED,
                nullableSnapshot("Added", "2026-09-01"), nullableSnapshot(null, null));
    }

    @Test
    void omitsNullsWithoutMutatingTheInjectedMapper() throws Exception {
        ObjectMapper injectedMapper = objectMapper();
        TaskActivityRecorder recorder = new TaskActivityRecorder(taskActivityRepository, injectedMapper);
        TaskEntity nullableTask = task();
        nullableTask.setDescription(null);

        assertTrue(injectedMapper.writeValueAsString(recorder.capture(nullableTask))
                .contains("\"description\":null"));

        recorder.recordCreation(nullableTask, actor(7L));

        TaskActivityEntity activity = capturedActivities(1).get(0);
        assertFalse(activity.getNewValue().contains("\"description\""));
        assertTrue(injectedMapper.writeValueAsString(recorder.capture(nullableTask))
                .contains("\"description\":null"));
    }

    @Test
    void equalSnapshotsDoNotPersistActivity() {
        TaskActivityRecorder recorder = recorder();
        TaskEntity unchanged = task();

        recorder.recordMutation(recorder.capture(unchanged), unchanged, actor(7L));

        verifyNoInteractions(taskActivityRepository);
    }

    @Test
    void serializationFailurePreservesCauseAndDoesNotSave() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        JsonProcessingException cause = new JsonProcessingException("json unavailable") { };
        when(failingMapper.copy()).thenReturn(failingMapper);
        when(failingMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)).thenReturn(failingMapper);
        when(failingMapper.writeValueAsString(any())).thenThrow(cause);
        TaskActivityRecorder recorder = new TaskActivityRecorder(taskActivityRepository, failingMapper);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> recorder.recordCreation(task(), actor(7L)));

        assertSame(cause, exception.getCause());
        verifyNoInteractions(taskActivityRepository);
    }

    @Test
    void rejectsMissingRequiredRecorderIdsBeforePersistence() {
        TaskActivityRecorder recorder = recorder();
        TaskEntity missingTaskId = task();
        missingTaskId.setId(null);

        assertThrows(NullPointerException.class, () -> recorder.recordCreation(missingTaskId, actor(7L)));
        assertThrows(NullPointerException.class, () -> recorder.recordCreation(task(), null));
        assertThrows(IllegalStateException.class, () -> recorder.recordCreation(task(), actor(null)));
        verifyNoInteractions(taskActivityRepository);
    }

    private void recordStatusChange(TaskActivityRecorder recorder, TaskStatus beforeStatus, TaskStatus afterStatus) {
        TaskEntity after = task();
        after.setStatus(afterStatus);
        recorder.recordMutation(recorder.capture(withStatus(beforeStatus)), after, actor(7L));
    }

    private List<TaskActivityEntity> capturedActivities(int expectedCount) {
        ArgumentCaptor<TaskActivityEntity> captured = ArgumentCaptor.forClass(TaskActivityEntity.class);
        verify(taskActivityRepository, times(expectedCount)).save(captured.capture());
        return captured.getAllValues();
    }

    private void assertActivity(TaskActivityEntity activity, TaskAuditAction action, String oldValue, String newValue) {
        assertEquals(20L, activity.getTaskId());
        assertEquals(7L, activity.getUserId());
        assertEquals(action, activity.getAction());
        assertEquals(oldValue, activity.getOldValue());
        assertEquals(newValue, activity.getNewValue());
    }

    private String fullSnapshot(TaskStatus status, Long assigneeId) {
        return fullSnapshot(status, assigneeId, "Prepare dataset");
    }

    private String fullSnapshot(TaskStatus status, Long assigneeId, String title) {
        return "{\"schemaVersion\":1,\"projectId\":50,\"groupId\":100,\"milestoneId\":10,\"parentTaskId\":5,\"assigneeId\":"
                + assigneeId + ",\"title\":\"" + title + "\",\"description\":\"Normalize source files\",\"status\":\""
                + status + "\",\"priority\":\"HIGH\",\"type\":\"TASK\",\"dueDate\":\"2026-08-01\",\"blockedReason\":\"Waiting for access\",\"progressPercent\":10}";
    }

    private String nullableSnapshot(String description, String dueDate) {
        String descriptionField = description == null ? "" : ",\"description\":\"" + description + "\"";
        String dueDateField = dueDate == null ? "" : ",\"dueDate\":\"" + dueDate + "\"";
        return "{\"schemaVersion\":1,\"projectId\":50,\"groupId\":100,\"milestoneId\":10,\"parentTaskId\":5,\"assigneeId\":8,\"title\":\"Prepare dataset\""
                + descriptionField + ",\"status\":\"IN_PROGRESS\",\"priority\":\"HIGH\",\"type\":\"TASK\""
                + dueDateField + ",\"progressPercent\":10}";
    }

    private TaskActivityRecorder recorder() { return new TaskActivityRecorder(taskActivityRepository, objectMapper()); }

    private TaskEntity task() {
        TaskEntity task = TaskEntity.builder().projectId(50L).groupId(100L).milestoneId(10L).parentTaskId(5L)
                .assigneeId(8L).title("Prepare dataset").description("Normalize source files")
                .status(TaskStatus.IN_PROGRESS).priority(TaskPriority.HIGH).type(TaskType.TASK)
                .dueDate(LocalDate.of(2026, 8, 1)).blockedReason("Waiting for access").progressPercent(10).build();
        task.setId(20L);
        return task;
    }

    private TaskEntity withStatus(TaskStatus status) { TaskEntity task = task(); task.setStatus(status); return task; }
    private TaskEntity withAssignee(Long assigneeId) { TaskEntity task = task(); task.setAssigneeId(assigneeId); return task; }
    private User actor(Long id) { User actor = new User(); actor.setId(id); return actor; }
    private ObjectMapper objectMapper() { return new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); }
}
