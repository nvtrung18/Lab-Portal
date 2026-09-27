package com.web.labportalbackend.auth.config;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthDataSeederTest {

    @Mock
    private UserRepository userRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    @Test
    void startupPreservesChangedPasswordOfSeededUser() {
        User admin = new User();
        admin.setUsername("admin");
        String changedPasswordHash = passwordEncoder.encode("changed-password");
        admin.setPassword(changedPasswordHash);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

        new AuthDataSeeder(userRepository, passwordEncoder).run(new DefaultApplicationArguments(new String[0]));

        assertEquals(changedPasswordHash, admin.getPassword());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void startupEncodesOnlyLegacyPlaintextSeedPassword() {
        User admin = new User();
        admin.setUsername("admin");
        admin.setPassword("admin123");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

        new AuthDataSeeder(userRepository, passwordEncoder).run(new DefaultApplicationArguments(new String[0]));

        assertTrue(passwordEncoder.matches("admin123", admin.getPassword()));
        verify(userRepository).save(admin);
    }
}
