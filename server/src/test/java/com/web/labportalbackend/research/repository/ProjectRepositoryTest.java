package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.enums.UserStatus;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.enums.ProjectStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProjectRepositoryTest {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LaboratoryRepository laboratoryRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findByGroupId_returnsProjectsForGroup() {
        GroupEntity group = createGroup("Project Query Group");
        ProjectEntity project = ProjectEntity.builder()
                .group(group)
                .lab(group.getLab())
                .title("Stable Query Project")
                .description("Repository query verification")
                .status(ProjectStatus.ONGOING)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 3, 1))
                .build();
        projectRepository.saveAndFlush(project);

        List<ProjectEntity> projects = projectRepository.findByGroupId(group.getId());

        assertEquals(1, projects.size());
        assertEquals("Stable Query Project", projects.get(0).getTitle());
        assertEquals(group.getId(), projects.get(0).getGroup().getId());
    }

    @Test
    void project_foreignKeyRejectsUnknownGroup() {
        assertThrows(PersistenceException.class, () -> {
            entityManager.createNativeQuery("""
                    INSERT INTO projects (group_id, title, status, start_date, created_at, updated_at, active, deleted)
                    VALUES (999999, 'Invalid FK Project', 'PLANNED', CURRENT_DATE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), true, false)
                    """).executeUpdate();
            entityManager.flush();
        });
    }

    private GroupEntity createGroup(String groupName) {
        User leader = new User();
        leader.setUsername(groupName.toLowerCase().replace(" ", "_") + "_leader");
        leader.setEmail(groupName.toLowerCase().replace(" ", ".") + ".leader@test.com");
        leader.setPassword("hashed_password");
        leader.setStatus(UserStatus.ACTIVE);
        leader = userRepository.saveAndFlush(leader);

        Laboratory lab = new Laboratory();
        lab.setLabName(groupName + " Lab");
        lab.setLocation("Room P1");
        lab.setCapacity(8);
        lab = laboratoryRepository.saveAndFlush(lab);

        GroupEntity group = GroupEntity.builder()
                .lab(lab)
                .name(groupName)
                .leader(leader)
                .build();
        return groupRepository.saveAndFlush(group);
    }
}
