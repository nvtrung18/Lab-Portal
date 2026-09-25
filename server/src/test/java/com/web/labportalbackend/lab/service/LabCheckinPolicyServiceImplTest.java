package com.web.labportalbackend.lab.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryAllowedNetworkRepository;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.lab.service.impl.LabCheckinPolicyServiceImpl;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class LabCheckinPolicyServiceImplTest {

    private final LaboratoryRepository laboratoryRepository = mock(LaboratoryRepository.class);
    private final LaboratoryAllowedNetworkRepository networkRepository = mock(LaboratoryAllowedNetworkRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final User manager = mock(User.class);
    private final User admin = mock(User.class);
    private final Laboratory laboratory = mock(Laboratory.class);
    private LabCheckinPolicyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LabCheckinPolicyServiceImpl(laboratoryRepository, networkRepository,
                userRepository, auditLogService);
        when(userRepository.findByUsername("manager")).thenReturn(Optional.of(manager));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(manager.getId()).thenReturn(3L);
        when(manager.hasRole("ADMIN")).thenReturn(false);
        when(manager.hasRole("LAB_MANAGER")).thenReturn(true);
        when(admin.hasRole("ADMIN")).thenReturn(true);
        when(admin.hasRole("LAB_MANAGER")).thenReturn(false);
        when(laboratory.getId()).thenReturn(5L);
        when(laboratory.getActive()).thenReturn(true);
        when(laboratory.getDeleted()).thenReturn(false);
        when(laboratoryRepository.findById(5L)).thenReturn(Optional.of(laboratory));
        when(laboratoryRepository.findManagedByIdForStatusAuthorization(5L, 3L))
                .thenReturn(Optional.of(laboratory));
        when(networkRepository.findFirstByLaboratoryIdAndNetworkValueOrderByIdDesc(5L, "203.0.113.42/32"))
                .thenReturn(Optional.empty());
        when(networkRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("manager", "n/a", List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void managerCannotRegisterCurrentNetworkForTheManagedLab() {
        assertThrows(AccessDeniedException.class,
                () -> service.registerCurrentNetwork(5L, "203.0.113.42"));
    }

    @Test
    void adminCanRegisterCurrentNetworkForAnyActiveLab() {
        authenticateAsAdmin();

        var response = service.registerCurrentNetwork(5L, "203.0.113.42");

        assertEquals("203.0.113.42/32", response.network());
    }

    @Test
    void reactivatesAPreviouslyDisabledNetworkInsteadOfCreatingAHistoryDuplicate() {
        authenticateAsAdmin();
        var existing = mock(com.web.labportalbackend.lab.entity.LaboratoryAllowedNetwork.class);
        when(existing.getNetworkValue()).thenReturn("203.0.113.42/32");
        when(existing.getActive()).thenReturn(false);
        when(existing.getDeleted()).thenReturn(true);
        when(networkRepository.findFirstByLaboratoryIdAndNetworkValueOrderByIdDesc(5L, "203.0.113.42/32"))
                .thenReturn(Optional.of(existing));

        service.registerCurrentNetwork(5L, "203.0.113.42");

        org.mockito.Mockito.verify(existing).setActive(true);
        org.mockito.Mockito.verify(existing).setDeleted(false);
        org.mockito.Mockito.verify(networkRepository).save(existing);
    }

    @Test
    void managerCannotUpdateAnotherLaboratory() {
        when(laboratoryRepository.findManagedByIdForStatusAuthorization(6L, 3L))
                .thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class,
                () -> service.registerCurrentNetwork(6L, "203.0.113.42"));
    }

    @Test
    void invalidNetworkIsRejectedBeforePersistence() {
        authenticateAsAdmin();
        assertThrows(IllegalArgumentException.class,
                () -> service.registerCurrentNetwork(5L, "lab.example.com"));
    }

    private void authenticateAsAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "n/a", List.of()));
    }
}
