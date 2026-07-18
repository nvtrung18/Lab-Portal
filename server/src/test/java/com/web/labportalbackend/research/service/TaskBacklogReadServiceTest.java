package com.web.labportalbackend.research.service;

import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.research.dto.response.TaskBacklogPageResponse;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.enums.TaskStatus;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
import com.web.labportalbackend.research.security.TaskPermissionHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskBacklogReadServiceTest {
    @Mock TaskRepository taskRepository;
    @Mock ProjectRepository projectRepository;
    @Mock GroupRepository groupRepository;
    @Mock GroupMemberRepository groupMemberRepository;
    @Mock UserRepository userRepository;
    @Mock TaskPermissionHelper permissionHelper;
    @Mock Authentication authentication;
    @InjectMocks TaskBoardReadService service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void projectIsLoadedBeforeCurrentUserAndUserIsResolvedOnce() {
        ProjectEntity project = setupManager();
        when(taskRepository.findBacklogTasksForManager(anyLong(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.readBacklog(project.getId(), 0, 20);

        InOrder order = inOrder(projectRepository, userRepository);
        order.verify(projectRepository).findByIdAndDeletedFalseAndActiveTrue(50L);
        order.verify(userRepository).findByUsername("manager");
        verify(userRepository, times(1)).findByUsername("manager");
    }

    @Test
    void missingProjectSkipsCurrentUserAndBacklogDependencies() {
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.readBacklog(50L, 0, 20));

        verifyNoInteractions(userRepository, permissionHelper, groupMemberRepository, taskRepository);
    }

    @Test
    void managerUsesManagerQueryWithUnsortedPageRequest() {
        setupManager();
        when(taskRepository.findBacklogTasksForManager(anyLong(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 50), 0));

        service.readBacklog(50L, 1, 50);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(taskRepository).findBacklogTasksForManager(org.mockito.ArgumentMatchers.eq(50L), pageable.capture());
        assertEquals(1, pageable.getValue().getPageNumber());
        assertEquals(50, pageable.getValue().getPageSize());
        assertFalse(pageable.getValue().getSort().isSorted());
        verify(taskRepository, never()).findBacklogTasksForStudent(anyLong(), anyList(), anyList(), anyLong(), any());
    }

    @Test
    void studentUsesStrictLeaderAndMemberPageQuery() {
        setupStudent(List.of(100L), List.of(200L));
        when(taskRepository.findBacklogTasksForStudent(anyLong(), anyList(), anyList(), anyLong(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.readBacklog(50L, 0, 20);

        verify(taskRepository).findBacklogTasksForStudent(50L, List.of(100L), List.of(200L), 7L,
                PageRequest.of(0, 20));
        verify(taskRepository, never()).findBacklogTasksForManager(anyLong(), any());
    }

    @Test
    void unrelatedStudentIsDeniedBeforeBacklogQuery() {
        authenticate("student");
        User user = activeUser(7L);
        ProjectEntity project = project();
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(Optional.of(project));
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(user));
        when(permissionHelper.canViewProjectBoard(7L, project)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.readBacklog(50L, 0, 20));

        verify(taskRepository, never()).findBacklogTasksForManager(anyLong(), any());
        verify(taskRepository, never()).findBacklogTasksForStudent(anyLong(), anyList(), anyList(), anyLong(), any());
    }

    @ParameterizedTest
    @MethodSource("validPagination")
    void validPaginationValuesArePassedUnchanged(int page, int size) {
        setupManager();
        when(taskRepository.findBacklogTasksForManager(anyLong(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(page, size), 0));

        service.readBacklog(50L, page, size);

        verify(taskRepository).findBacklogTasksForManager(50L, PageRequest.of(page, size));
    }

    @ParameterizedTest
    @MethodSource("invalidPagination")
    void invalidPaginationIsRejectedWithoutClampingOrTaskQuery(int page, int size) {
        setupManager();

        assertThrows(IllegalArgumentException.class, () -> service.readBacklog(50L, page, size));

        verify(taskRepository, never()).findBacklogTasksForManager(anyLong(), any());
        verify(taskRepository, never()).findBacklogTasksForStudent(anyLong(), anyList(), anyList(), anyLong(), any());
    }

    @Test
    void responseMapsContentAndCopiesMetadataFromSameRepositoryPage() {
        setupManager();
        TaskEntity task = TaskEntity.builder().title("Mapped backlog").projectId(50L).milestoneId(1L)
                .status(TaskStatus.BACKLOG).build();
        task.setId(99L);
        when(taskRepository.findBacklogTasksForManager(50L, PageRequest.of(2, 20)))
                .thenReturn(new PageImpl<>(List.of(task), PageRequest.of(2, 20), 41));

        TaskBacklogPageResponse response = service.readBacklog(50L, 2, 20);

        assertEquals(List.of(99L), response.content().stream().map(item -> item.getId()).toList());
        assertEquals(2, response.page());
        assertEquals(20, response.size());
        assertEquals(41, response.totalElements());
        assertEquals(3, response.totalPages());
    }

    @Test
    void emptyRepositoryPageReturnsNonNullEmptyContentAndMetadata() {
        setupManager();
        when(taskRepository.findBacklogTasksForManager(50L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        TaskBacklogPageResponse response = service.readBacklog(50L, 0, 20);

        assertNotNull(response.content());
        assertEquals(List.of(), response.content());
        assertEquals(0, response.totalElements());
        assertEquals(0, response.totalPages());
    }

    @Test
    void backlogReadIsReadOnlyTransactional() throws NoSuchMethodException {
        Transactional transactional = TaskBoardReadService.class
                .getMethod("readBacklog", Long.class, int.class, int.class)
                .getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertEquals(true, transactional.readOnly());
    }

    private ProjectEntity setupManager() {
        authenticate("manager");
        User manager = activeUser(2L);
        manager.addRole(new Role("LAB_MANAGER", "manager"));
        ProjectEntity project = project();
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(Optional.of(project));
        when(userRepository.findByUsername("manager")).thenReturn(Optional.of(manager));
        when(permissionHelper.canViewProjectBoard(2L, project)).thenReturn(true);
        return project;
    }

    private void setupStudent(List<Long> leaderGroups, List<Long> memberGroups) {
        authenticate("student");
        User student = activeUser(7L);
        ProjectEntity project = project();
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(Optional.of(project));
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(student));
        when(permissionHelper.canViewProjectBoard(7L, project)).thenReturn(true);
        when(groupMemberRepository.findActiveGroupIdsByProjectIdAndUserIdAndRole(50L, 7L, GroupRole.LEADER))
                .thenReturn(leaderGroups);
        when(groupMemberRepository.findActiveGroupIdsByProjectIdAndUserIdAndRole(50L, 7L, GroupRole.MEMBER))
                .thenReturn(memberGroups);
    }

    private void authenticate(String username) {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(username);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private User activeUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setActive(true);
        user.setDeleted(false);
        return user;
    }

    private ProjectEntity project() {
        Laboratory laboratory = new Laboratory();
        laboratory.setId(1L);
        ProjectEntity project = ProjectEntity.builder().title("Project").lab(laboratory).build();
        project.setId(50L);
        return project;
    }

    private static Stream<Arguments> validPagination() {
        return Stream.of(Arguments.of(0, 20), Arguments.of(0, 100));
    }

    private static Stream<Arguments> invalidPagination() {
        return Stream.of(
                Arguments.of(-1, 20),
                Arguments.of(0, 0),
                Arguments.of(0, -1),
                Arguments.of(0, 101)
        );
    }
}
