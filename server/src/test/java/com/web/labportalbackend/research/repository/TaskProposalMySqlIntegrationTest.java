package com.web.labportalbackend.research.repository;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.MilestoneEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.entity.TaskProposalEntity;
import com.web.labportalbackend.research.enums.TaskProposalStatus;
import com.web.labportalbackend.research.enums.TaskType;

/** Opt-in MySQL/Flyway evidence for the V58 task-proposal schema contract. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "LAB_PORTAL_MYSQL_IT", matches = "(?i:true)")
class TaskProposalMySqlIntegrationTest {

    private static final String DEFAULT_URL = "jdbc:mysql://127.0.0.1:3307/lab_portal_it";
    private static final AtomicLong SEQUENCE = new AtomicLong(System.currentTimeMillis());
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    UserRepository userRepository;
    @Autowired
    LaboratoryRepository laboratoryRepository;
    @Autowired
    ProjectRepository projectRepository;
    @Autowired
    GroupRepository groupRepository;
    @Autowired
    MilestoneRepository milestoneRepository;
    @Autowired
    TaskProposalRepository taskProposalRepository;
    @Autowired
    jakarta.persistence.EntityManager entityManager;

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
    @Transactional
    void v58CreatesTaskProposalWithRequiredMysqlContract() {
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM flyway_schema_history
                WHERE version = '58' AND success = 1
                """, Integer.class));
        Map<String, Object> table = jdbcTemplate.queryForMap("""
                SELECT ENGINE, TABLE_COLLATION, SUBSTRING_INDEX(TABLE_COLLATION, '_', 1) AS TABLE_CHARSET
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'research_task_proposal'
                """);
        assertEquals("InnoDB", table.get("ENGINE"));
        assertEquals("utf8mb4", table.get("TABLE_CHARSET"));
        assertEquals("utf8mb4_unicode_ci", table.get("TABLE_COLLATION"));

        Map<String, Map<String, String>> columns = columns("research_task_proposal");
        assertEquals(Map.ofEntries(
                Map.entry("id", "bigint"), Map.entry("proposed_by", "bigint"),
                Map.entry("reviewed_by", "bigint"), Map.entry("project_id", "bigint"),
                Map.entry("group_id", "bigint"), Map.entry("milestone_id", "bigint"),
                Map.entry("ai_action_suggestion_id", "bigint"), Map.entry("assisted_by_ai", "tinyint(1)"),
                Map.entry("payload_json", "json"), Map.entry("status", "varchar(20)"),
                Map.entry("reason", "text"), Map.entry("reviewed_at", "timestamp(6)"),
                Map.entry("created_at", "timestamp(6)"), Map.entry("updated_at", "timestamp(6)"),
                Map.entry("active", "tinyint(1)"), Map.entry("deleted", "tinyint(1)")),
                columns.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, entry -> entry.getValue().get("type"))));
        for (String column : List.of("id", "proposed_by", "project_id", "group_id", "assisted_by_ai", "payload_json",
                "status", "created_at", "updated_at", "active", "deleted")) {
            assertEquals("NO", columns.get(column).get("nullable"));
        }
        for (String column : List.of("reviewed_by", "milestone_id", "ai_action_suggestion_id", "reason",
                "reviewed_at")) {
            assertEquals("YES", columns.get(column).get("nullable"));
        }
        assertEquals("auto_increment", columns.get("id").get("extra"));
        assertEquals("0", columns.get("assisted_by_ai").get("default"));
        assertEquals("PENDING", columns.get("status").get("default"));
        assertEquals("CURRENT_TIMESTAMP(6)", columns.get("created_at").get("default"));
        assertEquals("CURRENT_TIMESTAMP(6)", columns.get("updated_at").get("default"));
        assertEquals("1", columns.get("active").get("default"));
        assertEquals("0", columns.get("deleted").get("default"));
        assertTrue(columns.get("updated_at").get("extra").toLowerCase(Locale.ROOT)
                .contains("on update current_timestamp(6)"));

        assertIndex("research_task_proposal", "idx_task_proposal_proposer_status_created",
                List.of("proposed_by", "status", "created_at", "id"));
        assertIndex("research_task_proposal", "idx_task_proposal_group_status_created",
                List.of("group_id", "status", "created_at", "id"));
        assertIndex("research_task_proposal", "idx_task_proposal_project_status_created",
                List.of("project_id", "status", "created_at", "id"));
        assertIndex("research_task_proposal", "idx_task_proposal_ai_suggestion", List.of("ai_action_suggestion_id"));
        Map<String, ForeignKey> foreignKeys = foreignKeys("research_task_proposal");
        assertRestrictiveForeignKey(new ForeignKey("proposed_by", "users", "id", "RESTRICT"),
                foreignKeys.get("fk_task_proposal_proposer"));
        assertRestrictiveForeignKey(new ForeignKey("reviewed_by", "users", "id", "RESTRICT"),
                foreignKeys.get("fk_task_proposal_reviewer"));
        assertRestrictiveForeignKey(new ForeignKey("project_id", "projects", "id", "RESTRICT"),
                foreignKeys.get("fk_task_proposal_project"));
        assertRestrictiveForeignKey(new ForeignKey("group_id", "research_groups", "id", "RESTRICT"),
                foreignKeys.get("fk_task_proposal_group"));
        assertRestrictiveForeignKey(new ForeignKey("milestone_id", "milestones", "id", "RESTRICT"),
                foreignKeys.get("fk_task_proposal_milestone"));

        Fixture fixture = fixture();
        jdbcTemplate.update("""
                INSERT INTO research_task_proposal (proposed_by, project_id, group_id, payload_json)
                VALUES (?, ?, ?, ?)
                """, fixture.proposer().getId(), fixture.project().getId(), fixture.group().getId(),
                payload(TaskType.TASK, fixture.project().getId(), fixture.group().getId(), null));
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO research_task_proposal (proposed_by, project_id, group_id, payload_json)
                VALUES (?, ?, ?, '{not-json}')
                """, fixture.proposer().getId(), fixture.project().getId(), fixture.group().getId()));
        DataAccessException invalidStatusException = assertThrows(
                DataAccessException.class,
                () -> jdbcTemplate.update(
                        """
                                INSERT INTO research_task_proposal (
                                    proposed_by,
                                    project_id,
                                    group_id,
                                    payload_json,
                                    status
                                )
                                VALUES (?, ?, ?, ?, 'INVALID')
                                """,
                        fixture.proposer().getId(),
                        fixture.project().getId(),
                        fixture.group().getId(),
                        payload(TaskType.TASK, fixture.project().getId(), fixture.group().getId(), null)));

        Throwable rootCause = invalidStatusException.getMostSpecificCause();

        assertTrue(
                rootCause instanceof SQLException,
                () -> "Expected SQLException root cause but was: "
                        + rootCause.getClass().getName());

        SQLException sqlException = (SQLException) rootCause;

        assertEquals(
                3819,
                sqlException.getErrorCode());

        assertEquals(
                "HY000",
                sqlException.getSQLState());

        assertTrue(
                sqlException.getMessage()
                        .contains("chk_task_proposal_status"),
                () -> "Unexpected constraint error: "
                        + sqlException.getMessage());
    }

    @Test
    @Transactional
    void v59CreatesAiCoreWithRequiredMysqlContract() {
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM flyway_schema_history
                WHERE version = '59' AND success = 1
                """, Integer.class));
        Set<String> aiTables = Set.of("ai_conversation", "ai_assistant_config", "ai_message", "ai_action_suggestion",
                "ai_usage_log", "ai_quota_config");
        assertEquals(6, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN (
                    'ai_conversation', 'ai_assistant_config', 'ai_message', 'ai_action_suggestion', 'ai_usage_log',
                    'ai_quota_config')
                """, Integer.class));
        aiTables.forEach(this::assertMysqlTable);

        assertTableContract("ai_conversation", new TableContract(Map.ofEntries(
                Map.entry("id", "bigint"), Map.entry("user_id", "bigint"),
                Map.entry("assistant_key", "varchar(50)"), Map.entry("module_context", "varchar(100)"),
                Map.entry("lab_id", "bigint"), Map.entry("project_id", "bigint"), Map.entry("group_id", "bigint"),
                Map.entry("title", "varchar(255)"), Map.entry("created_at", "timestamp(6)"),
                Map.entry("updated_at", "timestamp(6)"), Map.entry("active", "tinyint(1)"),
                Map.entry("deleted", "tinyint(1)")), Set.of("lab_id", "project_id", "group_id"),
                Map.of("active", "1", "deleted", "0", "created_at", "CURRENT_TIMESTAMP(6)", "updated_at",
                        "CURRENT_TIMESTAMP(6)"), true));
        assertTableContract("ai_assistant_config", new TableContract(Map.ofEntries(
                Map.entry("id", "bigint"), Map.entry("assistant_key", "varchar(50)"),
                Map.entry("enabled", "tinyint(1)"), Map.entry("system_prompt_key", "varchar(100)"),
                Map.entry("max_requests_per_day", "int"), Map.entry("max_context_tokens", "int"),
                Map.entry("created_at", "timestamp(6)"), Map.entry("updated_at", "timestamp(6)"),
                Map.entry("active", "tinyint(1)"), Map.entry("deleted", "tinyint(1)")), Set.of(),
                Map.of("enabled", "1", "active", "1", "deleted", "0", "created_at", "CURRENT_TIMESTAMP(6)",
                        "updated_at", "CURRENT_TIMESTAMP(6)"), true));
        assertTableContract("ai_message", new TableContract(Map.ofEntries(
                Map.entry("id", "bigint"), Map.entry("conversation_id", "bigint"), Map.entry("role", "varchar(20)"),
                Map.entry("content", "text"), Map.entry("created_at", "timestamp(6)"),
                Map.entry("active", "tinyint(1)"), Map.entry("deleted", "tinyint(1)")), Set.of(),
                Map.of("active", "1", "deleted", "0", "created_at", "CURRENT_TIMESTAMP(6)"), false));
        assertTableContract("ai_action_suggestion", new TableContract(Map.ofEntries(
                Map.entry("id", "bigint"), Map.entry("requested_by", "bigint"),
                Map.entry("assistant_key", "varchar(50)"), Map.entry("action_type", "varchar(100)"),
                Map.entry("target_module", "varchar(100)"), Map.entry("target_id", "bigint"),
                Map.entry("payload_json", "json"), Map.entry("status", "varchar(20)"),
                Map.entry("executed_by", "bigint"), Map.entry("rejected_reason", "text"),
                Map.entry("executed_at", "timestamp(6)"), Map.entry("created_at", "timestamp(6)"),
                Map.entry("updated_at", "timestamp(6)"), Map.entry("active", "tinyint(1)"),
                Map.entry("deleted", "tinyint(1)")), Set.of("target_id", "executed_by", "rejected_reason", "executed_at"),
                Map.of("status", "PENDING", "active", "1", "deleted", "0", "created_at", "CURRENT_TIMESTAMP(6)",
                        "updated_at", "CURRENT_TIMESTAMP(6)"), true));
        assertTableContract("ai_usage_log", new TableContract(Map.ofEntries(
                Map.entry("id", "bigint"), Map.entry("user_id", "bigint"),
                Map.entry("assistant_key", "varchar(50)"), Map.entry("role", "varchar(50)"),
                Map.entry("module", "varchar(100)"), Map.entry("lab_id", "bigint"),
                Map.entry("project_id", "bigint"), Map.entry("group_id", "bigint"),
                Map.entry("prompt_tokens", "int"), Map.entry("completion_tokens", "int"),
                Map.entry("status", "varchar(50)"), Map.entry("error_message", "text"),
                Map.entry("created_at", "timestamp(6)"), Map.entry("active", "tinyint(1)"),
                Map.entry("deleted", "tinyint(1)")), Set.of("lab_id", "project_id", "group_id", "error_message"),
                Map.of("prompt_tokens", "0", "completion_tokens", "0", "active", "1", "deleted", "0", "created_at",
                        "CURRENT_TIMESTAMP(6)"), false));
        assertTableContract("ai_quota_config", new TableContract(Map.ofEntries(
                Map.entry("id", "bigint"), Map.entry("assistant_key", "varchar(50)"), Map.entry("role", "varchar(50)"),
                Map.entry("module", "varchar(100)"), Map.entry("max_requests_per_day", "int"),
                Map.entry("max_context_tokens", "int"), Map.entry("enabled", "tinyint(1)"),
                Map.entry("created_at", "timestamp(6)"), Map.entry("updated_at", "timestamp(6)"),
                Map.entry("active", "tinyint(1)"), Map.entry("deleted", "tinyint(1)")), Set.of(),
                Map.of("enabled", "1", "active", "1", "deleted", "0", "created_at", "CURRENT_TIMESTAMP(6)",
                        "updated_at", "CURRENT_TIMESTAMP(6)"), true));

        assertIndex("ai_conversation", "idx_ai_conversation_user_assistant_module_updated",
                List.of("user_id", "assistant_key", "module_context", "updated_at", "id"));
        assertIndex("ai_conversation", "idx_ai_conversation_lab_updated", List.of("lab_id", "updated_at", "id"));
        assertIndex("ai_conversation", "idx_ai_conversation_project_updated",
                List.of("project_id", "updated_at", "id"));
        assertIndex("ai_conversation", "idx_ai_conversation_group_updated", List.of("group_id", "updated_at", "id"));
        assertIndex("ai_message", "idx_ai_message_conversation_created", List.of("conversation_id", "created_at", "id"));
        assertIndex("ai_action_suggestion", "idx_ai_action_suggestion_requested_status_created",
                List.of("requested_by", "status", "created_at", "id"));
        assertIndex("ai_action_suggestion", "idx_ai_action_suggestion_assistant_status_created",
                List.of("assistant_key", "status", "created_at", "id"));
        assertIndex("ai_action_suggestion", "idx_ai_action_suggestion_target", List.of("target_module", "target_id"));
        assertIndex("ai_action_suggestion", "idx_ai_action_suggestion_executed_created",
                List.of("executed_by", "executed_at", "id"));
        assertIndex("ai_usage_log", "idx_ai_usage_log_user_created", List.of("user_id", "created_at", "id"));
        assertIndex("ai_usage_log", "idx_ai_usage_log_assistant_role_module_created",
                List.of("assistant_key", "role", "module", "created_at", "id"));
        assertIndex("ai_usage_log", "idx_ai_usage_log_lab_created", List.of("lab_id", "created_at", "id"));
        assertIndex("ai_usage_log", "idx_ai_usage_log_project_created", List.of("project_id", "created_at", "id"));
        assertIndex("ai_usage_log", "idx_ai_usage_log_group_created", List.of("group_id", "created_at", "id"));
        assertIndex("ai_assistant_config", "uk_ai_assistant_config_assistant_key", List.of("assistant_key"));
        assertIndex("ai_quota_config", "uk_ai_quota_config_assistant_role_module",
                List.of("assistant_key", "role", "module"));

        assertConstraintTypes("ai_conversation", Map.of("chk_ai_conversation_assistant_key", "CHECK"));
        assertConstraintTypes("ai_assistant_config", Map.of("uk_ai_assistant_config_assistant_key", "UNIQUE",
                "chk_ai_assistant_config_assistant_key", "CHECK", "chk_ai_assistant_config_requests", "CHECK",
                "chk_ai_assistant_config_context", "CHECK"));
        assertConstraintTypes("ai_message", Map.of("chk_ai_message_role", "CHECK"));
        assertConstraintTypes("ai_action_suggestion", Map.of("chk_ai_action_suggestion_assistant_key", "CHECK",
                "chk_ai_action_suggestion_status", "CHECK"));
        assertConstraintTypes("ai_usage_log", Map.of("chk_ai_usage_log_assistant_key", "CHECK",
                "chk_ai_usage_log_prompt_tokens", "CHECK", "chk_ai_usage_log_completion_tokens", "CHECK"));
        assertConstraintTypes("ai_quota_config", Map.of("uk_ai_quota_config_assistant_role_module", "UNIQUE",
                "chk_ai_quota_config_assistant_key", "CHECK", "chk_ai_quota_config_requests", "CHECK",
                "chk_ai_quota_config_context", "CHECK"));

        Map<String, ForeignKey> conversationForeignKeys = foreignKeys("ai_conversation");
        assertRestrictiveForeignKey(new ForeignKey("user_id", "users", "id", "RESTRICT"),
                conversationForeignKeys.get("fk_ai_conversation_user"));
        assertRestrictiveForeignKey(new ForeignKey("lab_id", "laboratories", "id", "RESTRICT"),
                conversationForeignKeys.get("fk_ai_conversation_lab"));
        assertRestrictiveForeignKey(new ForeignKey("project_id", "projects", "id", "RESTRICT"),
                conversationForeignKeys.get("fk_ai_conversation_project"));
        assertRestrictiveForeignKey(new ForeignKey("group_id", "research_groups", "id", "RESTRICT"),
                conversationForeignKeys.get("fk_ai_conversation_group"));
        assertRestrictiveForeignKey(new ForeignKey("conversation_id", "ai_conversation", "id", "RESTRICT"),
                foreignKeys("ai_message").get("fk_ai_message_conversation"));
        Map<String, ForeignKey> suggestionForeignKeys = foreignKeys("ai_action_suggestion");
        assertRestrictiveForeignKey(new ForeignKey("requested_by", "users", "id", "RESTRICT"),
                suggestionForeignKeys.get("fk_ai_action_suggestion_requested_by"));
        assertRestrictiveForeignKey(new ForeignKey("executed_by", "users", "id", "RESTRICT"),
                suggestionForeignKeys.get("fk_ai_action_suggestion_executed_by"));
        Map<String, ForeignKey> usageForeignKeys = foreignKeys("ai_usage_log");
        assertRestrictiveForeignKey(new ForeignKey("user_id", "users", "id", "RESTRICT"),
                usageForeignKeys.get("fk_ai_usage_log_user"));
        assertRestrictiveForeignKey(new ForeignKey("lab_id", "laboratories", "id", "RESTRICT"),
                usageForeignKeys.get("fk_ai_usage_log_lab"));
        assertRestrictiveForeignKey(new ForeignKey("project_id", "projects", "id", "RESTRICT"),
                usageForeignKeys.get("fk_ai_usage_log_project"));
        assertRestrictiveForeignKey(new ForeignKey("group_id", "research_groups", "id", "RESTRICT"),
                usageForeignKeys.get("fk_ai_usage_log_group"));
        assertRestrictiveForeignKey(new ForeignKey("ai_action_suggestion_id", "ai_action_suggestion", "id", "RESTRICT"),
                foreignKeys("research_task_proposal").get("fk_task_proposal_ai_action_suggestion"));
        assertIndex("research_task_proposal", "idx_task_proposal_ai_suggestion", List.of("ai_action_suggestion_id"));

        Fixture fixture = fixture();
        jdbcTemplate.update("""
                INSERT INTO ai_conversation (user_id, assistant_key, module_context, lab_id, project_id, group_id, title)
                VALUES (?, 'LAB_ASSISTANT', 'research', ?, ?, ?, 'MySQL contract')
                """, fixture.proposer().getId(), fixture.project().getLab().getId(), fixture.project().getId(),
                fixture.group().getId());
        Long conversationId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update("INSERT INTO ai_message (conversation_id, `role`, content) VALUES (?, 'USER', 'hello')",
                conversationId);
        jdbcTemplate.update("""
                INSERT INTO ai_assistant_config (assistant_key, system_prompt_key, max_requests_per_day, max_context_tokens)
                VALUES ('LAB_ASSISTANT', 'mysql-it', 1, 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO ai_action_suggestion (requested_by, assistant_key, action_type, target_module, payload_json,
                    executed_by)
                VALUES (?, 'LAB_ASSISTANT', 'CREATE_TASK', 'research', JSON_OBJECT('title', 'Synthetic'), ?)
                """, fixture.proposer().getId(), fixture.reviewer().getId());
        Long suggestionId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update("""
                INSERT INTO ai_usage_log (user_id, assistant_key, `role`, `module`, lab_id, project_id, group_id,
                    status)
                VALUES (?, 'LAB_ASSISTANT', 'STUDENT', 'research', ?, ?, ?, 'SUCCESS')
                """, fixture.proposer().getId(), fixture.project().getLab().getId(), fixture.project().getId(),
                fixture.group().getId());
        jdbcTemplate.update("""
                INSERT INTO ai_quota_config (assistant_key, `role`, `module`, max_requests_per_day, max_context_tokens)
                VALUES ('LAB_ASSISTANT', 'STUDENT', 'research', 1, 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO research_task_proposal (
                    proposed_by, project_id, group_id, ai_action_suggestion_id, payload_json)
                VALUES (?, ?, ?, ?, ?)
                """, fixture.proposer().getId(), fixture.project().getId(), fixture.group().getId(), suggestionId,
                payload(TaskType.TASK, fixture.project().getId(), fixture.group().getId(), null));

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("INSERT INTO ai_message (conversation_id, `role`, content) VALUES (-1, 'USER', 'x')"));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                INSERT INTO ai_assistant_config (assistant_key, system_prompt_key, max_requests_per_day, max_context_tokens)
                VALUES ('INVALID', 'invalid', 1, 1)
                """));
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO ai_assistant_config (assistant_key, system_prompt_key, max_requests_per_day, max_context_tokens)
                VALUES ('LAB_ASSISTANT', 'duplicate', 1, 1)
                """));
    }

    @Test
    @Transactional
    void repositoryPersistsNativeJsonObjectAndReloadsParseEquivalentPayload() throws Exception {
        Fixture fixture = fixture();
        String payload = payload(TaskType.REVIEW, fixture.project().getId(), fixture.group().getId(),
                fixture.milestone().getId());
        TaskProposalEntity saved = taskProposalRepository.saveAndFlush(TaskProposalEntity.builder()
                .proposedById(fixture.proposer().getId()).projectId(fixture.project().getId())
                .groupId(fixture.group().getId())
                .milestoneId(fixture.milestone().getId()).payloadJson(payload).build());

        assertEquals("OBJECT",
                jdbcTemplate.queryForObject("SELECT JSON_TYPE(payload_json) FROM research_task_proposal WHERE id = ?",
                        String.class, saved.getId()));
        assertEquals(fixture.project().getId().toString(), jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.projectId')) FROM research_task_proposal WHERE id = ?",
                String.class, saved.getId()));
        assertEquals("Proposal title", jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.title')) FROM research_task_proposal WHERE id = ?",
                String.class, saved.getId()));
        assertEquals("REVIEW", jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.type')) FROM research_task_proposal WHERE id = ?",
                String.class, saved.getId()));

        entityManager.clear();
        TaskProposalEntity reloaded = taskProposalRepository.findByIdAndDeletedFalseAndActiveTrue(saved.getId())
                .orElseThrow();
        assertEquals(OBJECT_MAPPER.readTree(payload), OBJECT_MAPPER.readTree(reloaded.getPayloadJson()));
    }

    @Test
    @Transactional
    void proposalBlocksPhysicalDeletionOfAllReferencedOwners() {
        Fixture fixture = fixture();
        TaskProposalEntity proposal = taskProposalRepository.saveAndFlush(TaskProposalEntity.builder()
                .proposedById(fixture.proposer().getId()).reviewedById(fixture.reviewer().getId())
                .projectId(fixture.project().getId()).groupId(fixture.group().getId())
                .milestoneId(fixture.milestone().getId())
                .payloadJson(payload(TaskType.TASK, fixture.project().getId(), fixture.group().getId(),
                        fixture.milestone().getId()))
                .status(TaskProposalStatus.REJECTED)
                .reason("Needs more detail").reviewedAt(Instant.parse("2026-07-30T12:34:56Z")).build());

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", fixture.proposer().getId()));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", fixture.reviewer().getId()));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("DELETE FROM projects WHERE id = ?", fixture.project().getId()));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("DELETE FROM research_groups WHERE id = ?", fixture.group().getId()));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("DELETE FROM milestones WHERE id = ?", fixture.milestone().getId()));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM research_task_proposal WHERE id = ?",
                Integer.class, proposal.getId()));
    }

    private void assertMysqlTable(String tableName) {
        Map<String, Object> table = jdbcTemplate.queryForMap("""
                SELECT ENGINE, TABLE_COLLATION, SUBSTRING_INDEX(TABLE_COLLATION, '_', 1) AS TABLE_CHARSET
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
                """, tableName);
        assertEquals("InnoDB", table.get("ENGINE"));
        assertEquals("utf8mb4", table.get("TABLE_CHARSET"));
        assertEquals("utf8mb4_unicode_ci", table.get("TABLE_COLLATION"));
    }

    private void assertTableContract(String tableName, TableContract contract) {
        Map<String, Map<String, String>> actualColumns = columns(tableName);
        assertEquals(contract.columnTypes(), actualColumns.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey, entry -> entry.getValue().get("type"))));
        assertEquals(contract.nullableColumns(), actualColumns.entrySet().stream()
                .filter(entry -> "YES".equals(entry.getValue().get("nullable"))).map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet()));
        assertEquals("auto_increment", actualColumns.get("id").get("extra"));
        contract.defaults().forEach((column, expectedDefault) ->
            assertEquals(expectedDefault, actualColumns.get(column).get("default")));
        if (contract.updatedAtHasOnUpdate()) {
            assertTrue(actualColumns.get("updated_at").get("extra").toLowerCase(Locale.ROOT)
                    .contains("on update current_timestamp(6)"));
        }
    }

    private void assertConstraintTypes(String tableName, Map<String, String> expected) {
        assertEquals(expected, jdbcTemplate.query("""
                SELECT CONSTRAINT_NAME, CONSTRAINT_TYPE
                FROM information_schema.TABLE_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = ?
                  AND CONSTRAINT_TYPE IN ('CHECK', 'UNIQUE')
                """, resultSet -> {
            Map<String, String> result = new java.util.HashMap<>();
            while (resultSet.next())
                result.put(resultSet.getString("CONSTRAINT_NAME"), resultSet.getString("CONSTRAINT_TYPE"));
            return result;
        }, tableName));
    }

    private Map<String, Map<String, String>> columns(String tableName) {
        return jdbcTemplate.query("""
                SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, CAST(COLUMN_DEFAULT AS CHAR) AS COLUMN_DEFAULT, EXTRA
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
                """, resultSet -> {
            Map<String, Map<String, String>> result = new java.util.HashMap<>();
            while (resultSet.next())
                result.put(resultSet.getString("COLUMN_NAME"), Map.of(
                        "type", resultSet.getString("COLUMN_TYPE"), "nullable", resultSet.getString("IS_NULLABLE"),
                        "default", String.valueOf(resultSet.getString("COLUMN_DEFAULT")), "extra",
                        resultSet.getString("EXTRA")));
            return result;
        }, tableName);
    }

    private Map<String, ForeignKey> foreignKeys(String tableName) {
        return jdbcTemplate.query(
                """
                        SELECT kcu.CONSTRAINT_NAME, kcu.COLUMN_NAME, kcu.REFERENCED_TABLE_NAME, kcu.REFERENCED_COLUMN_NAME, rc.DELETE_RULE
                        FROM information_schema.KEY_COLUMN_USAGE kcu JOIN information_schema.REFERENTIAL_CONSTRAINTS rc
                          ON rc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA AND rc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME
                        WHERE kcu.CONSTRAINT_SCHEMA = DATABASE() AND kcu.TABLE_NAME = ?
                        """,
                resultSet -> {
                    Map<String, ForeignKey> result = new java.util.HashMap<>();
                    while (resultSet.next())
                        result.put(resultSet.getString(1), new ForeignKey(
                                resultSet.getString(2), resultSet.getString(3), resultSet.getString(4),
                                resultSet.getString(5)));
                    return result;
                }, tableName);
    }

    private void assertIndex(String tableName, String name, List<String> columns) {
        assertEquals(columns, jdbcTemplate.query("""
                SELECT COLUMN_NAME FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?
                ORDER BY SEQ_IN_INDEX
                """, (resultSet, rowNum) -> resultSet.getString(1), tableName, name));
    }

    private Fixture fixture() {
        User proposer = userFixture("proposer");
        User reviewer = userFixture("reviewer");
        String token = unique("scope");
        Laboratory laboratory = laboratoryRepository.saveAndFlush(laboratory(token));
        ProjectEntity project = projectRepository
                .saveAndFlush(ProjectEntity.builder().lab(laboratory).title(token + " project").build());
        GroupEntity group = groupRepository
                .saveAndFlush(GroupEntity.builder().lab(laboratory).leader(proposer).name(token + " group").build());
        MilestoneEntity milestone = milestoneRepository
                .saveAndFlush(MilestoneEntity.builder().project(project).title(token + " milestone").build());
        return new Fixture(proposer, reviewer, project, group, milestone);
    }

    private User userFixture(String label) {
        String token = unique(label);
        User user = new User();
        user.setUsername(trimTo(token, 50));
        user.setEmail(trimTo(token, 70) + "@mysql-it.example.test");
        user.setPassword("not-a-real-credential");
        return userRepository.saveAndFlush(user);
    }

    private static Laboratory laboratory(String token) {
        Laboratory laboratory = new Laboratory();
        laboratory.setLabName(trimTo(token, 100));
        laboratory.setLocation("MySQL integration test");
        laboratory.setCapacity(1);
        return laboratory;
    }

    private static String payload(TaskType type, Long projectId, Long groupId, Long milestoneId) {
        return """
                {"projectId":%d,"groupId":%d,"milestoneId":%s,"parentTaskId":null,
                "title":"Proposal title","description":null,"priority":"MEDIUM","type":"%s","dueDate":null}
                """.formatted(projectId, groupId, milestoneId == null ? "null" : milestoneId, type.name());
    }

    private static void assertRestrictiveForeignKey(ForeignKey expected, ForeignKey actual) {
        assertEquals(expected.column(), actual.column());
        assertEquals(expected.referencedTable(), actual.referencedTable());
        assertEquals(expected.referencedColumn(), actual.referencedColumn());
        assertTrue(
                "RESTRICT".equalsIgnoreCase(actual.deleteRule()) || "NO ACTION".equalsIgnoreCase(actual.deleteRule()));
    }

    private static String unique(String label) {
        return "mysql-it-" + label + "-" + SEQUENCE.incrementAndGet();
    }

    private static String trimTo(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank())
            throw new IllegalStateException(name + " must be set for the opt-in MySQL integration suite");
        return value;
    }

    private static void assertDisposableDatabase(String url) {
        int slash = url.lastIndexOf('/');
        int query = url.indexOf('?', slash);
        String database = slash < 0 ? "" : url.substring(slash + 1, query < 0 ? url.length() : query);
        if (!database.toLowerCase(Locale.ROOT).endsWith("_it"))
            throw new IllegalStateException("LAB_PORTAL_MYSQL_IT_URL must target a disposable database ending in _it");
    }

    private record Fixture(User proposer, User reviewer, ProjectEntity project, GroupEntity group,
            MilestoneEntity milestone) {
    }

    private record ForeignKey(String column, String referencedTable, String referencedColumn, String deleteRule) {
    }

    private record TableContract(Map<String, String> columnTypes, Set<String> nullableColumns,
            Map<String, String> defaults, boolean updatedAtHasOnUpdate) {
    }
}
