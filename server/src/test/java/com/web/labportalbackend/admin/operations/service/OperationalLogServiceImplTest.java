package com.web.labportalbackend.admin.operations.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.web.labportalbackend.admin.operations.service.impl.OperationalLogServiceImpl;
import com.web.labportalbackend.ai.repository.AiActionSuggestionRepository;
import com.web.labportalbackend.ai.repository.AiUsageLogRepository;
import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.face.repository.FaceCheckinLogRepository;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class OperationalLogServiceImplTest {

    @Mock AiUsageLogRepository aiUsageLogRepository;
    @Mock AiActionSuggestionRepository aiActionSuggestionRepository;
    @Mock FaceCheckinLogRepository faceCheckinLogRepository;
    @Mock UserRepository userRepository;
    @Mock LaboratoryRepository laboratoryRepository;

    private OperationalLogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OperationalLogServiceImpl(aiUsageLogRepository, aiActionSuggestionRepository,
                faceCheckinLogRepository, userRepository, laboratoryRepository);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("manager", "n/a", java.util.List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void managerCannotRequestFaceLogsForAnotherLaboratory() {
        User manager = user(2L, "manager", "LAB_MANAGER");
        Laboratory managedLab = new Laboratory();
        managedLab.setId(3L);
        managedLab.setActive(true);
        managedLab.setDeleted(false);
        when(userRepository.findByUsername("manager")).thenReturn(Optional.of(manager));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(2L)).thenReturn(Optional.of(managedLab));

        assertThrows(AccessDeniedException.class, () -> service.getFaceCheckins(
                null, 9L, null, null, null, null, PageRequest.of(0, 20)));

        verify(faceCheckinLogRepository, never()).findAll(
                org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<com.web.labportalbackend.face.entity.FaceCheckinLogEntity>>any(),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void managerCannotAccessAiUsageLogs() {
        when(userRepository.findByUsername("manager"))
                .thenReturn(Optional.of(user(2L, "manager", "LAB_MANAGER")));

        assertThrows(AccessDeniedException.class, () -> service.getAiUsage(
                null, null, null, null, null, PageRequest.of(0, 20)));

        verify(aiUsageLogRepository, never()).findAll(
                org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<com.web.labportalbackend.ai.entity.AiUsageLogEntity>>any(),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class));
    }

    private User user(Long id, String username, String roleName) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setActive(true);
        user.setDeleted(false);
        user.addRole(new Role(roleName, roleName));
        return user;
    }
}
