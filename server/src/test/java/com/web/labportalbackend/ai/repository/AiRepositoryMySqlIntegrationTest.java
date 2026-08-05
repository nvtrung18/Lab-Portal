package com.web.labportalbackend.ai.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.labportalbackend.ai.entity.AiActionSuggestionEntity;
import com.web.labportalbackend.ai.entity.AiAssistantConfigEntity;
import com.web.labportalbackend.ai.entity.AiConversationEntity;
import com.web.labportalbackend.ai.entity.AiMessageEntity;
import com.web.labportalbackend.ai.entity.AiQuotaConfigEntity;
import com.web.labportalbackend.ai.entity.AiUsageLogEntity;
import com.web.labportalbackend.ai.enums.AiActionSuggestionStatus;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiMessageRole;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/** Opt-in MySQL/Flyway evidence for the V59 AI JPA mapping contract. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "LAB_PORTAL_MYSQL_IT", matches = "(?i:true)")
class AiRepositoryMySqlIntegrationTest {

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
    AiConversationRepository conversationRepository;
    @Autowired
    AiMessageRepository messageRepository;
    @Autowired
    AiAssistantConfigRepository assistantConfigRepository;
    @Autowired
    AiActionSuggestionRepository actionSuggestionRepository;
    @Autowired
    AiUsageLogRepository usageLogRepository;
    @Autowired
    AiQuotaConfigRepository quotaConfigRepository;
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
    void repositoriesPersistAndReloadV59AiMappings() throws Exception {
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM flyway_schema_history
                WHERE version = '59' AND success = 1
                """, Integer.class));
        Fixture fixture = fixture();
        String payload = "{\"title\":\"Synthetic AI action\",\"priority\":\"MEDIUM\"}";

        AiConversationEntity conversation = conversationRepository.saveAndFlush(AiConversationEntity.builder()
                .userId(fixture.user().getId()).assistantKey(AiAssistantKey.LAB_ASSISTANT)
                .moduleContext("research").labId(fixture.laboratory().getId())
                .projectId(fixture.project().getId()).groupId(fixture.group().getId()).title("AI conversation").build());
        AiMessageEntity message = messageRepository.saveAndFlush(AiMessageEntity.builder()
                .conversationId(conversation.getId()).role(AiMessageRole.USER).content("hello").build());
        AiAssistantConfigEntity assistantConfig = assistantConfigRepository.saveAndFlush(AiAssistantConfigEntity.builder()
                .assistantKey(AiAssistantKey.LAB_ASSISTANT).systemPromptKey("mysql-it")
                .maxRequestsPerDay(10).maxContextTokens(100).build());
        AiActionSuggestionEntity suggestion = actionSuggestionRepository.saveAndFlush(AiActionSuggestionEntity.builder()
                .requestedById(fixture.user().getId()).assistantKey(AiAssistantKey.LAB_ASSISTANT)
                .actionType("CREATE_TASK").targetModule("research").targetId(fixture.project().getId())
                .payloadJson(payload).status(AiActionSuggestionStatus.EDITED).executedById(fixture.reviewer().getId()).build());
        Instant windowStart = Instant.parse("2026-08-05T00:00:00Z");
        Instant windowEnd = Instant.parse("2026-08-06T00:00:00Z");
        AiUsageLogEntity usageLog = usageLogRepository.saveAndFlush(AiUsageLogEntity.builder()
                .userId(fixture.user().getId()).assistantKey(AiAssistantKey.LAB_ASSISTANT).role("STUDENT")
                .module("research").labId(fixture.laboratory().getId()).projectId(fixture.project().getId())
                .groupId(fixture.group().getId()).promptTokens(12).completionTokens(34).status("SUCCESS")
                .createdAt(windowStart).build());
        usageLogRepository.saveAndFlush(AiUsageLogEntity.builder()
                .userId(fixture.user().getId()).assistantKey(AiAssistantKey.LAB_ASSISTANT).role("STUDENT")
                .module("research").promptTokens(1).completionTokens(2).status("ERROR")
                .createdAt(windowStart.plusSeconds(1)).build());
        usageLogRepository.saveAndFlush(AiUsageLogEntity.builder()
                .userId(fixture.user().getId()).assistantKey(AiAssistantKey.LAB_ASSISTANT).role("TEACHER")
                .module("other").promptTokens(1).completionTokens(2).status("PENDING")
                .createdAt(windowStart.plusSeconds(2)).build());
        AiQuotaConfigEntity quotaConfig = quotaConfigRepository.saveAndFlush(AiQuotaConfigEntity.builder()
                .assistantKey(AiAssistantKey.LAB_ASSISTANT).role("STUDENT").module("research")
                .maxRequestsPerDay(20).maxContextTokens(200).build());

        entityManager.clear();

        assertEquals(AiAssistantKey.LAB_ASSISTANT,
                conversationRepository.findById(conversation.getId()).orElseThrow().getAssistantKey());
        assertEquals("LAB_ASSISTANT", jdbcTemplate.queryForObject(
                "SELECT assistant_key FROM ai_conversation WHERE id = ?", String.class, conversation.getId()));
        AiMessageEntity reloadedMessage = messageRepository.findById(message.getId()).orElseThrow();
        assertEquals(AiMessageRole.USER, reloadedMessage.getRole());
        assertEquals("USER", jdbcTemplate.queryForObject(
                "SELECT `role` FROM ai_message WHERE id = ?", String.class, message.getId()));
        assertReducedAuditDefaults(reloadedMessage.getCreatedAt(), reloadedMessage.getActive(), reloadedMessage.getDeleted());
        assertTrue(assistantConfigRepository.findById(assistantConfig.getId()).orElseThrow().getEnabled());
        AiActionSuggestionEntity reloadedSuggestion = actionSuggestionRepository.findById(suggestion.getId()).orElseThrow();
        assertEquals(AiActionSuggestionStatus.EDITED, reloadedSuggestion.getStatus());
        assertEquals("EDITED", jdbcTemplate.queryForObject(
                "SELECT status FROM ai_action_suggestion WHERE id = ?", String.class, suggestion.getId()));
        assertEquals(OBJECT_MAPPER.readTree(payload), OBJECT_MAPPER.readTree(reloadedSuggestion.getPayloadJson()));
        AiUsageLogEntity reloadedUsageLog = usageLogRepository.findById(usageLog.getId()).orElseThrow();
        assertEquals(AiAssistantKey.LAB_ASSISTANT, reloadedUsageLog.getAssistantKey());
        assertReducedAuditDefaults(reloadedUsageLog.getCreatedAt(), reloadedUsageLog.getActive(), reloadedUsageLog.getDeleted());
        assertTrue(quotaConfigRepository.findById(quotaConfig.getId()).orElseThrow().getEnabled());
        assertEquals(3, usageLogRepository
                .countByUserIdAndAssistantKeyAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndActiveTrueAndDeletedFalse(
                        fixture.user().getId(), AiAssistantKey.LAB_ASSISTANT, windowStart, windowEnd));
        assertEquals(2, usageLogRepository
                .countByUserIdAndAssistantKeyAndRoleAndModuleAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndActiveTrueAndDeletedFalse(
                        fixture.user().getId(), AiAssistantKey.LAB_ASSISTANT, "STUDENT", "research", windowStart,
                        windowEnd));
        assertEquals("OBJECT", jdbcTemplate.queryForObject(
                "SELECT JSON_TYPE(payload_json) FROM ai_action_suggestion WHERE id = ?", String.class, suggestion.getId()));
    }

    private static void assertReducedAuditDefaults(java.time.Instant createdAt, Boolean active, Boolean deleted) {
        assertNotNull(createdAt);
        assertTrue(active);
        assertFalse(deleted);
    }

    private Fixture fixture() {
        String token = unique("ai");
        User user = userFixture(token + "-user");
        User reviewer = userFixture(token + "-reviewer");
        Laboratory laboratory = laboratoryRepository.saveAndFlush(laboratory(token));
        ProjectEntity project = projectRepository.saveAndFlush(ProjectEntity.builder().lab(laboratory)
                .title(token + " project").build());
        GroupEntity group = groupRepository.saveAndFlush(GroupEntity.builder().lab(laboratory).leader(user)
                .name(token + " group").build());
        return new Fixture(user, reviewer, laboratory, project, group);
    }

    private User userFixture(String token) {
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

    private record Fixture(User user, User reviewer, Laboratory laboratory, ProjectEntity project, GroupEntity group) {
    }
}
