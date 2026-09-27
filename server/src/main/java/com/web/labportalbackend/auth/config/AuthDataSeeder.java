package com.web.labportalbackend.auth.config;

import com.web.labportalbackend.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Upgrades legacy plaintext seed passwords on non-production profiles.
 * <p>
 * Current migrations already store BCrypt hashes. Existing or user-changed
 * encoded passwords must remain untouched when the application restarts.
 */
@Slf4j
@Component
@Profile("!prod")
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
                if (seedPassword.rawPassword().equals(user.getPassword())) {
                    user.setPassword(passwordEncoder.encode(seedPassword.rawPassword()));
                    userRepository.save(user);
                    log.info("Legacy plaintext seed password encoded on startup for user: {}", seedPassword.username());
                }
            });
        }
    }
}
