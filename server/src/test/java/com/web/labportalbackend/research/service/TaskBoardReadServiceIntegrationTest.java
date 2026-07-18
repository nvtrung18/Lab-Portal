package com.web.labportalbackend.research.service;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.GroupMemberEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.repository.TaskRepository;
import com.web.labportalbackend.research.security.TaskPermissionHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;

@DataJpaTest
@Import(TaskBoardReadService.class)
class TaskBoardReadServiceIntegrationTest {
    @Autowired TaskBoardReadService service;
    @Autowired TestEntityManager entityManager;
    @MockBean TaskRepository taskRepository;
    @MockBean TaskPermissionHelper permissionHelper;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void managerRejectsPersistedCrossLabAssigneeBeforeBoardQuery() {
        User manager = entityManager.persist(user("manager"));
        com.web.labportalbackend.auth.entity.Role managerRole = entityManager.persist(
                new com.web.labportalbackend.auth.entity.Role("LAB_MANAGER", "manager"));
        manager.addRole(managerRole);
        entityManager.flush();
        User assignee = entityManager.persist(user("assignee"));
        Laboratory projectLab = entityManager.persist(lab("Project lab", manager));
        Laboratory otherLab = entityManager.persist(lab("Other lab", manager));
        ProjectEntity project = entityManager.persist(ProjectEntity.builder()
                .title("Requested project").lab(projectLab).manager(manager).build());
        GroupEntity inconsistentGroup = entityManager.persist(GroupEntity.builder()
                .name("Cross-lab group").lab(otherLab).project(project).leader(assignee).build());
        entityManager.persist(GroupMemberEntity.builder()
                .group(inconsistentGroup).user(assignee).role(GroupRole.MEMBER).build());
        entityManager.flush();
        entityManager.clear();

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("manager", "password", java.util.List.of()));
        when(permissionHelper.canViewProjectBoard(anyLong(), any(ProjectEntity.class))).thenReturn(true);

        assertThrows(AccessDeniedException.class,
                () -> service.read(project.getId(), null, assignee.getId(), null, null, null, false, false));
        verify(taskRepository, never()).findBoardTasksForManager(anyLong(), any(), any(), any(), any(), any(),
                anyBoolean(), anyBoolean());
    }

    private User user(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.test");
        user.setPassword("password");
        return user;
    }

    private Laboratory lab(String name, User manager) {
        Laboratory lab = new Laboratory();
        lab.setLabName(name);
        lab.setLocation("Room");
        lab.setCapacity(10);
        lab.setManager(manager);
        return lab;
    }
}
