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

    private record SeedPassword(String username, String rawPassword) {}

    private static final SeedPassword[] DEFAULT_PASSWORDS = {
            new SeedPassword("admin", "admin123"),
            new SeedPassword("lab_manager", "manager123"),
            new SeedPassword("user1", "user123")
    };

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (SeedPassword seedPassword : DEFAULT_PASSWORDS) {
            userRepository.findByUsername(seedPassword.username()).ifPresent(user -> {
                if (!passwordEncoder.matches(seedPassword.rawPassword(), user.getPassword())) {
                    user.setPassword(passwordEncoder.encode(seedPassword.rawPassword()));
                    userRepository.save(user);
                    log.info("Default password re-encoded with BCrypt on startup for user: {}", seedPassword.username());
                }
            });
        }
    }
}
