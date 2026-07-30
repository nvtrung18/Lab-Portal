package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.enums.TaskPriority;
import com.web.labportalbackend.research.enums.TaskStatus;
import com.web.labportalbackend.research.enums.TaskType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in MySQL/Flyway evidence for the V57 task-activity schema contract. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "LAB_PORTAL_MYSQL_IT", matches = "(?i:true)")
class TaskActivityMySqlIntegrationTest {

    private static final String DEFAULT_URL = "jdbc:mysql://127.0.0.1:3307/lab_portal_it";
    private static final AtomicLong SEQUENCE = new AtomicLong(System.currentTimeMillis());

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserRepository userRepository;
    @Autowired LaboratoryRepository laboratoryRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired TaskRepository taskRepository;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        String url = environmentOrDefault("LAB_PORTAL_MYSQL_IT_URL", DEFAULT_URL);
        assertDisposableDatabase(url);
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> requiredEnvironment("LAB_PORTAL_MYSQL_IT_USERNAME"));
        registry.add("spring.datasource.password", () -> requiredEnvironment("LAB_PORTAL_MYSQL_IT_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.baseline-on-migrate", () -> "false");
        registry.add("spring.flyway.clean-disabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
    }

    @Test
    void v57CreatesTaskActivityWithRequiredMysqlContract() {
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM flyway_schema_history
                WHERE version = '57' AND success = 1
                """, Integer.class));

        Map<String, Object> table = jdbcTemplate.queryForMap("""
                SELECT ENGINE, TABLE_COLLATION,
                       SUBSTRING_INDEX(TABLE_COLLATION, '_', 1) AS TABLE_CHARSET
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'research_task_activity'
                """);
        assertEquals("InnoDB", table.get("ENGINE"));
        assertEquals("utf8mb4", table.get("TABLE_CHARSET"));
        assertEquals("utf8mb4_unicode_ci", table.get("TABLE_COLLATION"));

        Map<String, Map<String, String>> columns = jdbcTemplate.query("""
                SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE,
                       CAST(COLUMN_DEFAULT AS CHAR) AS COLUMN_DEFAULT, EXTRA
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'research_task_activity'
                """, resultSet -> {
            Map<String, Map<String, String>> result = new java.util.HashMap<>();
            while (resultSet.next()) {
                result.put(resultSet.getString("COLUMN_NAME"), Map.of(
                        "type", resultSet.getString("COLUMN_TYPE"),
                        "nullable", resultSet.getString("IS_NULLABLE"),
                        "default", String.valueOf(resultSet.getString("COLUMN_DEFAULT")),
                        "extra", resultSet.getString("EXTRA")));
            }
            return result;
        });
        assertEquals(Map.of(
                "id", "bigint", "task_id", "bigint", "user_id", "bigint", "action", "varchar(50)",
                "old_value", "text", "new_value", "text", "created_at", "timestamp(6)",
                "updated_at", "timestamp(6)", "active", "tinyint(1)", "deleted", "tinyint(1)"),
                columns.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, entry -> entry.getValue().get("type"))));
        assertEquals("NO", columns.get("task_id").get("nullable"));
        assertEquals("NO", columns.get("user_id").get("nullable"));
        assertEquals("NO", columns.get("action").get("nullable"));
        assertEquals("NO", columns.get("id").get("nullable"));
        assertEquals("NO", columns.get("created_at").get("nullable"));
        assertEquals("NO", columns.get("updated_at").get("nullable"));
        assertEquals("NO", columns.get("active").get("nullable"));
        assertEquals("NO", columns.get("deleted").get("nullable"));
        assertEquals("YES", columns.get("old_value").get("nullable"));
        assertEquals("YES", columns.get("new_value").get("nullable"));
        assertEquals("auto_increment", columns.get("id").get("extra"));
        assertEquals("CURRENT_TIMESTAMP(6)", columns.get("created_at").get("default"));
        assertEquals("CURRENT_TIMESTAMP(6)", columns.get("updated_at").get("default"));
        assertEquals("1", columns.get("active").get("default"));
        assertEquals("0", columns.get("deleted").get("default"));
        assertTrue(columns.get("updated_at").get("extra").toLowerCase(Locale.ROOT)
                .contains("on update current_timestamp(6)"));

        assertEquals(List.of("task_id", "created_at", "id"), jdbcTemplate.query("""
                SELECT COLUMN_NAME FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'research_task_activity'
                  AND INDEX_NAME = 'idx_research_task_activity_task_created'
                ORDER BY SEQ_IN_INDEX
                """, (resultSet, rowNum) -> resultSet.getString(1)));

        Map<String, ForeignKey> foreignKeys = jdbcTemplate.query("""
                SELECT kcu.CONSTRAINT_NAME, kcu.COLUMN_NAME,
                       kcu.REFERENCED_TABLE_NAME, kcu.REFERENCED_COLUMN_NAME, rc.DELETE_RULE
                FROM information_schema.KEY_COLUMN_USAGE kcu
                JOIN information_schema.REFERENTIAL_CONSTRAINTS rc
                  ON rc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA
                 AND rc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME
                WHERE kcu.CONSTRAINT_SCHEMA = DATABASE() AND kcu.TABLE_NAME = 'research_task_activity'
                """, resultSet -> {
            Map<String, ForeignKey> result = new java.util.HashMap<>();
            while (resultSet.next()) result.put(resultSet.getString(1), new ForeignKey(
                    resultSet.getString(2), resultSet.getString(3),
                    resultSet.getString(4), resultSet.getString(5)));
            return result;
        });
        assertRestrictiveForeignKey(new ForeignKey("task_id", "tasks", "id", "RESTRICT"),
                foreignKeys.get("fk_research_task_activity_task"));
        assertRestrictiveForeignKey(new ForeignKey("user_id", "users", "id", "RESTRICT"),
                foreignKeys.get("fk_research_task_activity_user"));
    }

    @Test
    @Transactional
    void activityBlocksPhysicalTaskAndActorDeletionWhilePreservingHistory() {
        User actor = userFixture();
        TaskEntity task = taskFixture();
        jdbcTemplate.update("""
                INSERT INTO research_task_activity (task_id, user_id, action, old_value, new_value)
                VALUES (?, ?, ?, ?, ?)
                """, task.getId(), actor.getId(), "TASK_STATUS_CHANGED", "TODO", "IN_PROGRESS");

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("DELETE FROM tasks WHERE id = ?", task.getId()));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", actor.getId()));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM research_task_activity WHERE task_id = ? AND user_id = ?
                """, Integer.class, task.getId(), actor.getId()));
    }

    private User userFixture() {
        String token = unique("actor");
        User actor = new User();
        actor.setUsername(token.substring(0, Math.min(token.length(), 50)));
        actor.setEmail(token.substring(0, Math.min(token.length(), 70)) + "@mysql-it.example.test");
        actor.setPassword("not-a-real-credential");
        return userRepository.saveAndFlush(actor);
    }

    private TaskEntity taskFixture() {
        String token = unique("task");
        Laboratory laboratory = new Laboratory();
        laboratory.setLabName(token.substring(0, Math.min(token.length(), 100)));
        laboratory.setLocation("MySQL integration test");
        laboratory.setCapacity(1);
        laboratory = laboratoryRepository.saveAndFlush(laboratory);
        ProjectEntity project = projectRepository.saveAndFlush(ProjectEntity.builder()
                .lab(laboratory).title(token + " project").build());
        return taskRepository.saveAndFlush(TaskEntity.builder()
                .projectId(project.getId()).title(token).status(TaskStatus.TODO)
                .priority(TaskPriority.MEDIUM).type(TaskType.TASK).progressPercent(0).build());
    }

    private static void assertRestrictiveForeignKey(ForeignKey expected, ForeignKey actual) {
        assertEquals(expected.column(), actual.column());
        assertEquals(expected.referencedTable(), actual.referencedTable());
        assertEquals(expected.referencedColumn(), actual.referencedColumn());
        assertTrue("RESTRICT".equalsIgnoreCase(actual.deleteRule())
                || "NO ACTION".equalsIgnoreCase(actual.deleteRule()));
    }

    private static String unique(String label) {
        return "mysql-it-" + label + "-" + SEQUENCE.incrementAndGet();
    }

    private static String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set for the opt-in MySQL integration suite");
        }
        return value;
    }

    private static void assertDisposableDatabase(String url) {
        int slash = url.lastIndexOf('/');
        int query = url.indexOf('?', slash);
        String database = slash < 0 ? "" : url.substring(slash + 1, query < 0 ? url.length() : query);
        if (!database.toLowerCase(Locale.ROOT).endsWith("_it")) {
            throw new IllegalStateException(
                    "LAB_PORTAL_MYSQL_IT_URL must target a disposable database ending in _it");
        }
    }

    private record ForeignKey(String column, String referencedTable, String referencedColumn, String deleteRule) {
    }
}
