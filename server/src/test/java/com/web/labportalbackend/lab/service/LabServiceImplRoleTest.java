package com.web.labportalbackend.lab.service;

import com.web.labportalbackend.admin.audit.enums.AuditAction;
import com.web.labportalbackend.admin.audit.enums.AuditModule;
import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.booking.repository.CleaningRepository;
import com.web.labportalbackend.booking.repository.ComplaintRepository;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.entity.Membership;
import com.web.labportalbackend.lab.enums.LabMemberRole;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.lab.repository.MembershipRepository;
import com.web.labportalbackend.lab.service.impl.LabServiceImpl;
import com.web.labportalbackend.research.repository.ProjectRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LabServiceImplRoleTest {

    @Mock private LaboratoryRepository laboratoryRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private MembershipRepository membershipRepository;
    @Mock private TimeSlotRepository timeSlotRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private CleaningRepository cleaningRepository;
    @Mock private ComplaintRepository complaintRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private EntityManager entityManager;
    @InjectMocks private LabServiceImpl labService;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void assignedManagerCanSetDeputyAndSecretaryWithinTheirLaboratory() {
        authenticateManager();
        Laboratory lab = laboratory(10L);
        Membership membership = membership(lab, student(2L));
        when(laboratoryRepository.findManagedByIdForStatusAuthorization(10L, 1L)).thenReturn(Optional.of(lab));
        when(membershipRepository.findByUserIdAndLaboratoryIdAndDeletedFalse(2L, 10L))
                .thenReturn(Optional.of(membership));
        when(membershipRepository.save(membership)).thenReturn(membership);

        assertEquals("DEPUTY", labService.updateLabMemberRole(10L, 2L, LabMemberRole.DEPUTY).getRole());
        assertEquals("SECRETARY", labService.updateLabMemberRole(10L, 2L, LabMemberRole.SECRETARY).getRole());
        verify(auditLogService, times(2)).logCurrentUser(eq(AuditAction.CHANGE_LAB_MEMBER_ROLE),
                eq(AuditModule.LAB), eq("MEMBERSHIP"), eq(20L), anyString());
    }

    @Test
    void managerCannotChangeMemberOfAnotherLaboratory() {
        authenticateManager();
        when(laboratoryRepository.findManagedByIdForStatusAuthorization(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class,
                () -> labService.updateLabMemberRole(99L, 2L, LabMemberRole.DEPUTY));
        verifyNoInteractions(membershipRepository, auditLogService);
    }

    @Test
    void managerCannotAssignLaboratoryRoleToAnotherManager() {
        authenticateManager();
        Laboratory lab = laboratory(10L);
        User otherManager = student(2L);
        otherManager.addRole(new Role("LAB_MANAGER", "Lab manager"));
        Membership membership = membership(lab, otherManager);
        when(laboratoryRepository.findManagedByIdForStatusAuthorization(10L, 1L)).thenReturn(Optional.of(lab));
        when(membershipRepository.findByUserIdAndLaboratoryIdAndDeletedFalse(2L, 10L))
                .thenReturn(Optional.of(membership));

        assertThrows(AccessDeniedException.class,
                () -> labService.updateLabMemberRole(10L, 2L, LabMemberRole.SECRETARY));
        verify(membershipRepository, never()).save(membership);
        verifyNoInteractions(auditLogService);
    }

    private void authenticateManager() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("manager", null, List.of()));
        User manager = new User();
        manager.setId(1L);
        manager.setUsername("manager");
        manager.addRole(new Role("LAB_MANAGER", "Lab manager"));
        when(userRepository.findByUsername("manager")).thenReturn(Optional.of(manager));
    }

    private Laboratory laboratory(Long id) {
        Laboratory lab = new Laboratory();
        lab.setId(id);
        lab.setLabName("Research Lab");
        return lab;
    }

    private User student(Long id) {
        User member = new User();
        member.setId(id);
        member.setUsername("student");
        member.setEmail("student@example.invalid");
        member.addRole(new Role("STUDENT", "Student"));
        return member;
    }

    private Membership membership(Laboratory lab, User member) {
        Membership membership = new Membership();
        membership.setId(20L);
        membership.setLaboratory(lab);
        membership.setUser(member);
        membership.setRole("MEMBER");
        return membership;
    }
}
