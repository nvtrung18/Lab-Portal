package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.enums.UserStatus;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.GroupMemberEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class GroupMemberRepositoryConstraintTest {

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LaboratoryRepository laboratoryRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void groupMember_uniqueConstraintRejectsDuplicateUserInSameGroup() {
        Fixture fixture = createFixture();

        GroupMemberEntity first = GroupMemberEntity.builder()
                .group(fixture.group())
                .user(fixture.member())
                .role(GroupRole.MEMBER)
                .joinedAt(Instant.now())
                .build();
        groupMemberRepository.saveAndFlush(first);

        GroupMemberEntity duplicate = GroupMemberEntity.builder()
                .group(fixture.group())
                .user(fixture.member())
                .role(GroupRole.MEMBER)
                .joinedAt(Instant.now())
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> groupMemberRepository.saveAndFlush(duplicate));
    }

    @Test
    void groupMember_foreignKeyRejectsUnknownGroupAndUser() {
        assertThrows(PersistenceException.class, () -> {
            entityManager.createNativeQuery("""
                    INSERT INTO group_members (group_id, user_id, role, joined_at, created_at, updated_at, active, deleted)
                    VALUES (999999, 999998, 'MEMBER', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), true, false)
                    """).executeUpdate();
            entityManager.flush();
        });
    }

    private Fixture createFixture() {
        User leader = createUser("leader_group_test", "leader.group@test.com");
        User member = createUser("member_group_test", "member.group@test.com");

        Laboratory lab = new Laboratory();
        lab.setLabName("Group Constraint Lab");
        lab.setLocation("Room G1");
        lab.setCapacity(10);
        lab = laboratoryRepository.saveAndFlush(lab);

        GroupEntity group = GroupEntity.builder()
                .lab(lab)
                .name("Constraint Group")
                .leader(leader)
                .build();
        group = groupRepository.saveAndFlush(group);

        return new Fixture(group, member);
    }

    private User createUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword("hashed_password");
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    private record Fixture(GroupEntity group, User member) {
    }
}
