package com.web.labportalbackend.ai.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.web.labportalbackend.admin.audit.repository.AuditLogRepository;
import com.web.labportalbackend.ai.context.impl.AiContextFreshReadExecutor;
import com.web.labportalbackend.ai.entity.AiAssistantConfigEntity;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiAssistantSystemRole;
import com.web.labportalbackend.ai.enums.AiCapability;
import com.web.labportalbackend.ai.enums.AiRequestedAction;
import com.web.labportalbackend.ai.enums.AiResourceType;
import com.web.labportalbackend.ai.repository.AiAssistantConfigRepository;
import com.web.labportalbackend.ai.service.AiCapabilityDecision;
import com.web.labportalbackend.ai.service.AiCapabilityRequest;
import com.web.labportalbackend.ai.service.AiCapabilityResolver;
import com.web.labportalbackend.ai.service.impl.AiCapabilityResolverImpl;
import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.RoleRepository;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.enums.LabStatus;
import com.web.labportalbackend.common.enums.UserStatus;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Real-MySQL-only evidence; H2 cannot prove committed visibility or the DATE function contract. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "LAB_PORTAL_MYSQL_IT", matches = "(?i:true)")
@Import(AiContextFreshAuthorizationMySqlIntegrationTest.RoleRevokeTestConfiguration.class)
class AiContextFreshAuthorizationMySqlIntegrationTest {

    private static final String DEFAULT_URL = "jdbc:mysql://127.0.0.1:3307/lab_portal_it";
    private static final AtomicLong SEQUENCE = new AtomicLong(System.currentTimeMillis());

