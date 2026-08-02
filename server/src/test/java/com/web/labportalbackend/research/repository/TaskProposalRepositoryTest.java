package com.web.labportalbackend.research.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.GroupMemberEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.entity.TaskProposalEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.enums.TaskProposalStatus;
import com.web.labportalbackend.research.enums.TaskType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

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

    @Test
    void laboratoryManagerAssignmentAloneDoesNotGrantStudentVisibility() {
        User actor = user("proposal-scope-actor");
        entityManager.persist(actor);
        User proposer = user("proposal-scope-proposer");
        entityManager.persist(proposer);
        Laboratory lab = new Laboratory();
        lab.setLabName("Proposal scope lab");
        lab.setLocation("Room");
        lab.setCapacity(10);
        lab.setManager(actor);
        entityManager.persist(lab);
        ProjectEntity project = ProjectEntity.builder()
                .title("Proposal scope project")
                .lab(lab)
                .build();
        entityManager.persist(project);
        GroupEntity group = GroupEntity.builder()
                .name("Proposal scope group")
                .lab(lab)
                .project(project)
                .leader(proposer)
                .build();
        entityManager.persist(group);
        TaskProposalEntity proposal = taskProposalRepository.saveAndFlush(
                scopedProposal(proposer.getId(), project.getId(), group.getId()));
        entityManager.clear();

        var page = taskProposalRepository.findVisibleForActor(
                actor.getId(), project.getId(), group.getId(), TaskProposalStatus.PENDING,
                true, false,
                PageRequest.of(0, 20));

        assertTrue(page.isEmpty());
        assertFalse(page.getContent().stream().anyMatch(row -> row.getId().equals(proposal.getId())));

        var managerPage = taskProposalRepository.findVisibleForActor(
                actor.getId(), project.getId(), group.getId(), TaskProposalStatus.PENDING,
                false, true,
                PageRequest.of(0, 20));

        assertEquals(java.util.List.of(proposal.getId()),
                managerPage.getContent().stream().map(TaskProposalEntity::getId).toList());
    }

    @Test
    void ownerVisibilityRequiresStudentRoleAndDoesNotBypassManagerScope() {
        User actor = user("proposal-owner-manager");
        entityManager.persist(actor);
        User foreignManager = user("proposal-foreign-manager");
        entityManager.persist(foreignManager);

        Laboratory foreignLab = new Laboratory();
        foreignLab.setLabName("Foreign proposal lab");
        foreignLab.setLocation("Room");
        foreignLab.setCapacity(10);
        foreignLab.setManager(foreignManager);
        entityManager.persist(foreignLab);
        ProjectEntity foreignProject = ProjectEntity.builder()
                .title("Foreign proposal project")
                .lab(foreignLab)
                .build();
        entityManager.persist(foreignProject);
        GroupEntity foreignGroup = GroupEntity.builder()
                .name("Foreign proposal group")
                .lab(foreignLab)
                .project(foreignProject)
                .leader(foreignManager)
                .build();
        entityManager.persist(foreignGroup);
        TaskProposalEntity ownedProposal = taskProposalRepository.saveAndFlush(
                scopedProposal(actor.getId(), foreignProject.getId(), foreignGroup.getId()));
        entityManager.clear();

        var page = taskProposalRepository.findVisibleForActor(
                actor.getId(), null, null, null, false, true, PageRequest.of(0, 20));

        assertTrue(page.isEmpty());
        assertFalse(page.getContent().stream().anyMatch(row -> row.getId().equals(ownedProposal.getId())));

        var studentPage = taskProposalRepository.findVisibleForActor(
                actor.getId(), null, null, null, true, false, PageRequest.of(0, 20));

        assertEquals(java.util.List.of(ownedProposal.getId()),
                studentPage.getContent().stream().map(TaskProposalEntity::getId).toList());
    }

    @Test
    void visibilityUnionFiltersHiddenTotalsRevocationAndTieBreakRemainAuthoritative() {
        User actor = user("proposal-matrix-actor");
        entityManager.persist(actor);
        User proposer = user("proposal-matrix-proposer");
        entityManager.persist(proposer);
        User otherManager = user("proposal-matrix-other-manager");
        entityManager.persist(otherManager);

        Laboratory managedLab = laboratory("Proposal matrix managed lab", actor);
        Laboratory foreignLab = laboratory("Proposal matrix foreign lab", otherManager);
        ProjectEntity leaderProject = project("Leader project", foreignLab);
        GroupEntity leaderGroup = group("Leader group", foreignLab, leaderProject, proposer);
        GroupMemberEntity leaderMembership = GroupMemberEntity.builder()
                .group(leaderGroup)
                .user(actor)
                .role(GroupRole.LEADER)
                .build();
        entityManager.persist(leaderMembership);
        ProjectEntity managedProject = project("Managed project", managedLab);
        GroupEntity managedGroup = group("Managed group", managedLab, managedProject, proposer);
        ProjectEntity hiddenProject = project("Hidden project", foreignLab);
        GroupEntity hiddenGroup = group("Hidden group", foreignLab, hiddenProject, proposer);

        Instant tiedCreatedAt = Instant.parse("2026-07-30T08:00:00Z");
        TaskProposalEntity own = scopedProposal(
                actor.getId(), hiddenProject.getId(), hiddenGroup.getId(),
                TaskProposalStatus.APPROVED, tiedCreatedAt);
        TaskProposalEntity leaderVisible = scopedProposal(
                proposer.getId(), leaderProject.getId(), leaderGroup.getId(),
                TaskProposalStatus.PENDING, tiedCreatedAt);
        TaskProposalEntity managerVisible = scopedProposal(
                proposer.getId(), managedProject.getId(), managedGroup.getId(),
                TaskProposalStatus.REJECTED, tiedCreatedAt);
        TaskProposalEntity hidden = scopedProposal(
                proposer.getId(), hiddenProject.getId(), hiddenGroup.getId(),
                TaskProposalStatus.PENDING, tiedCreatedAt);
        taskProposalRepository.saveAndFlush(own);
        taskProposalRepository.saveAndFlush(leaderVisible);
        taskProposalRepository.saveAndFlush(managerVisible);
        taskProposalRepository.saveAndFlush(hidden);
        entityManager.clear();

        var firstPage = taskProposalRepository.findVisibleForActor(
                actor.getId(), null, null, null, true, true, PageRequest.of(0, 2));
        var secondPage = taskProposalRepository.findVisibleForActor(
                actor.getId(), null, null, null, true, true, PageRequest.of(1, 2));
        var managerFilter = taskProposalRepository.findVisibleForActor(
                actor.getId(), managedProject.getId(), managedGroup.getId(),
                TaskProposalStatus.REJECTED, true, true, PageRequest.of(0, 20));
        var pendingFilter = taskProposalRepository.findVisibleForActor(
                actor.getId(), null, null, TaskProposalStatus.PENDING,
                true, true, PageRequest.of(0, 20));

        assertEquals(3, firstPage.getTotalElements());
        assertEquals(2, firstPage.getTotalPages());
        assertEquals(List.of(managerVisible.getId(), leaderVisible.getId()),
                firstPage.getContent().stream().map(TaskProposalEntity::getId).toList());
        assertEquals(List.of(own.getId()),
                secondPage.getContent().stream().map(TaskProposalEntity::getId).toList());
        assertEquals(List.of(managerVisible.getId()),
                managerFilter.getContent().stream().map(TaskProposalEntity::getId).toList());
        assertEquals(1, managerFilter.getTotalElements());
        assertEquals(List.of(leaderVisible.getId()),
                pendingFilter.getContent().stream().map(TaskProposalEntity::getId).toList());
        assertFalse(firstPage.getContent().stream().anyMatch(row -> row.getId().equals(hidden.getId())));

        GroupMemberEntity persistedMembership = entityManager.find(
                GroupMemberEntity.class, leaderMembership.getId());
        persistedMembership.setActive(false);
        Laboratory persistedManagedLab = entityManager.find(Laboratory.class, managedLab.getId());
        persistedManagedLab.setManager(otherManager);
        entityManager.flush();
        entityManager.clear();

        var afterRevocation = taskProposalRepository.findVisibleForActor(
                actor.getId(), null, null, null, true, true, PageRequest.of(0, 20));

        assertEquals(List.of(own.getId()),
                afterRevocation.getContent().stream().map(TaskProposalEntity::getId).toList());
        assertEquals(1, afterRevocation.getTotalElements());
    }

    private TaskProposalEntity proposal(TaskType type) {
        return TaskProposalEntity.builder()
                .proposedById(101L)
                .projectId(201L)
                .groupId(301L)
                .payloadJson(payload(type))
                .build();
    }

    private TaskProposalEntity scopedProposal(Long proposerId, Long projectId, Long groupId) {
        return TaskProposalEntity.builder()
                .proposedById(proposerId)
                .projectId(projectId)
                .groupId(groupId)
                .payloadJson("""
                        {"projectId":%d,"groupId":%d,"milestoneId":null,"parentTaskId":null,
                        "title":"Proposal title","description":null,"priority":"MEDIUM","type":"TASK","dueDate":null}
                        """.formatted(projectId, groupId))
                .build();
    }

    private TaskProposalEntity scopedProposal(
            Long proposerId,
            Long projectId,
            Long groupId,
            TaskProposalStatus status,
            Instant createdAt
    ) {
        TaskProposalEntity proposal = scopedProposal(proposerId, projectId, groupId);
        proposal.setStatus(status);
        proposal.setCreatedAt(createdAt);
        proposal.setUpdatedAt(createdAt);
        if (status != TaskProposalStatus.PENDING) {
            proposal.setReviewedById(proposerId);
            proposal.setReviewedAt(createdAt);
            if (status == TaskProposalStatus.REJECTED) {
                proposal.setReason("Needs revision");
            }
        }
        return proposal;
    }

    private Laboratory laboratory(String name, User manager) {
        Laboratory lab = new Laboratory();
        lab.setLabName(name);
        lab.setLocation("Room");
        lab.setCapacity(10);
        lab.setManager(manager);
        entityManager.persist(lab);
        return lab;
    }

    private ProjectEntity project(String title, Laboratory lab) {
        ProjectEntity project = ProjectEntity.builder().title(title).lab(lab).build();
        entityManager.persist(project);
        return project;
    }

    private GroupEntity group(
            String name,
            Laboratory lab,
            ProjectEntity project,
            User leader
    ) {
        GroupEntity group = GroupEntity.builder()
                .name(name)
                .lab(lab)
                .project(project)
                .leader(leader)
                .build();
        entityManager.persist(group);
        return group;
    }

    private User user(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.test");
        user.setPassword("password");
        return user;
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
