package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.MilestoneEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.research.enums.TaskPriority;
import com.web.labportalbackend.research.enums.TaskStatus;
import com.web.labportalbackend.research.enums.TaskType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class TaskRepositoryTest {
    @Autowired TaskRepository taskRepository;
    @Autowired TestEntityManager entityManager;

    private Long projectId;
    private Long otherProjectId;
    private Long leaderGroupId;
    private Long memberGroupId;
    private Long legacyProjectGroupId;
    private Long otherProjectGroupId;
    private Long userId;
    private Long otherUserId;
    private Long milestoneId;
    private Long otherMilestoneId;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        User user = entityManager.persist(user("board-user"));
        User otherUser = entityManager.persist(user("other-user"));
        userId = user.getId();
        otherUserId = otherUser.getId();
        Laboratory lab = new Laboratory();
        lab.setLabName("Board Lab"); lab.setLocation("Room"); lab.setCapacity(10);
        lab = entityManager.persist(lab);
        ProjectEntity project = ProjectEntity.builder().title("Board Project").lab(lab).build();
        project = entityManager.persist(project);
        projectId = project.getId();
        ProjectEntity otherProject = ProjectEntity.builder().title("Other Project").lab(lab).build();
        otherProjectId = entityManager.persist(otherProject).getId();
        MilestoneEntity otherMilestone = MilestoneEntity.builder().project(otherProject).title("Other Milestone").build();
        otherMilestoneId = entityManager.persist(otherMilestone).getId();
        GroupEntity leaderGroup = GroupEntity.builder().name("Leader Group").lab(lab).project(project).leader(user).build();
        leaderGroupId = entityManager.persist(leaderGroup).getId();
        GroupEntity memberGroup = GroupEntity.builder().name("Member Group").lab(lab).project(project).leader(user).build();
        memberGroupId = entityManager.persist(memberGroup).getId();
        GroupEntity legacyProjectGroup = GroupEntity.builder().name("Legacy Project Group").lab(lab).leader(user).build();
        legacyProjectGroupId = entityManager.persist(legacyProjectGroup).getId();
        project.setGroup(legacyProjectGroup);
        GroupEntity otherProjectGroup = GroupEntity.builder().name("Other Project Group").lab(lab).project(otherProject).leader(user).build();
        otherProjectGroupId = entityManager.persist(otherProjectGroup).getId();
        MilestoneEntity milestone = MilestoneEntity.builder().project(project).title("Milestone").build();
        milestoneId = entityManager.persist(milestone).getId();
        entityManager.flush();
    }

    @Test
    void managerQueryUsesDirectFieldsFiltersAndDeterministicOrdering() {
        TaskEntity first = save(task(projectId, leaderGroupId, userId, TaskStatus.TODO, LocalDate.of(2026, 8, 1), "2026-07-01T00:00:00Z"));
        TaskEntity second = save(task(projectId, leaderGroupId, userId, TaskStatus.TODO, LocalDate.of(2026, 8, 1), "2026-07-02T00:00:00Z"));
        TaskEntity undated = save(task(projectId, leaderGroupId, userId, TaskStatus.TODO, null, "2026-06-01T00:00:00Z"));
        TaskEntity otherProject = task(otherProjectId, leaderGroupId, userId, TaskStatus.TODO,
                LocalDate.of(2026, 7, 1), "2026-07-01T00:00:00Z");
        otherProject.setMilestoneId(otherMilestoneId);
        save(otherProject);

        List<TaskEntity> tasks = taskRepository.findBoardTasksForManager(projectId, leaderGroupId, userId, TaskStatus.TODO,
                TaskPriority.HIGH, TaskType.REVIEW, false, false);

        assertEquals(List.of(first.getId(), second.getId(), undated.getId()), tasks.stream().map(TaskEntity::getId).toList());
    }

    @Test
    void boardQueriesIncludeNullMilestoneOnlyWithinExistingAuthorizationScope() {
        TaskEntity managerProjectBacklog = task(projectId, null, null, TaskStatus.BACKLOG,
                TaskPriority.MEDIUM, TaskType.TASK);
        managerProjectBacklog.setMilestoneId(null);
        managerProjectBacklog = save(managerProjectBacklog);
        TaskEntity leaderBacklog = task(projectId, leaderGroupId, otherUserId, TaskStatus.BACKLOG,
                TaskPriority.MEDIUM, TaskType.TASK);
        leaderBacklog.setMilestoneId(null);
        leaderBacklog = save(leaderBacklog);
        TaskEntity memberOwn = task(projectId, memberGroupId, userId, TaskStatus.BACKLOG,
                TaskPriority.MEDIUM, TaskType.TASK);
        memberOwn.setMilestoneId(null);
        memberOwn = save(memberOwn);
        TaskEntity memberOther = task(projectId, memberGroupId, otherUserId, TaskStatus.BACKLOG,
                TaskPriority.MEDIUM, TaskType.TASK);
        memberOther.setMilestoneId(null);
        memberOther = save(memberOther);
        TaskEntity otherProject = task(otherProjectId, null, null, TaskStatus.BACKLOG,
                TaskPriority.MEDIUM, TaskType.TASK);
        otherProject.setMilestoneId(null);
        save(otherProject);

        assertEquals(List.of(managerProjectBacklog.getId(), leaderBacklog.getId(), memberOwn.getId(), memberOther.getId()),
                ids(taskRepository.findBoardTasksForManager(projectId, null, null, TaskStatus.BACKLOG,
                        null, null, true, false)));
        assertEquals(List.of(leaderBacklog.getId(), memberOwn.getId()),
                ids(taskRepository.findBoardTasksForStudent(projectId, List.of(leaderGroupId),
                        List.of(memberGroupId), userId, null, null, TaskStatus.BACKLOG,
                        null, null, true, false)));
    }

    @Test
    void milestoneCompletionChecksIgnoreProjectBacklogWithoutMilestone() {
        TaskEntity projectBacklog = task(projectId, null, null, TaskStatus.BACKLOG,
                TaskPriority.MEDIUM, TaskType.TASK);
        projectBacklog.setMilestoneId(null);
        save(projectBacklog);

        assertEquals(false, taskRepository.existsIncompleteTaskByMilestoneId(milestoneId));
        assertEquals(false, taskRepository.existsTaskWithoutApprovedReportByMilestoneId(milestoneId));
    }

    @Test
    void studentQueryUnionsLeaderAndAssignedMemberScopeWithoutDuplicates() {
        TaskEntity leader = save(task(projectId, leaderGroupId, otherUserId, TaskStatus.TODO, LocalDate.of(2026, 8, 1), "2026-07-01T00:00:00Z"));
        TaskEntity own = save(task(projectId, memberGroupId, userId, TaskStatus.TODO, LocalDate.of(2026, 8, 2), "2026-07-01T00:00:00Z"));
        save(task(projectId, memberGroupId, otherUserId, TaskStatus.TODO, LocalDate.of(2026, 8, 3), "2026-07-01T00:00:00Z"));

        List<TaskEntity> tasks = taskRepository.findBoardTasksForStudent(projectId, List.of(leaderGroupId), List.of(memberGroupId), userId,
                null, null, null, null, null, false, false);

        assertEquals(List.of(leader.getId(), own.getId()), tasks.stream().map(TaskEntity::getId).toList());
    }

    @Test
    void queriesExcludeInactiveDeletedBacklogAndCancelledByDefault() {
        TaskEntity visible = save(task(projectId, leaderGroupId, userId, TaskStatus.TODO, LocalDate.of(2026, 8, 1), "2026-07-01T00:00:00Z"));
        TaskEntity backlog = save(task(projectId, leaderGroupId, userId, TaskStatus.BACKLOG, LocalDate.of(2026, 8, 2), "2026-07-01T00:00:00Z"));
        TaskEntity cancelled = save(task(projectId, leaderGroupId, userId, TaskStatus.CANCELLED, LocalDate.of(2026, 8, 3), "2026-07-01T00:00:00Z"));
        TaskEntity inactive = task(projectId, leaderGroupId, userId, TaskStatus.TODO, null, "2026-07-01T00:00:00Z");
        inactive.setActive(false); save(inactive);
        TaskEntity deleted = task(projectId, leaderGroupId, userId, TaskStatus.TODO, null, "2026-07-01T00:00:00Z");
        deleted.setDeleted(true); save(deleted);

        assertEquals(List.of(visible.getId()), taskRepository.findBoardTasksForManager(projectId, null, null, null, null, null, false, false).stream().map(TaskEntity::getId).toList());
        assertEquals(List.of(visible.getId(), backlog.getId(), cancelled.getId()), taskRepository.findBoardTasksForManager(projectId, null, null, null, null, null, true, true).stream().map(TaskEntity::getId).toList());
    }

    @Test
    void managerScalarFiltersReturnMatchingRowsAndExcludeRowsDifferingOnlyByFilter() {
        TaskEntity match = save(task(projectId, leaderGroupId, userId, TaskStatus.TODO,
                TaskPriority.HIGH, TaskType.REVIEW));
        TaskEntity memberMatch = save(task(projectId, memberGroupId, userId, TaskStatus.TODO, TaskPriority.HIGH, TaskType.REVIEW));
        TaskEntity otherAssignee = save(task(projectId, leaderGroupId, otherUserId, TaskStatus.TODO, TaskPriority.HIGH, TaskType.REVIEW));
        TaskEntity inReview = save(task(projectId, leaderGroupId, userId, TaskStatus.IN_REVIEW, TaskPriority.HIGH, TaskType.REVIEW));
        TaskEntity lowPriority = save(task(projectId, leaderGroupId, userId, TaskStatus.TODO, TaskPriority.LOW, TaskType.REVIEW));
        TaskEntity taskType = save(task(projectId, leaderGroupId, userId, TaskStatus.TODO, TaskPriority.HIGH, TaskType.TASK));

        assertEquals(List.of(match.getId()), ids(taskRepository.findBoardTasksForManager(projectId, leaderGroupId,
                userId, TaskStatus.TODO, TaskPriority.HIGH, TaskType.REVIEW, false, false)));
        assertEquals(List.of(match.getId(), otherAssignee.getId(), inReview.getId()), ids(taskRepository.findBoardTasksForManager(projectId, leaderGroupId,
                null, null, TaskPriority.HIGH, TaskType.REVIEW, false, false)));
        assertEquals(List.of(match.getId(), memberMatch.getId(), lowPriority.getId(), taskType.getId()), ids(taskRepository.findBoardTasksForManager(projectId, null,
                userId, TaskStatus.TODO, null, null, false, false)));
    }

    @Test
    void studentScalarFiltersRemainInsideLeaderAndMemberAuthorizationScope() {
        TaskEntity leaderMatch = save(task(projectId, leaderGroupId, otherUserId, TaskStatus.TODO,
                TaskPriority.HIGH, TaskType.REVIEW));
        TaskEntity memberOwn = save(task(projectId, memberGroupId, userId, TaskStatus.TODO,
                TaskPriority.HIGH, TaskType.REVIEW));
        save(task(projectId, memberGroupId, otherUserId, TaskStatus.TODO, TaskPriority.HIGH, TaskType.REVIEW));
        TaskEntity otherProject = task(otherProjectId, otherProjectGroupId, userId, TaskStatus.TODO,
                TaskPriority.HIGH, TaskType.REVIEW);
        otherProject.setMilestoneId(otherMilestoneId);
        save(otherProject);

        assertEquals(List.of(leaderMatch.getId()), ids(taskRepository.findBoardTasksForStudent(projectId,
                List.of(leaderGroupId), List.of(memberGroupId), userId, leaderGroupId, null, TaskStatus.TODO,
                TaskPriority.HIGH, TaskType.REVIEW, false, false)));
        assertEquals(List.of(memberOwn.getId()), ids(taskRepository.findBoardTasksForStudent(projectId,
                List.of(leaderGroupId), List.of(memberGroupId), userId, null, userId, TaskStatus.TODO,
                null, null, false, false)));
    }

    @Test
    void explicitStatusAndIncludeFlagsSelectOnlyDesignedStatuses() {
        TaskEntity todo = save(task(projectId, leaderGroupId, userId, TaskStatus.TODO,
                TaskPriority.HIGH, TaskType.REVIEW));
        TaskEntity backlog = save(task(projectId, leaderGroupId, userId, TaskStatus.BACKLOG,
                TaskPriority.HIGH, TaskType.REVIEW));
        TaskEntity cancelled = save(task(projectId, leaderGroupId, userId, TaskStatus.CANCELLED,
                TaskPriority.HIGH, TaskType.REVIEW));

        assertEquals(List.of(todo.getId()), ids(taskRepository.findBoardTasksForManager(projectId, null, null,
                null, null, null, false, false)));
        assertEquals(List.of(todo.getId(), backlog.getId(), cancelled.getId()).stream().sorted().toList(),
                ids(taskRepository.findBoardTasksForManager(projectId, null, null, null, null, null, true, true))
                        .stream().sorted().toList());
        assertEquals(List.of(backlog.getId()), ids(taskRepository.findBoardTasksForManager(projectId, null, null,
                TaskStatus.BACKLOG, null, null, true, false)));
    }

    @Test
    void queriesExcludeMilestoneProjectMismatchCrossLabAndInactiveOrDeletedOwnership() {
        TaskEntity valid = save(task(projectId, leaderGroupId, userId, TaskStatus.TODO,
                TaskPriority.HIGH, TaskType.REVIEW));

        MilestoneEntity otherMilestone = entityManager.persist(MilestoneEntity.builder()
                .project(entityManager.find(ProjectEntity.class, otherProjectId)).title("Other milestone").build());
        TaskEntity milestoneMismatch = task(projectId, leaderGroupId, userId, TaskStatus.TODO,
                TaskPriority.HIGH, TaskType.REVIEW);
        milestoneMismatch.setMilestoneId(otherMilestone.getId());
        save(milestoneMismatch);

        Laboratory otherLab = new Laboratory();
        otherLab.setLabName("Cross Lab"); otherLab.setLocation("Room"); otherLab.setCapacity(10);
        otherLab = entityManager.persist(otherLab);
        GroupEntity crossLab = entityManager.persist(GroupEntity.builder().name("Cross Lab Group").lab(otherLab)
                .project(entityManager.find(ProjectEntity.class, projectId)).leader(entityManager.find(User.class, userId)).build());
        save(task(projectId, crossLab.getId(), userId, TaskStatus.TODO, TaskPriority.HIGH, TaskType.REVIEW));

        GroupEntity inactive = entityManager.persist(GroupEntity.builder().name("Inactive Group")
                .lab(entityManager.find(ProjectEntity.class, projectId).getLab())
                .project(entityManager.find(ProjectEntity.class, projectId)).leader(entityManager.find(User.class, userId)).build());
        inactive.setActive(false);
        save(task(projectId, inactive.getId(), userId, TaskStatus.TODO, TaskPriority.HIGH, TaskType.REVIEW));

        GroupEntity deleted = entityManager.persist(GroupEntity.builder().name("Deleted Group")
                .lab(entityManager.find(ProjectEntity.class, projectId).getLab())
                .project(entityManager.find(ProjectEntity.class, projectId)).leader(entityManager.find(User.class, userId)).build());
        deleted.setDeleted(true);
        save(task(projectId, deleted.getId(), userId, TaskStatus.TODO, TaskPriority.HIGH, TaskType.REVIEW));
        entityManager.flush();

        assertEquals(List.of(valid.getId()), ids(taskRepository.findBoardTasksForManager(projectId, null, null,
                null, null, null, false, false)));
    }

    @Test
    void queriesExcludeInactiveAndDeletedRequestedProject() {
        save(task(projectId, leaderGroupId, userId, TaskStatus.TODO, TaskPriority.HIGH, TaskType.REVIEW));
        ProjectEntity project = entityManager.find(ProjectEntity.class, projectId);
        project.setActive(false);
        entityManager.flush();
        assertEquals(List.of(), ids(taskRepository.findBoardTasksForManager(projectId, null, null,
                null, null, null, false, false)));

        project.setActive(true);
        project.setDeleted(true);
        entityManager.flush();
        assertEquals(List.of(), ids(taskRepository.findBoardTasksForStudent(projectId, List.of(leaderGroupId),
                List.of(memberGroupId), userId, null, null, null, null, null, false, false)));
    }

    @Test
    void managerQueryAcceptsProjectGroupOwnershipAndRejectsInconsistentGroupOwnership() {
        TaskEntity visible = save(task(projectId, legacyProjectGroupId, userId, TaskStatus.TODO,
                LocalDate.of(2026, 8, 1), "2026-07-01T00:00:00Z"));
        save(task(projectId, otherProjectGroupId, userId, TaskStatus.TODO,
                LocalDate.of(2026, 8, 2), "2026-07-01T00:00:00Z"));

        assertEquals(List.of(visible.getId()), taskRepository.findBoardTasksForManager(projectId, null, null,
                null, null, null, false, false).stream().map(TaskEntity::getId).toList());
    }

    private TaskEntity save(TaskEntity task) { return taskRepository.saveAndFlush(task); }

    private TaskEntity task(Long projectId, Long groupId, Long assigneeId, TaskStatus status, LocalDate dueDate, String createdAt) {
        return task(projectId, groupId, assigneeId, status, TaskPriority.HIGH, TaskType.REVIEW, dueDate, createdAt);
    }

    private TaskEntity task(Long projectId, Long groupId, Long assigneeId, TaskStatus status,
                            TaskPriority priority, TaskType type) {
        return task(projectId, groupId, assigneeId, status, priority, type, LocalDate.of(2026, 8, 1), "2026-07-01T00:00:00Z");
    }

    private TaskEntity task(Long projectId, Long groupId, Long assigneeId, TaskStatus status,
                            TaskPriority priority, TaskType type, LocalDate dueDate, String createdAt) {
        TaskEntity task = TaskEntity.builder().projectId(projectId).groupId(groupId).milestoneId(milestoneId).assigneeId(assigneeId)
                .title("Board task").status(status).priority(priority).type(type).dueDate(dueDate).build();
        task.setCreatedAt(Instant.parse(createdAt)); task.setUpdatedAt(Instant.parse(createdAt));
        return task;
    }

    private List<Long> ids(List<TaskEntity> tasks) {
        return tasks.stream().map(TaskEntity::getId).toList();
    }

    private User user(String username) {
        User user = new User();
        user.setUsername(username); user.setEmail(username + "@example.test"); user.setPassword("password");
        return user;
    }
}