    @Autowired RoleRevokingCapabilityResolver resolver;
    @Autowired AiContextFreshReadExecutor executor;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired LaboratoryRepository labs;
    @Autowired AiAssistantConfigRepository assistantConfigs;
    @Autowired AuditLogRepository auditLogs;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        String url = environmentOrDefault("LAB_PORTAL_MYSQL_IT_URL", DEFAULT_URL);
        assertDisposableDatabase(url);
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> requiredEnvironment("LAB_PORTAL_MYSQL_IT_USERNAME"));
        registry.add("spring.datasource.password", () -> requiredEnvironment("LAB_PORTAL_MYSQL_IT_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.clean-disabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void committedRoleRevokeBetweenPreflightAndFreshReadFailsGenericallyAndMysqlAuditDateQueryExecutes() {
        String token = "fresh-context-" + SEQUENCE.incrementAndGet();
        User actor = student(token);
        Role studentRole = roles.findByName("STUDENT").orElseThrow();
        users.saveAndFlush(actor);
        actor.addRole(studentRole);
        users.saveAndFlush(actor);
        Laboratory lab = labs.saveAndFlush(lab(token));
        AiAssistantConfigEntity config = assistantConfigs
                .findByAssistantKeyAndActiveTrueAndDeletedFalse(AiAssistantKey.LAB_ASSISTANT)
                .orElseGet(() -> AiAssistantConfigEntity.builder().assistantKey(AiAssistantKey.LAB_ASSISTANT)
                        .enabled(true).systemPromptKey(token).maxRequestsPerDay(1).maxContextTokens(1).build());
        config.setEnabled(true);
        assistantConfigs.saveAndFlush(config);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor.getUsername(), null, java.util.List.of()));
        AiCapabilityRequest request = new AiCapabilityRequest(AiAssistantKey.LAB_ASSISTANT, actor.getId(),
                AiCapability.LAB_POLICY_READ,
                new AiCapabilityRequest.ResourceReference(AiResourceType.LABORATORY, lab.getId()), null,
                AiRequestedAction.READ);
        var preliminary = resolver.requireAllowed(request);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            User current = users.findById(actor.getId()).orElseThrow();
            current.getRoles().clear();
            users.saveAndFlush(current);
        });

        assertThrows(AiContextReadDeniedException.class, () -> executor.execute(preliminary, request, token));
        auditLogs.findAiContextAuditBuckets(actor.getId(), Instant.parse("2026-01-01T00:00:00Z"),
                org.springframework.data.domain.PageRequest.of(0, 1));
    }

    @Test
    void selectedStudentRoleRevocationAfterFreshDecisionDeniesTrustedRoleNameProjectionWhileManagerRemains() {
        String token = "fresh-context-dual-role-" + SEQUENCE.incrementAndGet();
        User actor = dualRole(token);
        Laboratory lab = labs.saveAndFlush(lab(token));
        enableLabAssistant(token);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor.getUsername(), null, java.util.List.of()));
        AiCapabilityRequest request = new AiCapabilityRequest(AiAssistantKey.LAB_ASSISTANT, actor.getId(),
                AiCapability.LAB_POLICY_READ,
                new AiCapabilityRequest.ResourceReference(AiResourceType.LABORATORY, lab.getId()), null,
                AiRequestedAction.READ);
        AiCapabilityDecision preliminary = resolver.requireAllowed(request);
        assertEquals(AiAssistantSystemRole.STUDENT, preliminary.selectedSystemRole());

        resolver.revokeSelectedRoleAfterNextAllowedDecision(actor.getId(), AiAssistantSystemRole.STUDENT);

        assertThrows(AiContextReadDeniedException.class, () -> executor.execute(preliminary, request, token));

        AiCapabilityDecision freshDecision = resolver.revokedDecision();
        assertNotNull(freshDecision);
        assertEquals(AiAssistantSystemRole.STUDENT, freshDecision.selectedSystemRole());
        assertEquals(Set.of("LAB_MANAGER"), users.findById(actor.getId()).orElseThrow().getRoles().stream()
                .map(Role::getName).collect(java.util.stream.Collectors.toSet()));
        assertTrue(labs.findAiContextLaboratory(actor.getId(), lab.getId(), AiAssistantSystemRole.STUDENT.name())
                .isEmpty(), "The projection must recheck the resolver-selected varchar role after revocation");
        assertTrue(labs.findAiContextLaboratory(actor.getId(), lab.getId(), AiAssistantSystemRole.LAB_MANAGER.name())
                .isPresent(), "The retained role proves the selected role, not generic role presence, denied projection");
    }

    private User student(String token) {
        User user = new User();
        user.setUsername(token);
        user.setEmail(token + "@mysql-it.example.test");
        user.setPassword("not-a-real-credential");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private User dualRole(String token) {
        User user = student(token);
        users.saveAndFlush(user);
        user.addRole(roles.findByName(AiAssistantSystemRole.STUDENT.name()).orElseThrow());
        user.addRole(roles.findByName(AiAssistantSystemRole.LAB_MANAGER.name()).orElseThrow());
        return users.saveAndFlush(user);
    }

    private void enableLabAssistant(String token) {
        AiAssistantConfigEntity config = assistantConfigs
                .findByAssistantKeyAndActiveTrueAndDeletedFalse(AiAssistantKey.LAB_ASSISTANT)
                .orElseGet(() -> AiAssistantConfigEntity.builder().assistantKey(AiAssistantKey.LAB_ASSISTANT)
                        .enabled(true).systemPromptKey(token).maxRequestsPerDay(1).maxContextTokens(1).build());
        config.setEnabled(true);
        assistantConfigs.saveAndFlush(config);
    }

    private Laboratory lab(String token) {
        Laboratory lab = new Laboratory();
        lab.setLabName(token);
        lab.setLocation("MySQL integration test");
        lab.setCapacity(1);
        lab.setStatus(LabStatus.AVAILABLE);
        return lab;
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

    @TestConfiguration(proxyBeanMethods = false)
    static class RoleRevokeTestConfiguration {

        @Bean
        @Primary
        RoleRevokingCapabilityResolver roleRevokingCapabilityResolver(
                AiCapabilityResolverImpl delegate,
                UserRepository users,
                RoleRepository roles,
                PlatformTransactionManager transactionManager) {
            return new RoleRevokingCapabilityResolver(delegate, users, roles, transactionManager);
        }
    }

    static class RoleRevokingCapabilityResolver implements AiCapabilityResolver {

        private final AiCapabilityResolver delegate;
        private final UserRepository users;
        private final RoleRepository roles;
        private final TransactionTemplate revokeTransaction;
        private final AtomicReference<Revocation> armedRevocation = new AtomicReference<>();
        private final AtomicReference<AiCapabilityDecision> revokedDecision = new AtomicReference<>();

        RoleRevokingCapabilityResolver(AiCapabilityResolver delegate,
                                       UserRepository users,
                                       RoleRepository roles,
                                       PlatformTransactionManager transactionManager) {
            this.delegate = delegate;
            this.users = users;
            this.roles = roles;
            this.revokeTransaction = new TransactionTemplate(transactionManager);
            this.revokeTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }

        @Override
        public AiCapabilityDecision resolve(AiCapabilityRequest request) {
            return delegate.resolve(request);
        }

        @Override
        public AiCapabilityDecision requireAllowed(AiCapabilityRequest request) {
            AiCapabilityDecision decision = delegate.requireAllowed(request);
            Revocation revocation = armedRevocation.getAndSet(null);
            if (revocation != null && decision.acceptedActorId().equals(revocation.actorId())
                    && decision.selectedSystemRole() == revocation.selectedRole()) {
                revokeTransaction.executeWithoutResult(status -> {
                    User current = users.findById(revocation.actorId()).orElseThrow();
                    current.removeRole(roles.findByName(revocation.selectedRole().name()).orElseThrow());
                    users.saveAndFlush(current);
                });
                revokedDecision.set(decision);
            }
            return decision;
        }

        void revokeSelectedRoleAfterNextAllowedDecision(Long actorId, AiAssistantSystemRole selectedRole) {
            revokedDecision.set(null);
            armedRevocation.set(new Revocation(actorId, selectedRole));
        }

        AiCapabilityDecision revokedDecision() {
            return revokedDecision.get();
        }

        private record Revocation(Long actorId, AiAssistantSystemRole selectedRole) {
        }
    }
}
