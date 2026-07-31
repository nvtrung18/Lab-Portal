package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.common.enums.UserStatus;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.GroupMemberEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class GroupMemberRepositoryTest {
    @Autowired GroupMemberRepository groupMemberRepository;
    @Autowired TestEntityManager entityManager;

    @Test
    void leaderQueryReturnsActiveSameProjectSameLabLeaderMembership() {
        Fixture fixture = fixture(GroupRole.LEADER);
        assertGroups(fixture, GroupRole.LEADER, fixture.group().getId());
    }

    @Test
    void memberQueryReturnsActiveSameProjectSameLabMemberMembership() {
        Fixture fixture = fixture(GroupRole.MEMBER);
        assertGroups(fixture, GroupRole.MEMBER, fixture.group().getId());
    }

    @Test
    void leaderQueryExcludesMemberMembership() {
        assertGroups(fixture(GroupRole.MEMBER), GroupRole.LEADER);
    }

    @Test
    void memberQueryExcludesLeaderMembership() {
        assertGroups(fixture(GroupRole.LEADER), GroupRole.MEMBER);
    }

    @Test
    void queryExcludesInactiveMembership() {
        Fixture fixture = fixture(GroupRole.MEMBER);
        fixture.membership().setActive(false);
        assertGroups(fixture, GroupRole.MEMBER);
    }

    @Test
    void queryExcludesDeletedMembership() {
        Fixture fixture = fixture(GroupRole.MEMBER);
        fixture.membership().setDeleted(true);
        assertGroups(fixture, GroupRole.MEMBER);
    }

    @Test
    void queryExcludesInactiveGroup() {
        Fixture fixture = fixture(GroupRole.MEMBER);
        fixture.group().setActive(false);
        assertGroups(fixture, GroupRole.MEMBER);
    }

    @Test
    void queryExcludesDeletedGroup() {
        Fixture fixture = fixture(GroupRole.MEMBER);
        fixture.group().setDeleted(true);
        assertGroups(fixture, GroupRole.MEMBER);
    }

    @Test
    void queryExcludesInactiveProject() {
        Fixture fixture = fixture(GroupRole.MEMBER);
        fixture.project().setActive(false);
        assertGroups(fixture, GroupRole.MEMBER);
    }

    @Test
    void queryExcludesDeletedProject() {
        Fixture fixture = fixture(GroupRole.MEMBER);
        fixture.project().setDeleted(true);
        assertGroups(fixture, GroupRole.MEMBER);
    }

    @Test
    void queryExcludesAnotherProjectGroup() {
        Fixture fixture = fixture(GroupRole.MEMBER);
        ProjectEntity requested = entityManager.persist(ProjectEntity.builder()
                .title("Requested project").lab(fixture.project().getLab()).build());
        assertEquals(List.of(), query(requested.getId(), fixture.user().getId(), GroupRole.MEMBER));
    }

    @Test
    void queryExcludesCrossLabInconsistentOwnership() {
        Fixture fixture = fixture(GroupRole.MEMBER);
        fixture.group().setLab(entityManager.persist(lab("Other lab")));
        assertGroups(fixture, GroupRole.MEMBER);
    }

    @Test
    void queryExcludesInactiveAssignee() {
        Fixture fixture = fixture(GroupRole.MEMBER);
        fixture.user().setActive(false);
        assertGroups(fixture, GroupRole.MEMBER);
    }

    @Test
    void queryExcludesDeletedAssignee() {
        Fixture fixture = fixture(GroupRole.MEMBER);
        fixture.user().setDeleted(true);
        assertGroups(fixture, GroupRole.MEMBER);
    }

    @Test
    void proposalNotificationLeaderQueryEnforcesExactActiveUsableLeaderScope() {
        Laboratory lab = entityManager.persist(lab("Proposal notification lab"));
        ProjectEntity project = entityManager.persist(ProjectEntity.builder()
                .title("Proposal notification project").lab(lab).build());

        User staleDenormalizedLeader = entityManager.persist(user("proposal-stale-leader"));
        GroupEntity targetGroup = entityManager.persist(GroupEntity.builder()
                .name("Proposal target group")
                .lab(lab)
                .project(project)
                .leader(staleDenormalizedLeader)
                .build());
        persistMembership(targetGroup, staleDenormalizedLeader, GroupRole.MEMBER);

        User eligibleOne = entityManager.persist(user("proposal-eligible-one"));
        User eligibleTwo = entityManager.persist(user("proposal-eligible-two"));
        persistMembership(targetGroup, eligibleOne, GroupRole.LEADER);
        persistMembership(targetGroup, eligibleTwo, GroupRole.LEADER);

        User inactiveMembershipUser = entityManager.persist(user("proposal-inactive-membership"));
        GroupMemberEntity inactiveMembership =
                persistMembership(targetGroup, inactiveMembershipUser, GroupRole.LEADER);
        inactiveMembership.setActive(false);

        User deletedMembershipUser = entityManager.persist(user("proposal-deleted-membership"));
        GroupMemberEntity deletedMembership =
                persistMembership(targetGroup, deletedMembershipUser, GroupRole.LEADER);
        deletedMembership.setDeleted(true);

        User inactiveUser = entityManager.persist(user("proposal-inactive-user"));
        inactiveUser.setActive(false);
        persistMembership(targetGroup, inactiveUser, GroupRole.LEADER);

        User deletedUser = entityManager.persist(user("proposal-deleted-user"));
        deletedUser.setDeleted(true);
        persistMembership(targetGroup, deletedUser, GroupRole.LEADER);

        User suspendedUser = entityManager.persist(user("proposal-suspended-user"));
        suspendedUser.setStatus(UserStatus.SUSPENDED);
        persistMembership(targetGroup, suspendedUser, GroupRole.LEADER);

        User crossGroupLeader = entityManager.persist(user("proposal-cross-group"));
        GroupEntity otherGroup = entityManager.persist(GroupEntity.builder()
                .name("Proposal other group")
                .lab(lab)
                .project(project)
                .leader(crossGroupLeader)
                .build());
        persistMembership(otherGroup, crossGroupLeader, GroupRole.LEADER);

        User inactiveGroupLeader = entityManager.persist(user("proposal-inactive-group"));
        GroupEntity inactiveGroup = entityManager.persist(GroupEntity.builder()
                .name("Proposal inactive group")
                .lab(lab)
                .project(project)
                .leader(inactiveGroupLeader)
                .build());
        inactiveGroup.setActive(false);
        persistMembership(inactiveGroup, inactiveGroupLeader, GroupRole.LEADER);

        User deletedGroupLeader = entityManager.persist(user("proposal-deleted-group"));
        GroupEntity deletedGroup = entityManager.persist(GroupEntity.builder()
                .name("Proposal deleted group")
                .lab(lab)
                .project(project)
                .leader(deletedGroupLeader)
                .build());
        deletedGroup.setDeleted(true);
        persistMembership(deletedGroup, deletedGroupLeader, GroupRole.LEADER);

        entityManager.flush();
        entityManager.clear();

        List<Long> actual =
                groupMemberRepository.findActiveLeaderUserIdsForProposalNotification(targetGroup.getId());

        assertEquals(Set.of(eligibleOne.getId(), eligibleTwo.getId()), Set.copyOf(actual));
        assertEquals(actual.size(), Set.copyOf(actual).size());
    }

    @Test
    void proposalNotificationLeaderQueryExcludesOtherwiseEligibleLeaderWhenExactGroupIsInactive() {
        GroupEntity targetGroup = persistProposalNotificationGroupWithEligibleLeader("inactive");
        targetGroup.setActive(false);

        assertProposalNotificationLeaderIds(targetGroup);
    }

    @Test
    void proposalNotificationLeaderQueryExcludesOtherwiseEligibleLeaderWhenExactGroupIsDeleted() {
        GroupEntity targetGroup = persistProposalNotificationGroupWithEligibleLeader("deleted");
        targetGroup.setDeleted(true);

        assertProposalNotificationLeaderIds(targetGroup);
    }

    private Fixture fixture(GroupRole role) {
        User user = entityManager.persist(user("assignee"));
        Laboratory lab = entityManager.persist(lab("Membership lab"));
        ProjectEntity project = entityManager.persist(ProjectEntity.builder()
                .title("Membership project").lab(lab).build());
        GroupEntity group = entityManager.persist(GroupEntity.builder()
                .name("Membership group").lab(lab).project(project).leader(user).build());
        GroupMemberEntity membership = entityManager.persist(GroupMemberEntity.builder()
                .group(group).user(user).role(role).build());
        entityManager.flush();
        return new Fixture(user, project, group, membership);
    }

    private void assertGroups(Fixture fixture, GroupRole role, Long... expectedIds) {
        entityManager.flush();
        entityManager.clear();
        assertEquals(List.of(expectedIds), query(fixture.project().getId(), fixture.user().getId(), role));
    }

    private List<Long> query(Long projectId, Long userId, GroupRole role) {
        return groupMemberRepository.findActiveGroupIdsByProjectIdAndUserIdAndRole(projectId, userId, role);
    }

    private User user(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.test");
        user.setPassword("password");
        return user;
    }

    private Laboratory lab(String name) {
        Laboratory lab = new Laboratory();
        lab.setLabName(name);
        lab.setLocation("Room");
        lab.setCapacity(10);
        return lab;
    }

    private GroupMemberEntity persistMembership(
            GroupEntity group,
            User user,
            GroupRole role
    ) {
        return entityManager.persist(GroupMemberEntity.builder()
                .group(group)
                .user(user)
                .role(role)
                .build());
    }

    private GroupEntity persistProposalNotificationGroupWithEligibleLeader(String suffix) {
        Laboratory lab = entityManager.persist(lab("Proposal " + suffix + " group lab"));
        ProjectEntity project = entityManager.persist(ProjectEntity.builder()
                .title("Proposal " + suffix + " group project")
                .lab(lab)
                .build());
        User leader = entityManager.persist(user("proposal-" + suffix + "-group-leader"));
        GroupEntity group = entityManager.persist(GroupEntity.builder()
                .name("Proposal " + suffix + " target group")
                .lab(lab)
                .project(project)
                .leader(leader)
                .build());
        persistMembership(group, leader, GroupRole.LEADER);
        return group;
    }

    private void assertProposalNotificationLeaderIds(GroupEntity group, Long... expectedIds) {
        Long groupId = group.getId();
        entityManager.flush();
        entityManager.clear();

        assertEquals(
                List.of(expectedIds),
                groupMemberRepository.findActiveLeaderUserIdsForProposalNotification(groupId)
        );
    }

    private record Fixture(User user, ProjectEntity project, GroupEntity group, GroupMemberEntity membership) {}
}
