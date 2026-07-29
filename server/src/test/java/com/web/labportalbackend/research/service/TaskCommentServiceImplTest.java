package com.web.labportalbackend.research.service;

import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.research.dto.request.CreateTaskCommentRequest;
import com.web.labportalbackend.research.dto.response.TaskCommentResponse;
import com.web.labportalbackend.research.entity.TaskCommentEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.TaskCommentRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
import com.web.labportalbackend.research.security.TaskPermissionHelper;
import com.web.labportalbackend.research.service.impl.TaskCommentServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskCommentServiceImplTest {
    @Mock TaskCommentRepository taskCommentRepository;
    @Mock TaskRepository taskRepository;
    @Mock UserRepository userRepository;
    @Mock GroupMemberRepository groupMemberRepository;
    @Mock TaskPermissionHelper taskPermissionHelper;
    private TaskCommentService service;
    private User author;
    private TaskEntity task;

    @BeforeEach
    void setUp() {
        service = new TaskCommentServiceImpl(taskCommentRepository, taskRepository, userRepository, groupMemberRepository, taskPermissionHelper);
        author = user(7L, "student", "STUDENT");
        task = TaskEntity.builder().projectId(3L).groupId(4L).title("Task").build(); task.setId(42L);
        authenticate("student");
        lenient().when(userRepository.findByUsername("student")).thenReturn(Optional.of(author));
        lenient().when(taskRepository.findByIdAndDeletedFalseAndActiveTrue(42L)).thenReturn(Optional.of(task));
        lenient().when(taskPermissionHelper.canViewTask(7L, task)).thenReturn(true);
        lenient().when(groupMemberRepository.findActiveRoleByGroupIdAndUserId(4L, 7L)).thenReturn(Optional.of(GroupRole.MEMBER));
        lenient().when(taskCommentRepository.save(any(TaskCommentEntity.class))).thenAnswer(i -> { TaskCommentEntity c = i.getArgument(0); c.setId(501L); c.setCreatedAt(Instant.parse("2026-07-29T01:00:00Z")); return c; });
    }
    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test void managerLeaderMemberAndAssigneePathsSaveAndMap() {
        assertSuccess(user(2L, "manager", "LAB_MANAGER"), null, "LAB_MANAGER");
        assertSuccess(user(7L, "student", "STUDENT"), GroupRole.LEADER, "STUDENT");
        assertSuccess(user(7L, "student", "STUDENT"), GroupRole.MEMBER, "STUDENT");
        when(groupMemberRepository.findActiveRoleByGroupIdAndUserId(4L, 7L)).thenReturn(Optional.empty());
        TaskCommentResponse response = service.addComment(42L, request("Valid"));
        assertNull(response.getGroupRole()); assertEquals("STUDENT", response.getAuthorRole());
    }

    @Test void savedCommentMapsEveryFrozenResponseField() {
        TaskCommentResponse response = service.addComment(42L, request("  Add dates  "));

        assertEquals(501L, response.getId());
        assertEquals(42L, response.getTaskId());
        assertEquals(7L, response.getAuthorId());
        assertEquals("Student User", response.getAuthorName());
        assertEquals("student@example.test", response.getAuthorEmail());
        assertEquals("STUDENT", response.getAuthorRole());
        assertEquals(GroupRole.MEMBER, response.getGroupRole());
        assertEquals("Add dates", response.getContent());
        assertEquals(Instant.parse("2026-07-29T01:00:00Z"), response.getCreatedAt());
    }

    @Test void unsupportedRolelessAndAdminCallersAreDeniedImmediatelyAfterAuthorLookup() {
        for (User unsupported : List.of(user(7L, "student", "ADMIN"), user(7L, "student", "OTHER"), rolelessUser())) {
            reset(taskRepository, taskPermissionHelper, groupMemberRepository, taskCommentRepository);
            when(userRepository.findByUsername("student")).thenReturn(Optional.of(unsupported));
            assertThrows(AccessDeniedException.class, () -> service.addComment(42L, request("Valid")));
            verifyNoInteractions(taskRepository, taskPermissionHelper, groupMemberRepository, taskCommentRepository);
        }
    }

    @Test void deniedVisibilityDoesNotSaveAndCallsPermissionWithServerResolvedIds() {
        when(taskPermissionHelper.canViewTask(7L, task)).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> service.addComment(42L, request("Valid")));
        verify(taskPermissionHelper).canViewTask(7L, task); verify(taskCommentRepository, never()).save(any());
    }

    @Test void unusableAuthorsAreDeniedBeforeTaskLookup() {
        for (User unusable : List.of(inactiveUser(), deletedUser())) {
            reset(taskRepository, taskPermissionHelper, groupMemberRepository, taskCommentRepository);
            when(userRepository.findByUsername("student")).thenReturn(Optional.of(unusable));
            assertThrows(AccessDeniedException.class, () -> service.addComment(42L, request("Valid")));
            verifyNoInteractions(taskRepository, taskPermissionHelper, groupMemberRepository, taskCommentRepository);
        }
        when(userRepository.findByUsername("student")).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> service.addComment(42L, request("Valid")));
    }

    @Test void absentAuthenticationAndTaskAreRejectedWithoutSave() {
        SecurityContextHolder.clearContext();
        assertThrows(AccessDeniedException.class, () -> service.addComment(42L, request("Valid")));
        verifyNoInteractions(taskRepository, taskPermissionHelper, groupMemberRepository, taskCommentRepository);
        authenticate("student"); when(taskRepository.findByIdAndDeletedFalseAndActiveTrue(42L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.addComment(42L, request("Valid")));
        verify(taskCommentRepository, never()).save(any());
    }

    @Test void validationRejectsNullMissingBlankOneAndTwoThousandOneBeforeAnyDownstreamCall() {
        List<CreateTaskCommentRequest> invalid = java.util.Arrays.asList(null, request(null), request("  "), request(" x "), request("x".repeat(2001)));
        for (CreateTaskCommentRequest value : invalid) assertThrows(IllegalArgumentException.class, () -> service.addComment(42L, value));
        CreateTaskCommentRequest unknown = request("Valid"); unknown.captureUnknownProperty("authorId", null);
        assertThrows(IllegalArgumentException.class, () -> service.addComment(42L, unknown));
        verifyNoInteractions(userRepository, taskRepository, taskPermissionHelper, groupMemberRepository, taskCommentRepository);
    }

    @Test void boundariesTrimAndExactOrderingArePreserved() {
        TaskCommentResponse response = service.addComment(42L, request("  " + "x".repeat(2000) + "  "));
        ArgumentCaptor<TaskCommentEntity> capture = ArgumentCaptor.forClass(TaskCommentEntity.class);
        verify(taskCommentRepository).save(capture.capture());
        assertEquals(42L, capture.getValue().getTaskId()); assertEquals(7L, capture.getValue().getAuthorId());
        assertEquals("x".repeat(2000), capture.getValue().getContent()); assertEquals(capture.getValue().getContent(), response.getContent());
        InOrder order = inOrder(userRepository, taskRepository, taskPermissionHelper, taskCommentRepository, groupMemberRepository);
        order.verify(userRepository).findByUsername("student"); order.verify(taskRepository).findByIdAndDeletedFalseAndActiveTrue(42L);
        order.verify(taskPermissionHelper).canViewTask(7L, task); order.verify(taskCommentRepository).save(any());
        order.verify(groupMemberRepository).findActiveRoleByGroupIdAndUserId(4L, 7L);
    }

    @Test void trimmedMinimumAndRepositoryFailureAreObserved() {
        TaskCommentResponse response = service.addComment(42L, request("  ok  ")); assertEquals("ok", response.getContent());
        when(taskCommentRepository.save(any())).thenThrow(new IllegalStateException("db down"));
        assertThrows(IllegalStateException.class, () -> service.addComment(42L, request("Valid")));
        verify(groupMemberRepository, times(1)).findActiveRoleByGroupIdAndUserId(4L, 7L);
    }

    private void assertSuccess(User user, GroupRole role, String expectedRole) {
        reset(taskCommentRepository, groupMemberRepository); author = user; when(userRepository.findByUsername("student")).thenReturn(Optional.of(user));
        when(taskPermissionHelper.canViewTask(user.getId(), task)).thenReturn(true);
        when(taskCommentRepository.save(any())).thenAnswer(i -> { TaskCommentEntity c=i.getArgument(0); c.setId(501L); return c; });
        if (role != null) when(groupMemberRepository.findActiveRoleByGroupIdAndUserId(4L, user.getId())).thenReturn(Optional.of(role));
        TaskCommentResponse response = service.addComment(42L, request("Valid")); assertEquals(expectedRole, response.getAuthorRole()); assertEquals(role, response.getGroupRole());
    }
    private CreateTaskCommentRequest request(String content) { CreateTaskCommentRequest r = new CreateTaskCommentRequest(); r.setContent(content); return r; }
    private User user(Long id, String username, String role) { User u = rolelessUser(); u.setId(id); u.setUsername(username); u.addRole(new Role(role, role)); return u; }
    private User rolelessUser() { User u = new User(); u.setId(7L); u.setUsername("student"); u.setFullName("Student User"); u.setEmail("student@example.test"); return u; }
    private User inactiveUser() { User u=user(7L,"student","STUDENT"); u.setActive(false); return u; }
    private User deletedUser() { User u=user(7L,"student","STUDENT"); u.setDeleted(true); return u; }
    private void authenticate(String username) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username, null, List.of())); }
}
