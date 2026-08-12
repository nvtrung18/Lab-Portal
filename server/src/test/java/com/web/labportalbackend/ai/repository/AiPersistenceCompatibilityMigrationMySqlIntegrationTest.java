package com.web.labportalbackend.ai.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.web.labportalbackend.LabPortalBackendApplication;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Opt-in two-phase MySQL evidence that V59 rows remain readable after the P5A-T7 migration.
 */
@EnabledIfEnvironmentVariable(named = "LAB_PORTAL_MYSQL_IT", matches = "(?i:true)")
class AiPersistenceCompatibilityMigrationMySqlIntegrationTest {

    private static final String DEFAULT_URL = "jdbc:mysql://127.0.0.1:3307/lab_portal_it";
    private static final AtomicLong SEQUENCE = new AtomicLong(System.currentTimeMillis());

    @Test
    void v59LegacyRowsUpgradeToHeadAndHibernateValidatesTheNewNullableMapping() throws Exception {
        DatabaseUrl configured = DatabaseUrl.parse(environmentOrDefault("LAB_PORTAL_MYSQL_IT_URL", DEFAULT_URL));
        assertDisposableDatabase(configured.database());
        String database = configured.database() + "_p5at7_" + SEQUENCE.incrementAndGet() + "_it";
        assertDisposableDatabase(database);
        String username = requiredEnvironment("LAB_PORTAL_MYSQL_IT_USERNAME");
        String password = requiredEnvironment("LAB_PORTAL_MYSQL_IT_PASSWORD");
        boolean created = false;

        try (Connection server = DriverManager.getConnection(configured.serverUrl(), username, password)) {
            try (Statement statement = server.createStatement()) {
                statement.execute("CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                created = true;
            }
            String disposableUrl = configured.withDatabase(database);
            migrate(disposableUrl, username, password, "59");
            insertV59Fixtures(disposableUrl, username, password);
            migrate(disposableUrl, username, password, null);

            assertV59RowsRemainReadable(disposableUrl, username, password);
            assertNewChecksAndThreeKeyCompatibility(disposableUrl, username, password);
            assertHibernateValidateStartsAfterTheSchemaReachesHead(disposableUrl, username, password);
        } finally {
            if (created) {
                try (Connection server = DriverManager.getConnection(configured.serverUrl(), username, password);
                     Statement statement = server.createStatement()) {
                    statement.execute("DROP DATABASE `" + database + "`");
                }
            }
        }
    }

    private static void migrate(String url, String username, String password, String target) {
        FluentConfiguration configuration = Flyway.configure().dataSource(url, username, password)
                .locations("classpath:db/migration").baselineOnMigrate(false).cleanDisabled(true);
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }

    private static void insertV59Fixtures(String url, String username, String password) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            long userId = insertUser(connection);
            for (String key : new String[] {"LAB_ASSISTANT", "RESEARCH_ASSISTANT"}) {
                insertConversation(connection, userId, key);
                insertAssistantConfig(connection, key);
                insertActionSuggestion(connection, userId, key);
                insertUsageLog(connection, userId, key);
                insertQuotaConfig(connection, key);
            }
        }
    }

    private static long insertUser(Connection connection) throws SQLException {
        String token = "p5at7-legacy-" + SEQUENCE.incrementAndGet();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO users (email, username, password, status) VALUES (?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, token + "@mysql-it.example.test");
            statement.setString(2, token);
            statement.setString(3, "not-a-real-credential");
            statement.setString(4, "ACTIVE");
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static void insertConversation(Connection connection, long userId, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO ai_conversation (user_id, assistant_key, module_context, title) VALUES (?, ?, ?, ?)")) {
            statement.setLong(1, userId);
            statement.setString(2, key);
            statement.setString(3, "legacy");
            statement.setString(4, "legacy " + key);
            statement.executeUpdate();
        }
    }

    private static void insertAssistantConfig(Connection connection, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO ai_assistant_config (assistant_key, enabled, system_prompt_key, max_requests_per_day, max_context_tokens)
                VALUES (?, true, ?, 1, 1)
                """)) {
            statement.setString(1, key);
            statement.setString(2, "legacy-" + key);
            statement.executeUpdate();
        }
    }

    private static void insertActionSuggestion(Connection connection, long userId, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO ai_action_suggestion (requested_by, assistant_key, action_type, target_module, payload_json, status)
                VALUES (?, ?, 'LEGACY_ACTION', 'legacy', JSON_OBJECT('legacy', true), 'EDITED')
                """)) {
            statement.setLong(1, userId);
            statement.setString(2, key);
            statement.executeUpdate();
        }
    }

    private static void insertUsageLog(Connection connection, long userId, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO ai_usage_log (user_id, assistant_key, `role`, `module`, status)
                VALUES (?, ?, 'STUDENT', 'legacy', 'SUCCESS')
                """)) {
            statement.setLong(1, userId);
            statement.setString(2, key);
            statement.executeUpdate();
        }
    }

    private static void insertQuotaConfig(Connection connection, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO ai_quota_config (assistant_key, `role`, `module`, max_requests_per_day, max_context_tokens)
                VALUES (?, 'STUDENT', 'legacy', 1, 1)
                """)) {
            statement.setString(1, key);
            statement.executeUpdate();
        }
    }

    private static void assertV59RowsRemainReadable(String url, String username, String password) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '60' AND success = 1"));
            for (String table : new String[] {
                    "ai_conversation", "ai_assistant_config", "ai_action_suggestion", "ai_usage_log", "ai_quota_config"}) {
                assertEquals(2, count(connection, "SELECT COUNT(*) FROM " + table
                        + " WHERE assistant_key IN ('LAB_ASSISTANT', 'RESEARCH_ASSISTANT')"));
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT target_module, status, model_version, adapter_version, prompt_version, resource_type, resource_id,
                           action_risk_level, confirmation_status, execution_status
                    FROM ai_action_suggestion WHERE action_type = 'LEGACY_ACTION'
                    """ ); ResultSet rows = statement.executeQuery()) {
                int count = 0;
                while (rows.next()) {
                    assertEquals("legacy", rows.getString("target_module"));
                    assertEquals("EDITED", rows.getString("status"));
                    for (int column = 3; column <= 10; column++) {
                        assertNull(rows.getObject(column));
                    }
                    count++;
                }
                assertEquals(2, count);
            }
        }
    }

    private static void assertNewChecksAndThreeKeyCompatibility(String url, String username, String password)
            throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            long userId = firstId(connection, "SELECT id FROM users ORDER BY id LIMIT 1");
            insertConversation(connection, userId, "ADMIN_ASSISTANT");
            insertAssistantConfig(connection, "ADMIN_ASSISTANT");
            insertActionSuggestion(connection, userId, "ADMIN_ASSISTANT");
            insertUsageLog(connection, userId, "ADMIN_ASSISTANT");
            insertQuotaConfig(connection, "ADMIN_ASSISTANT");

            assertRejects(connection, "INSERT INTO ai_assistant_config (assistant_key, enabled, system_prompt_key, max_requests_per_day, max_context_tokens) "
                    + "VALUES ('UNKNOWN', true, 'unknown', 1, 1)");
            assertRejects(connection, actionInsert(userId, "'UNKNOWN_RESOURCE'", "'READ_ONLY'", "'NOT_REQUIRED'", "'NOT_REQUESTED'"));
            assertRejects(connection, actionInsert(userId, "'PROJECT'", "'UNKNOWN_RISK'", "'NOT_REQUIRED'", "'NOT_REQUESTED'"));
            assertRejects(connection, actionInsert(userId, "'PROJECT'", "'READ_ONLY'", "'UNKNOWN_CONFIRMATION'", "'NOT_REQUESTED'"));
            assertRejects(connection, actionInsert(userId, "'PROJECT'", "'READ_ONLY'", "'NOT_REQUIRED'", "'UNKNOWN_EXECUTION'"));
        }
    }

    private static String actionInsert(long userId, String resourceType, String risk, String confirmation, String execution) {
        return "INSERT INTO ai_action_suggestion (requested_by, assistant_key, action_type, target_module, payload_json, status, "
                + "resource_type, action_risk_level, confirmation_status, execution_status) VALUES (" + userId
                + ", 'LAB_ASSISTANT', 'CHECK_TEST', 'legacy', JSON_OBJECT('check', true), 'PENDING', "
                + resourceType + ", " + risk + ", " + confirmation + ", " + execution + ")";
    }

    private static void assertHibernateValidateStartsAfterTheSchemaReachesHead(
            String url, String username, String password) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(LabPortalBackendApplication.class)
                .web(WebApplicationType.NONE).properties(
                        "spring.datasource.url=" + url,
                        "spring.datasource.username=" + username,
                        "spring.datasource.password=" + password,
                        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                        "spring.flyway.enabled=false",
                        "spring.jpa.hibernate.ddl-auto=validate",
                        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect")
                .run()) {
            assertNotNull(context.getBean(jakarta.persistence.EntityManagerFactory.class));
        }
    }

    private static int count(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static long firstId(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static void assertRejects(Connection connection, String sql) {
        assertThrows(SQLException.class, () -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(sql);
            }
        });
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

    private static void assertDisposableDatabase(String database) {
        if (!database.toLowerCase(Locale.ROOT).endsWith("_it") || !database.matches("[A-Za-z0-9_]+")) {
            throw new IllegalStateException("LAB_PORTAL_MYSQL_IT_URL must target a disposable database ending in _it");
        }
    }

    private record DatabaseUrl(String prefix, String suffix, String database) {
        static DatabaseUrl parse(String value) {
            int slash = value.lastIndexOf('/');
            int query = value.indexOf('?', slash);
            if (slash < 0) {
                throw new IllegalStateException("LAB_PORTAL_MYSQL_IT_URL must include a database name");
            }
            return new DatabaseUrl(value.substring(0, slash + 1), query < 0 ? "" : value.substring(query),
                    value.substring(slash + 1, query < 0 ? value.length() : query));
        }

        String serverUrl() {
            return prefix + suffix;
        }

        String withDatabase(String database) {
            return prefix + database + suffix;
        }
    }
}
