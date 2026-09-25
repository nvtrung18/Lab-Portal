package com.web.labportalbackend.auth.config;

import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.RoleRepository;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

/**
 * Creates a production administrator once, without assigning a built-in seed password.
 */
@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProductionAdminBootstrapper implements ApplicationRunner {

    private static final Set<String> RESERVED_SEED_USERNAMES = Set.of(
            "admin", "lab_manager", "user1",
            "manager01", "manager02", "manager03", "manager04", "manager05",
            "student01", "student02", "student03", "student04", "student05"
    );

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.auth.bootstrap-admin.username:}")
    private String bootstrapUsername;

    @Value("${app.auth.bootstrap-admin.email:}")
    private String bootstrapEmail;

    @Value("${app.auth.bootstrap-admin.password:}")
    private String bootstrapPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String username = requireText(bootstrapUsername, "BOOTSTRAP_ADMIN_USERNAME")
                .trim()
                .toLowerCase(Locale.ROOT);
        if (username.length() < 3 || username.length() > 50) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_USERNAME must be between 3 and 50 characters.");
        }
        if (RESERVED_SEED_USERNAMES.contains(username)) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_USERNAME must not use a built-in development seed username.");
        }

        User existing = userRepository.findByUsername(username).orElse(null);
        if (existing != null) {
            if (!existing.hasRole("ADMIN")
                    || !Boolean.TRUE.equals(existing.getActive())
                    || Boolean.TRUE.equals(existing.getDeleted())
                    || existing.getStatus() != UserStatus.ACTIVE) {
                throw new IllegalStateException(
                        "BOOTSTRAP_ADMIN_USERNAME already exists but is not an active administrator.");
            }
            log.info("Production bootstrap administrator already exists.");
            return;
        }

        String email = requireText(bootstrapEmail, "BOOTSTRAP_ADMIN_EMAIL")
                .trim()
                .toLowerCase(Locale.ROOT);
        if (email.length() > 100) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_EMAIL must be at most 100 characters.");
        }
        String password = requireText(bootstrapPassword, "BOOTSTRAP_ADMIN_PASSWORD");
        if (password.length() < 16 || password.length() > 100) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD must be between 16 and 100 characters.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_EMAIL is already assigned to a different user.");
        }

        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new IllegalStateException("Required ADMIN role is missing."));

        User admin = new User();
        admin.setEmail(email);
        admin.setUsername(username);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setFullName("Production Administrator");
        admin.setStatus(UserStatus.ACTIVE);
        admin.setActive(true);
        admin.setDeleted(false);
        admin.addRole(adminRole);
        userRepository.save(admin);

        log.info("Created the initial production administrator account.");
    }

    private String requireText(String value, String environmentVariable) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(environmentVariable + " must be configured for the first production startup.");
        }
        return value;
    }
}
