package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.enums.UserStatus;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.MilestoneEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.enums.MilestoneStatus;
import com.web.labportalbackend.research.enums.ProjectStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MilestoneRepositoryTest {

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LaboratoryRepository laboratoryRepository;

    @Test
    void findByProjectIdOrderByStartDateAsc_returnsTimelineSortedByStartDate() {
        ProjectEntity project = createProject();
        milestoneRepository.saveAndFlush(milestone(project, "Prototype", LocalDate.of(2026, 5, 1)));
        milestoneRepository.saveAndFlush(milestone(project, "Planning", LocalDate.of(2026, 1, 1)));
        milestoneRepository.saveAndFlush(milestone(project, "Experiment", LocalDate.of(2026, 3, 1)));

        List<MilestoneEntity> milestones = milestoneRepository.findByProjectIdOrderByStartDateAsc(project.getId());

        assertEquals(3, milestones.size());
        assertEquals("Planning", milestones.get(0).getName());
        assertEquals("Experiment", milestones.get(1).getName());
        assertEquals("Prototype", milestones.get(2).getName());
    }

    private ProjectEntity createProject() {
        User leader = new User();
        leader.setUsername("milestone_leader");
        leader.setEmail("milestone.leader@test.com");
        leader.setPassword("hashed_password");
        leader.setStatus(UserStatus.ACTIVE);
        leader = userRepository.saveAndFlush(leader);

        Laboratory lab = new Laboratory();
        lab.setLabName("Milestone Lab");
        lab.setLocation("Room M1");
        lab.setCapacity(8);
        lab = laboratoryRepository.saveAndFlush(lab);

        GroupEntity group = GroupEntity.builder()
                .lab(lab)
                .name("Milestone Group")
                .leader(leader)
                .build();
        group = groupRepository.saveAndFlush(group);

        ProjectEntity project = ProjectEntity.builder()
                .group(group)
                .title("Milestone Project")
                .description("Timeline project")
                .status(ProjectStatus.ONGOING)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .build();
        return projectRepository.saveAndFlush(project);
    }

    private MilestoneEntity milestone(ProjectEntity project, String name, LocalDate startDate) {
        return MilestoneEntity.builder()
                .project(project)
                .name(name)
                .startDate(startDate)
                .endDate(startDate.plusDays(14))
                .status(MilestoneStatus.PLANNED)
                .build();
    }
}
