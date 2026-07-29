package com.web.labportalbackend.research.service;

import com.web.labportalbackend.research.dto.request.CreateTaskCommentRequest;
import com.web.labportalbackend.research.dto.response.TaskCommentResponse;
import com.web.labportalbackend.research.security.TaskPermissionHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Opt-in MySQL/Flyway schema evidence. Start docker/mysql-it-compose.yml and
 * set LAB_PORTAL_MYSQL_IT=true with disposable *_it database credentials.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "LAB_PORTAL_MYSQL_IT", matches = "(?i:true)")
class TaskCommentMySqlIntegrationTest {

    private static final String DEFAULT_URL = "jdbc:mysql://127.0.0.1:3307/lab_portal_it"
            + "?useUnicode=true&characterEncoding=utf8&connectionCollation=utf8mb4_unicode_ci"
            + "&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true";

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired TaskCommentService taskCommentService;
    @MockitoBean TaskPermissionHelper taskPermissionHelper;

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
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
    }

    @Test
    void flywayCreatesTaskCommentTableWithRequiredMysqlContract() {
        Integer migrationApplied = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '56' AND success = 1", Integer.class);
        assertEquals(1, migrationApplied);
        assertEquals("InnoDB", jdbcTemplate.queryForObject("""
                SELECT ENGINE FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'task_comments'
                """, String.class));
        Map<String, Object> table = jdbcTemplate.queryForMap("""
                SELECT TABLE_COLLATION, SUBSTRING_INDEX(TABLE_COLLATION, '_', 1) AS TABLE_CHARSET
                FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'task_comments'
                """);
        assertEquals("utf8mb4", table.get("TABLE_CHARSET"));
        assertEquals("utf8mb4_unicode_ci", table.get("TABLE_COLLATION"));
        Map<String, Map<String, Object>> columns = jdbcTemplate.query("""
                SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'task_comments'
                """, rs -> { Map<String, Map<String, Object>> result = new java.util.HashMap<>(); while (rs.next()) result.put(rs.getString("COLUMN_NAME"), Map.of("type", rs.getString("COLUMN_TYPE"), "nullable", rs.getString("IS_NULLABLE"), "default", String.valueOf(rs.getObject("COLUMN_DEFAULT")), "extra", rs.getString("EXTRA"))); return result; });
        assertEquals(Map.of("id", "bigint", "task_id", "bigint", "author_id", "bigint", "content", "text",
                "created_at", "timestamp(6)", "updated_at", "timestamp(6)", "active", "tinyint(1)", "deleted", "tinyint(1)"),
                columns.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get("type"))));
        assertEquals(Map.of("id", "NO", "task_id", "NO", "author_id", "NO", "content", "NO", "created_at", "NO", "updated_at", "NO", "active", "NO", "deleted", "NO"),
                columns.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get("nullable"))));
        assertEquals("auto_increment", columns.get("id").get("extra"));
        assertEquals("CURRENT_TIMESTAMP(6)", columns.get("created_at").get("default"));
        assertEquals("CURRENT_TIMESTAMP(6)", columns.get("updated_at").get("default")); assertEquals("1", columns.get("active").get("default")); assertEquals("0", columns.get("deleted").get("default"));
        assertEquals("on update current_timestamp(6)", ((String) columns.get("updated_at").get("extra")).toLowerCase(Locale.ROOT));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
                AND TABLE_NAME = 'task_comments' AND INDEX_NAME = 'idx_task_comments_task_id' AND COLUMN_NAME = 'task_id' AND SEQ_IN_INDEX = 1
                """, Integer.class));
        Map<String, String> deleteRules = jdbcTemplate.query("""
                SELECT CONSTRAINT_NAME, DELETE_RULE FROM information_schema.REFERENTIAL_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'task_comments'
                """, rs -> { Map<String, String> result = new java.util.HashMap<>(); while (rs.next()) result.put(rs.getString(1), rs.getString(2)); return result; });
        assertEquals(Map.of("fk_task_comment_task", "CASCADE", "fk_task_comment_author", "RESTRICT"), deleteRules);
    }

    @Test
    @Transactional
    void servicePersistsAuthenticatedAuthorAndDatabaseEnforcesCommentDeleteLifecycle() {
        Map<String, Object> actor = jdbcTemplate.queryForMap("""
                SELECT u.id, u.username FROM users u JOIN user_roles ur ON ur.user_id = u.id JOIN roles r ON r.id = ur.role_id
                WHERE r.name = 'STUDENT' AND u.active = 1 AND u.deleted = 0 LIMIT 1
                """);
        Long taskId = jdbcTemplate.queryForObject("SELECT id FROM tasks WHERE active = 1 AND deleted = 0 LIMIT 1", Long.class);
        Long authorId = ((Number) actor.get("id")).longValue();
        when(taskPermissionHelper.canViewTask(authorId, null)).thenReturn(true);
        when(taskPermissionHelper.canViewTask(org.mockito.ArgumentMatchers.eq(authorId), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor.get("username"), null));
        try {
            CreateTaskCommentRequest request = new CreateTaskCommentRequest(); request.setContent("  MySQL lifecycle  ");
            TaskCommentResponse saved = taskCommentService.addComment(taskId, request);
            assertEquals(authorId, saved.getAuthorId()); assertEquals(taskId, saved.getTaskId()); assertEquals("MySQL lifecycle", saved.getContent());
            assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM task_comments WHERE id = ? AND author_id = ? AND task_id = ?", Integer.class, saved.getId(), authorId, taskId));
            assertThrows(Exception.class, () -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", authorId));
            jdbcTemplate.update("DELETE FROM tasks WHERE id = ?", taskId);
            assertFalse(Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT EXISTS(SELECT 1 FROM task_comments WHERE id = ?)", Boolean.class, saved.getId())));
        } finally { SecurityContextHolder.clearContext(); }
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
            throw new IllegalStateException("LAB_PORTAL_MYSQL_IT_URL must target a disposable database ending in _it");
        }
    }
}
