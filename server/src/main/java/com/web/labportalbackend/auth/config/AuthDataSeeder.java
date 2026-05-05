package com.web.labportalbackend.auth.config;

import com.web.labportalbackend.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time data seeder that runs on application startup.
 * <p>
 * Ensures the default admin user has a correctly encoded password,
 * since Flyway migrations use raw SQL and cannot invoke BCrypt directly.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthDataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_DEFAULT_PASSWORD = "admin123";

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        userRepository.findByUsername(ADMIN_USERNAME).ifPresent(admin -> {
            // Only update if the password doesn't match (e.g., seed hash was wrong)
            if (!passwordEncoder.matches(ADMIN_DEFAULT_PASSWORD, admin.getPassword())) {
                admin.setPassword(passwordEncoder.encode(ADMIN_DEFAULT_PASSWORD));
                userRepository.save(admin);
                log.info("Admin password re-encoded with BCrypt on startup");
            }
        });
    }
}
