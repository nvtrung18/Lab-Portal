package com.web.labportalbackend.research.service;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.enums.TaskPriority;
import com.web.labportalbackend.research.enums.TaskStatus;
import com.web.labportalbackend.research.enums.TaskType;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in MySQL/Flyway evidence for the V56 task-comment schema contract. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "LAB_PORTAL_MYSQL_IT", matches = "(?i:true)")
class TaskCommentMySqlIntegrationTest {

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
    void v56CreatesTaskCommentsWithRequiredMysqlContract() {
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM flyway_schema_history
                WHERE version = '56' AND success = 1
                """, Integer.class));

        Map<String, Object> table = jdbcTemplate.queryForMap("""
                SELECT ENGINE, TABLE_COLLATION,
                       SUBSTRING_INDEX(TABLE_COLLATION, '_', 1) AS TABLE_CHARSET
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'task_comments'
                """);
        assertEquals("InnoDB", table.get("ENGINE"));
        assertEquals("utf8mb4", table.get("TABLE_CHARSET"));
        assertEquals("utf8mb4_unicode_ci", table.get("TABLE_COLLATION"));

        Map<String, Map<String, String>> columns = jdbcTemplate.query("""
                SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE,
                       CAST(COLUMN_DEFAULT AS CHAR) AS COLUMN_DEFAULT, EXTRA
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'task_comments'
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
                "id", "bigint", "task_id", "bigint", "author_id", "bigint", "content", "text",
                "created_at", "timestamp(6)", "updated_at", "timestamp(6)",
                "active", "tinyint(1)", "deleted", "tinyint(1)"),
                columns.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, entry -> entry.getValue().get("type"))));
        assertTrue(columns.values().stream().allMatch(column -> "NO".equals(column.get("nullable"))));
        assertEquals("auto_increment", columns.get("id").get("extra"));
        assertEquals("CURRENT_TIMESTAMP(6)", columns.get("created_at").get("default"));
        assertEquals("CURRENT_TIMESTAMP(6)", columns.get("updated_at").get("default"));
        assertEquals("1", columns.get("active").get("default"));
        assertEquals("0", columns.get("deleted").get("default"));
        assertTrue(columns.get("updated_at").get("extra").toLowerCase(Locale.ROOT)
                .contains("on update current_timestamp(6)")); // MySQL may also report default_generated.

        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'task_comments'
                  AND INDEX_NAME = 'idx_task_comments_task_id'
                  AND COLUMN_NAME = 'task_id' AND SEQ_IN_INDEX = 1
                """, Integer.class));

        Map<String, ForeignKey> foreignKeys = jdbcTemplate.query("""
                SELECT kcu.CONSTRAINT_NAME, kcu.COLUMN_NAME,
                       kcu.REFERENCED_TABLE_NAME, kcu.REFERENCED_COLUMN_NAME,
                       rc.DELETE_RULE
                FROM information_schema.KEY_COLUMN_USAGE kcu
                JOIN information_schema.REFERENTIAL_CONSTRAINTS rc
                  ON rc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA
                 AND rc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME
                WHERE kcu.CONSTRAINT_SCHEMA = DATABASE() AND kcu.TABLE_NAME = 'task_comments'
                """, resultSet -> {
            Map<String, ForeignKey> result = new java.util.HashMap<>();
            while (resultSet.next()) result.put(resultSet.getString(1), new ForeignKey(
                    resultSet.getString(2), resultSet.getString(3),
                    resultSet.getString(4), resultSet.getString(5)));
            return result;
        });
        assertEquals(new ForeignKey("task_id", "tasks", "id", "CASCADE"),
                foreignKeys.get("fk_task_comment_task"));
        ForeignKey authorForeignKey = foreignKeys.get("fk_task_comment_author");
        assertEquals("author_id", authorForeignKey.column());
        assertEquals("users", authorForeignKey.referencedTable());
        assertEquals("id", authorForeignKey.referencedColumn());
        assertTrue("RESTRICT".equalsIgnoreCase(authorForeignKey.deleteRule())
                || "NO ACTION".equalsIgnoreCase(authorForeignKey.deleteRule()));
    }

    @Test
    @Transactional
    void commentBlocksAuthorDeletionAndCascadesWhenItsTaskIsDeleted() {
        User author = authorFixture();
        TaskEntity task = taskFixture();
        jdbcTemplate.update("INSERT INTO task_comments (task_id, author_id, content) VALUES (?, ?, ?)",
                task.getId(), author.getId(), "MySQL lifecycle");

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", author.getId()));

        assertEquals(1, jdbcTemplate.update("DELETE FROM tasks WHERE id = ?", task.getId()));
        assertFalse(Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM task_comments WHERE task_id = ?)
                """, Boolean.class, task.getId())));
        assertEquals(1, jdbcTemplate.update("DELETE FROM users WHERE id = ?", author.getId()));
    }

    private User authorFixture() {
        String token = unique("author");
        User author = new User();
        author.setUsername(token.substring(0, Math.min(token.length(), 50)));
        author.setEmail(token.substring(0, Math.min(token.length(), 70)) + "@mysql-it.example.test");
        author.setPassword("not-a-real-credential");
        return userRepository.saveAndFlush(author);
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
