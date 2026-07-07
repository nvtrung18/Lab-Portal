package com.web.labportalbackend.research.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.RoleRepository;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.booking.entity.Booking;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.enums.UserStatus;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.GroupMemberEntity;
import com.web.labportalbackend.research.entity.MilestoneEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.entity.ReportEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.enums.MilestoneStatus;
import com.web.labportalbackend.research.enums.ProjectStatus;
import com.web.labportalbackend.research.enums.TaskStatus;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.MilestoneRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.repository.ReportRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProjectStatsIntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LaboratoryRepository laboratoryRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Test
    void getProjectStats_updatesTaskCompletionAndReportCount() throws Exception {
        ProjectFixture fixture = createProjectFixture("task-report");
        TaskEntity done = createTask(fixture.milestone(), TaskStatus.DONE);
        TaskEntity review = createTask(fixture.milestone(), TaskStatus.IN_REVIEW);
        TaskEntity todo = createTask(fixture.milestone(), TaskStatus.TODO);
        createReport(done, 1);
        createReport(review, 1);
        createReport(review, 2);

        JsonNode data = callStats(fixture.project().getId());
        JsonNode overview = data.get("overview");

        assertEquals(fixture.project().getId().longValue(), data.get("projectId").asLong());
        assertEquals(3, overview.get("taskCount").asLong());
        assertEquals(1, overview.get("completedTaskCount").asLong());
        assertEquals("33.33", overview.get("taskCompletionRate").decimalValue().toPlainString());
        assertEquals(3, overview.get("reportCount").asLong());
        assertEquals(0, overview.get("attendanceCount").asLong());
    }

    @Test
    void getProjectStats_mapsAttendanceFromBookingStatsPort() throws Exception {
        ProjectFixture fixture = createProjectFixture("attendance");
        createTask(fixture.milestone(), TaskStatus.DONE);
        createBooking(fixture.member(), fixture.lab(), BookingStatus.COMPLETED);
        createBooking(fixture.member(), fixture.lab(), BookingStatus.CHECKED_IN);
        createBooking(fixture.member(), fixture.lab(), BookingStatus.NO_SHOW);
        createBooking(fixture.member(), fixture.lab(), BookingStatus.CONFIRMED);

        JsonNode overview = callStats(fixture.project().getId()).get("overview");

        assertEquals(2, overview.get("attendanceCount").asLong());
        assertEquals("50.0", overview.get("attendanceRate").decimalValue().toPlainString());
    }

    @Test
    void getProjectStats_matchesNativeSqlCounts() throws Exception {
        ProjectFixture fixture = createProjectFixture("sanity");
        TaskEntity doneOne = createTask(fixture.milestone(), TaskStatus.DONE);
        TaskEntity doneTwo = createTask(fixture.milestone(), TaskStatus.DONE);
        TaskEntity inProgress = createTask(fixture.milestone(), TaskStatus.IN_PROGRESS);
        createReport(doneOne, 1);
        createReport(doneTwo, 1);
        createReport(inProgress, 1);
        createReport(inProgress, 2);
        createBooking(fixture.member(), fixture.lab(), BookingStatus.COMPLETED);
        createBooking(fixture.member(), fixture.lab(), BookingStatus.IN_PROGRESS);
        createBooking(fixture.member(), fixture.lab(), BookingStatus.NO_SHOW);

        JsonNode data = callStats(fixture.project().getId());
        JsonNode overview = data.get("overview");
        Long projectId = fixture.project().getId();

        Long totalTasks = jdbcTemplate.queryForObject("""
                SELECT COUNT(t.id)
                FROM tasks t
                JOIN milestones m ON m.id = t.milestone_id
                WHERE m.project_id = ?
                """, Long.class, projectId);
        Long doneTasks = jdbcTemplate.queryForObject("""
                SELECT COUNT(t.id)
                FROM tasks t
                JOIN milestones m ON m.id = t.milestone_id
                WHERE m.project_id = ? AND t.status = 'DONE'
                """, Long.class, projectId);
        Long reportCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(r.id)
                FROM reports r
                JOIN tasks t ON t.id = r.task_id
                JOIN milestones m ON m.id = t.milestone_id
                WHERE m.project_id = ?
                """, Long.class, projectId);
        Long totalBookings = jdbcTemplate.queryForObject("""
                SELECT COUNT(b.id)
                FROM projects p
                JOIN research_groups g ON g.id = p.group_id
                JOIN group_members gm ON gm.group_id = g.id
                JOIN bookings b ON b.user_id = gm.user_id AND b.lab_id = g.lab_id
                WHERE p.id = ?
                  AND p.active = true AND p.deleted = false
                  AND g.active = true AND g.deleted = false
                  AND gm.active = true AND gm.deleted = false
                  AND b.active = true AND b.deleted = false
                """, Long.class, projectId);
        Long attendedSessions = jdbcTemplate.queryForObject("""
                SELECT COUNT(b.id)
                FROM projects p
                JOIN research_groups g ON g.id = p.group_id
                JOIN group_members gm ON gm.group_id = g.id
                JOIN bookings b ON b.user_id = gm.user_id AND b.lab_id = g.lab_id
                WHERE p.id = ?
                  AND b.status IN ('CHECKED_IN', 'IN_PROGRESS', 'COMPLETED')
                  AND p.active = true AND p.deleted = false
                  AND g.active = true AND g.deleted = false
                  AND gm.active = true AND gm.deleted = false
                  AND b.active = true AND b.deleted = false
                """, Long.class, projectId);
        Long noShowSessions = jdbcTemplate.queryForObject("""
                SELECT COUNT(b.id)
                FROM projects p
                JOIN research_groups g ON g.id = p.group_id
                JOIN group_members gm ON gm.group_id = g.id
                JOIN bookings b ON b.user_id = gm.user_id AND b.lab_id = g.lab_id
                WHERE p.id = ?
                  AND b.status = 'NO_SHOW'
                  AND p.active = true AND p.deleted = false
                  AND g.active = true AND g.deleted = false
                  AND gm.active = true AND gm.deleted = false
                  AND b.active = true AND b.deleted = false
                """, Long.class, projectId);

        assertEquals(totalTasks, overview.get("taskCount").asLong());
        assertEquals(doneTasks, overview.get("completedTaskCount").asLong());
        assertEquals(reportCount, overview.get("reportCount").asLong());
        assertEquals(attendedSessions, overview.get("attendanceCount").asLong());
        assertEquals(
                attendedSessions == 0 ? "0" : java.math.BigDecimal.valueOf(attendedSessions)
                        .multiply(java.math.BigDecimal.valueOf(100))
                        .divide(java.math.BigDecimal.valueOf(totalBookings), 2, java.math.RoundingMode.HALF_UP)
                        .stripTrailingZeros()
                        .toPlainString(),
                overview.get("attendanceRate").decimalValue().stripTrailingZeros().toPlainString()
        );
    }

    private JsonNode callStats(Long projectId) throws Exception {
        String response = mockMvc.perform(get("/projects/{id}/stats", projectId)
                        .param("type", "overview")
                        .with(user("stats_manager").roles("LAB_MANAGER")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("data");
    }

    private ProjectFixture createProjectFixture(String name) {
        int sequence = SEQUENCE.incrementAndGet();
        User manager = findOrCreateManager();
        User leader = createUser(name + "_leader_" + sequence);
        User member = createUser(name + "_member_" + sequence);

        Laboratory lab = new Laboratory();
        lab.setLabName("Stats " + name + " Lab " + sequence);
        lab.setLocation("Room S" + sequence);
        lab.setCapacity(12);
        lab.setManager(manager);
        lab = laboratoryRepository.saveAndFlush(lab);

        GroupEntity group = GroupEntity.builder()
                .lab(lab)
                .name("Stats " + name + " Group " + sequence)
                .leader(leader)
                .build();
        group = groupRepository.saveAndFlush(group);

        groupMemberRepository.saveAndFlush(GroupMemberEntity.builder()
                .group(group)
                .user(member)
                .role(GroupRole.MEMBER)
                .build());

        ProjectEntity project = ProjectEntity.builder()
                .lab(lab)
                .group(group)
                .title("Stats " + name + " Project " + sequence)
                .description("Project stats integration test")
                .status(ProjectStatus.ONGOING)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .build();
        project = projectRepository.saveAndFlush(project);

        MilestoneEntity milestone = MilestoneEntity.builder()
                .project(project)
                .name("Stats Milestone " + sequence)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 2, 1))
                .status(MilestoneStatus.IN_PROGRESS)
                .build();
        milestone = milestoneRepository.saveAndFlush(milestone);

        return new ProjectFixture(member, lab, project, milestone);
    }

    private User findOrCreateManager() {
        return userRepository.findByUsername("stats_manager")
                .orElseGet(() -> {
                    Role role = roleRepository.findByName("LAB_MANAGER")
                            .orElseGet(() -> roleRepository.saveAndFlush(new Role("LAB_MANAGER", "Lab manager")));
                    User user = createUser("stats_manager");
                    user.addRole(role);
                    return userRepository.saveAndFlush(user);
                });
    }

    private User createUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@test.com");
        user.setPassword("hashed_password");
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    private TaskEntity createTask(MilestoneEntity milestone, TaskStatus status) {
        TaskEntity task = TaskEntity.builder()
                .milestoneId(milestone.getId())
                .title("Task " + SEQUENCE.incrementAndGet())
                .status(status)
                .build();
        return taskRepository.saveAndFlush(task);
    }

    private void createReport(TaskEntity task, int version) {
        ReportEntity report = ReportEntity.builder()
                .milestoneId(task.getMilestoneId())
                .taskId(task.getId())
                .version(version)
                .title("Legacy report")
                .contentDone("Completed work")
                .result("Result")
                .difficulty("None")
                .nextPlan("Continue")
                .selfAssessment("Good")
                .fileUrl("https://files.local/task-" + task.getId() + "-v" + version + ".pdf")
                .fileName("report.pdf")
                .submissionScope("legacy-task-" + task.getId())
                .build();
        reportRepository.saveAndFlush(report);
    }

    private void createBooking(User user, Laboratory lab, BookingStatus status) {
        Booking booking = new Booking();
        booking.setUser(user);
        booking.setLab(lab);
        booking.setStartTime(Instant.parse("2026-05-14T01:00:00Z").plusSeconds(SEQUENCE.incrementAndGet() * 3600L));
        booking.setEndTime(booking.getStartTime().plusSeconds(3600));
        booking.setStatus(status);
        booking.setParticipantsCount(1);
        bookingRepository.saveAndFlush(booking);
    }

    private record ProjectFixture(User member, Laboratory lab, ProjectEntity project, MilestoneEntity milestone) {
    }
}
