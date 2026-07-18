package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.MilestoneEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.enums.TaskPriority;
import com.web.labportalbackend.research.enums.TaskStatus;
import com.web.labportalbackend.research.enums.TaskType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class TaskBacklogRepositoryTest {
    @Autowired TaskRepository taskRepository;
    @Autowired TestEntityManager entityManager;

    private User user;
    private User otherUser;
    private Laboratory lab;
    private ProjectEntity project;
    private ProjectEntity otherProject;
    private GroupEntity leaderGroup;
    private GroupEntity memberGroup;
    private GroupEntity legacyGroup;
    private MilestoneEntity milestone;
    private MilestoneEntity otherMilestone;

    @BeforeEach
    void setUp() {
        user = entityManager.persist(user("backlog-user"));
        otherUser = entityManager.persist(user("backlog-other"));
        lab = entityManager.persist(lab("Backlog Lab"));
        project = entityManager.persist(ProjectEntity.builder().title("Backlog Project").lab(lab).build());
        otherProject = entityManager.persist(ProjectEntity.builder().title("Other Project").lab(lab).build());
        leaderGroup = entityManager.persist(group("Leader Group", lab, project));
        memberGroup = entityManager.persist(group("Member Group", lab, project));
        legacyGroup = entityManager.persist(GroupEntity.builder().name("Legacy Group").lab(lab).leader(user).build());
        project.setGroup(legacyGroup);
        milestone = entityManager.persist(MilestoneEntity.builder().project(project).title("Milestone").build());
        otherMilestone = entityManager.persist(MilestoneEntity.builder().project(otherProject).title("Other Milestone").build());
        entityManager.flush();
    }

    @Test
    void managerReturnsOnlyValidBacklogIncludingUngroupedTasks() {
        TaskEntity grouped = save(task(project, leaderGroup, milestone, user, TaskStatus.BACKLOG));
        TaskEntity ungrouped = save(task(project, null, milestone, user, TaskStatus.BACKLOG));
        TaskEntity legacy = save(task(project, legacyGroup, milestone, user, TaskStatus.BACKLOG));
        save(task(project, leaderGroup, milestone, user, TaskStatus.TODO));
        save(task(project, leaderGroup, milestone, user, TaskStatus.CANCELLED));
        TaskEntity inactive = task(project, leaderGroup, milestone, user, TaskStatus.BACKLOG);
        inactive.setActive(false);
        save(inactive);
        TaskEntity deleted = task(project, leaderGroup, milestone, user, TaskStatus.BACKLOG);
        deleted.setDeleted(true);
        save(deleted);
        save(task(otherProject, null, otherMilestone, user, TaskStatus.BACKLOG));

        Page<TaskEntity> page = taskRepository.findBacklogTasksForManager(project.getId(), PageRequest.of(0, 20));

        assertEquals(List.of(grouped.getId(), ungrouped.getId(), legacy.getId()).stream().sorted().toList(),
                ids(page).stream().sorted().toList());
        assertEquals(3, page.getTotalElements());
        assertEquals(1, page.getTotalPages());
    }

    @Test
    void managerExcludesInvalidGroupAndMilestoneOwnership() {
        TaskEntity valid = save(task(project, leaderGroup, milestone, user, TaskStatus.BACKLOG));
        save(task(project, leaderGroup, otherMilestone, user, TaskStatus.BACKLOG));

        Laboratory otherLab = entityManager.persist(lab("Other Lab"));
        GroupEntity crossLab = entityManager.persist(group("Cross Lab", otherLab, project));
        save(task(project, crossLab, milestone, user, TaskStatus.BACKLOG));

        GroupEntity otherProjectGroup = entityManager.persist(group("Other Project Group", lab, otherProject));
        save(task(project, otherProjectGroup, milestone, user, TaskStatus.BACKLOG));

        GroupEntity inactive = entityManager.persist(group("Inactive Group", lab, project));
        inactive.setActive(false);
        save(task(project, inactive, milestone, user, TaskStatus.BACKLOG));

        GroupEntity deleted = entityManager.persist(group("Deleted Group", lab, project));
        deleted.setDeleted(true);
        save(task(project, deleted, milestone, user, TaskStatus.BACKLOG));
        entityManager.flush();

        assertEquals(List.of(valid.getId()), ids(taskRepository.findBacklogTasksForManager(
                project.getId(), PageRequest.of(0, 20))));
    }

    @Test
    void inactiveOrDeletedRequestedProjectProducesNoBacklog() {
        save(task(project, leaderGroup, milestone, user, TaskStatus.BACKLOG));
        project.setActive(false);
        entityManager.flush();
        assertTrue(taskRepository.findBacklogTasksForManager(project.getId(), PageRequest.of(0, 20)).isEmpty());

        project.setActive(true);
        project.setDeleted(true);
        entityManager.flush();
        assertTrue(taskRepository.findBacklogTasksForStudent(project.getId(), List.of(leaderGroup.getId()),
                List.of(memberGroup.getId()), user.getId(), PageRequest.of(0, 20)).isEmpty());
    }

    @Test
    void studentUnionsLeaderAndOwnMemberScopeWithoutUngroupedOrDuplicates() {
        TaskEntity leader = save(task(project, leaderGroup, milestone, otherUser, TaskStatus.BACKLOG));
        TaskEntity leaderOwn = save(task(project, leaderGroup, milestone, user, TaskStatus.BACKLOG));
        TaskEntity memberOwn = save(task(project, memberGroup, milestone, user, TaskStatus.BACKLOG));
        save(task(project, memberGroup, milestone, otherUser, TaskStatus.BACKLOG));
        save(task(project, null, milestone, user, TaskStatus.BACKLOG));
        save(task(project, leaderGroup, milestone, user, TaskStatus.TODO));

        Page<TaskEntity> page = taskRepository.findBacklogTasksForStudent(project.getId(),
                List.of(leaderGroup.getId()), List.of(leaderGroup.getId(), memberGroup.getId()), user.getId(),
                PageRequest.of(0, 20));

        assertEquals(List.of(leader.getId(), leaderOwn.getId(), memberOwn.getId()).stream().sorted().toList(),
                ids(page).stream().sorted().toList());
        assertEquals(3, page.getTotalElements());
    }

    @Test
    void studentScopeExcludesInactiveDeletedAndUnrelatedGroups() {
        TaskEntity valid = save(task(project, leaderGroup, milestone, otherUser, TaskStatus.BACKLOG));
        GroupEntity unrelated = entityManager.persist(group("Unrelated", lab, project));
        save(task(project, unrelated, milestone, user, TaskStatus.BACKLOG));
        GroupEntity inactive = entityManager.persist(group("Inactive Student Group", lab, project));
        inactive.setActive(false);
        save(task(project, inactive, milestone, user, TaskStatus.BACKLOG));
        GroupEntity deleted = entityManager.persist(group("Deleted Student Group", lab, project));
        deleted.setDeleted(true);
        save(task(project, deleted, milestone, user, TaskStatus.BACKLOG));
        entityManager.flush();

        Page<TaskEntity> page = taskRepository.findBacklogTasksForStudent(project.getId(),
                List.of(leaderGroup.getId(), inactive.getId(), deleted.getId()), List.of(-1L),
                user.getId(), PageRequest.of(0, 20));

        assertEquals(List.of(valid.getId()), ids(page));
    }

    @Test
    void fixedOrderingUsesDueDateThenCreatedAtThenIdWithNullDatesLast() {
        TaskEntity laterDue = save(task(project, leaderGroup, milestone, user, TaskStatus.BACKLOG,
                LocalDate.of(2026, 9, 2), "2026-07-01T00:00:00Z"));
        TaskEntity firstTie = save(task(project, leaderGroup, milestone, user, TaskStatus.BACKLOG,
                LocalDate.of(2026, 9, 1), "2026-07-01T00:00:00Z"));
        TaskEntity secondTie = save(task(project, leaderGroup, milestone, user, TaskStatus.BACKLOG,
                LocalDate.of(2026, 9, 1), "2026-07-01T00:00:00Z"));
        TaskEntity createdLater = save(task(project, leaderGroup, milestone, user, TaskStatus.BACKLOG,
                LocalDate.of(2026, 9, 1), "2026-07-02T00:00:00Z"));
        TaskEntity undated = save(task(project, leaderGroup, milestone, user, TaskStatus.BACKLOG,
                null, "2026-06-01T00:00:00Z"));

        Page<TaskEntity> page = taskRepository.findBacklogTasksForManager(project.getId(), PageRequest.of(0, 20));

        assertEquals(List.of(firstTie.getId(), secondTie.getId(), createdLater.getId(), laterDue.getId(), undated.getId()),
                ids(page));
    }

    @Test
    void paginationUsesStableDatabaseOrderAndScopedMetadataAcrossPages() {
        TaskEntity first = save(task(project, leaderGroup, milestone, user, TaskStatus.BACKLOG,
                LocalDate.of(2026, 9, 1), "2026-07-01T00:00:00Z"));
        TaskEntity second = save(task(project, leaderGroup, milestone, user, TaskStatus.BACKLOG,
                LocalDate.of(2026, 9, 2), "2026-07-01T00:00:00Z"));
        TaskEntity third = save(task(project, leaderGroup, milestone, user, TaskStatus.BACKLOG,
                LocalDate.of(2026, 9, 3), "2026-07-01T00:00:00Z"));
        save(task(project, leaderGroup, milestone, user, TaskStatus.TODO));

        Page<TaskEntity> firstPage = taskRepository.findBacklogTasksForManager(project.getId(), PageRequest.of(0, 2));
        Page<TaskEntity> secondPage = taskRepository.findBacklogTasksForManager(project.getId(), PageRequest.of(1, 2));

        assertEquals(List.of(first.getId(), second.getId()), ids(firstPage));
        assertEquals(List.of(third.getId()), ids(secondPage));
        assertEquals(3, firstPage.getTotalElements());
        assertEquals(2, firstPage.getTotalPages());
        assertEquals(3, secondPage.getTotalElements());
        assertEquals(2, secondPage.getTotalPages());
    }

    private TaskEntity save(TaskEntity task) {
        return taskRepository.saveAndFlush(task);
    }

    private TaskEntity task(ProjectEntity taskProject, GroupEntity group, MilestoneEntity taskMilestone,
                            User assignee, TaskStatus status) {
        return task(taskProject, group, taskMilestone, assignee, status,
                LocalDate.of(2026, 9, 1), "2026-07-01T00:00:00Z");
    }

    private TaskEntity task(ProjectEntity taskProject, GroupEntity group, MilestoneEntity taskMilestone,
                            User assignee, TaskStatus status, LocalDate dueDate, String createdAt) {
        TaskEntity task = TaskEntity.builder()
                .projectId(taskProject.getId())
                .groupId(group == null ? null : group.getId())
                .milestoneId(taskMilestone.getId())
                .assigneeId(assignee.getId())
                .title("Backlog task")
                .status(status)
                .priority(TaskPriority.HIGH)
                .type(TaskType.TASK)
                .dueDate(dueDate)
                .build();
        task.setCreatedAt(Instant.parse(createdAt));
        task.setUpdatedAt(Instant.parse(createdAt));
        return task;
    }

    private List<Long> ids(Page<TaskEntity> page) {
        return page.getContent().stream().map(TaskEntity::getId).toList();
    }

    private GroupEntity group(String name, Laboratory groupLab, ProjectEntity groupProject) {
        return GroupEntity.builder().name(name).lab(groupLab).project(groupProject).leader(user).build();
    }

    private Laboratory lab(String name) {
        Laboratory laboratory = new Laboratory();
        laboratory.setLabName(name);
        laboratory.setLocation("Room");
        laboratory.setCapacity(10);
        return laboratory;
    }

    private User user(String username) {
        User created = new User();
        created.setUsername(username);
        created.setEmail(username + "@example.test");
        created.setPassword("password");
        return created;
    }
}
