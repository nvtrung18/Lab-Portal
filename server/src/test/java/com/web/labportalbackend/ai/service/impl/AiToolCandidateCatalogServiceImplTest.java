package com.web.labportalbackend.ai.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.web.labportalbackend.ai.context.AiLabContext;
import com.web.labportalbackend.ai.enums.AiAssistantSystemRole;
import com.web.labportalbackend.ai.enums.AiToolId;
import com.web.labportalbackend.ai.service.AiCurrentActor;
import com.web.labportalbackend.ai.service.AiCurrentActorProvider;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.enums.LabStatus;
import com.web.labportalbackend.common.enums.TimeSlotStatus;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiToolCandidateCatalogServiceImplTest {

    @Mock private AiCurrentActorProvider actorProvider;
    @Mock private LaboratoryRepository laboratoryRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private com.web.labportalbackend.ai.service.AiCapabilityResolver capabilityResolver;

    private AiToolCandidateCatalogServiceImpl catalog;

    @BeforeEach
    void setUp() {
        catalog = new AiToolCandidateCatalogServiceImpl(actorProvider, new AiToolRegistryServiceImpl(),
                laboratoryRepository, bookingRepository, projectRepository, groupRepository, taskRepository,
                capabilityResolver, new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @Test
    void adminReceivesOnlyCurrentGlobalAdminCandidates() {
        when(actorProvider.requireCurrentActor()).thenReturn(new AiCurrentActor(1L, AiAssistantSystemRole.ADMIN));

        var candidates = catalog.candidates();

        assertEquals(List.of(AiToolId.ADMIN_SYSTEM_SUMMARY, AiToolId.ADMIN_AUDIT_SUMMARY,
                AiToolId.ADMIN_CONFIG_DRAFT), candidates.stream().map(candidate -> candidate.toolId()).toList());
        verifyNoInteractions(laboratoryRepository, bookingRepository, projectRepository, groupRepository, taskRepository);
    }

    @Test
    void explicitAccountIdIsAuthorizedBeforeItBecomesAPlanningCandidate() {
        when(actorProvider.requireCurrentActor()).thenReturn(new AiCurrentActor(1L, AiAssistantSystemRole.ADMIN));
        var decision = org.mockito.Mockito.mock(com.web.labportalbackend.ai.service.AiCapabilityDecision.class);
        when(decision.allowed()).thenReturn(true);
        when(decision.resolvedResource()).thenReturn(new com.web.labportalbackend.ai.service.AiCapabilityDecision.ResolvedResource(
                com.web.labportalbackend.ai.enums.AiResourceType.USER_ACCOUNT, 42L, null, null, null, null,
                com.web.labportalbackend.ai.enums.AiResourceScope.EXISTING_BUSINESS_PERMISSION));
        when(capabilityResolver.resolve(any())).thenReturn(decision);

        var candidates = catalog.candidates("Kiểm tra tài khoản ID 42.");

        assertEquals(2, candidates.size());
        assertTrue(candidates.stream().allMatch(c -> c.resource().resourceId().equals(42L)));
        assertTrue(candidates.stream().anyMatch(c -> c.toolId() == AiToolId.ADMIN_USER_STATUS_LOOKUP));
        assertTrue(candidates.stream().anyMatch(c -> c.toolId() == AiToolId.ADMIN_ACCOUNT_ACTION_DRAFT));
    }

    @Test
    void unauthorizedExplicitIdDoesNotFallBackToAnotherResourceOrGlobalTool() {
        when(actorProvider.requireCurrentActor()).thenReturn(new AiCurrentActor(1L, AiAssistantSystemRole.ADMIN));
        when(capabilityResolver.resolve(any())).thenReturn(null);

        assertTrue(catalog.candidates("Kiểm tra tài khoản 999999.").isEmpty());
    }

    @Test
    void explicitParentMismatchCannotReenterThroughTheDefaultCatalog() {
        when(actorProvider.requireCurrentActor()).thenReturn(new AiCurrentActor(7L, AiAssistantSystemRole.STUDENT));
        var decision = org.mockito.Mockito.mock(com.web.labportalbackend.ai.service.AiCapabilityDecision.class);
        when(decision.allowed()).thenReturn(true);
        when(decision.resolvedResource()).thenReturn(new com.web.labportalbackend.ai.service.AiCapabilityDecision.ResolvedResource(
                com.web.labportalbackend.ai.enums.AiResourceType.GROUP, 30L, 10L, 99L, 30L, null,
                com.web.labportalbackend.ai.enums.AiResourceScope.GROUP_MEMBER));
        when(capabilityResolver.resolve(any())).thenAnswer(call -> {
            com.web.labportalbackend.ai.service.AiCapabilityRequest request = call.getArgument(0);
            if (request.capability() == com.web.labportalbackend.ai.enums.AiCapability.RESEARCH_GROUP_SUMMARY) return decision;
            return null;
        });

        assertTrue(catalog.candidates("Tóm tắt group 30 thuộc project 20").isEmpty());
        verifyNoInteractions(groupRepository, projectRepository, taskRepository);
    }

    @Test
    void studentCandidatesContainOnlyJoinedLabsAndOwnBookings() {
        when(actorProvider.requireCurrentActor()).thenReturn(new AiCurrentActor(7L, AiAssistantSystemRole.STUDENT));
        when(laboratoryRepository.findAiCandidateLabsForStudent(org.mockito.ArgumentMatchers.eq(7L), any()))
                .thenReturn(List.of(new AiLabContext.Laboratory(10L, "AI Lab", LabStatus.AVAILABLE)));
        when(bookingRepository.findAiCandidateOwnBookings(org.mockito.ArgumentMatchers.eq(7L), any()))
                .thenReturn(List.of(new AiLabContext.OwnBooking(30L, BookingStatus.APPROVED,
                        new AiLabContext.Slot(20L, Instant.parse("2026-09-06T08:00:00Z"),
                                Instant.parse("2026-09-06T10:00:00Z"), TimeSlotStatus.AVAILABLE))));
        when(projectRepository.findAiToolCandidateProjects(org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq("STUDENT"), any())).thenReturn(List.of());
        when(groupRepository.findAiToolCandidateGroups(org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq("STUDENT"), any())).thenReturn(List.of());
        when(taskRepository.findAiToolCandidateTasks(org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq("STUDENT"), any())).thenReturn(List.of());

        var candidates = catalog.candidates();

        assertEquals(List.of(AiToolId.LAB_AVAILABLE_SLOTS_READ, AiToolId.LAB_POLICY_READ,
                        AiToolId.LAB_OWN_BOOKING_READ),
                candidates.stream().map(candidate -> candidate.toolId()).toList());
        assertTrue(candidates.stream().allMatch(candidate -> candidate.description().length() <= 512));
        assertTrue(candidates.stream()
                .filter(candidate -> candidate.toolId() == AiToolId.LAB_POLICY_READ)
                .allMatch(candidate -> candidate.description().contains("authorized knowledge documents")));
    }

    @Test
    void managerCandidatesAreBoundToTheManagedLabOnly() {
        when(actorProvider.requireCurrentActor()).thenReturn(
                new AiCurrentActor(8L, AiAssistantSystemRole.LAB_MANAGER));
        Laboratory lab = new Laboratory();
        lab.setId(10L);
        lab.setLabName("AI Lab");
        lab.setActive(true);
        lab.setDeleted(false);
        when(laboratoryRepository.findAiCandidateLabsForManager(org.mockito.ArgumentMatchers.eq(8L), any()))
                .thenReturn(List.of(new AiLabContext.Laboratory(10L, "AI Lab", LabStatus.AVAILABLE, 30)));
        when(projectRepository.findAiToolCandidateProjects(org.mockito.ArgumentMatchers.eq(8L),
                org.mockito.ArgumentMatchers.eq("LAB_MANAGER"), any())).thenReturn(List.of());
        when(groupRepository.findAiToolCandidateGroups(org.mockito.ArgumentMatchers.eq(8L),
                org.mockito.ArgumentMatchers.eq("LAB_MANAGER"), any())).thenReturn(List.of());
        when(taskRepository.findAiToolCandidateTasks(org.mockito.ArgumentMatchers.eq(8L),
                org.mockito.ArgumentMatchers.eq("LAB_MANAGER"), any())).thenReturn(List.of());

        var candidates = catalog.candidates();

        assertEquals(List.of(AiToolId.LAB_AVAILABLE_SLOTS_READ, AiToolId.LAB_MANAGED_SUMMARY,
                        AiToolId.LAB_SHIFT_CREATE_DRAFT, AiToolId.LAB_POLICY_READ),
                candidates.stream().map(candidate -> candidate.toolId()).toList());
        assertTrue(candidates.stream().allMatch(candidate -> candidate.resource().resourceId().equals(10L)));
        assertTrue(candidates.stream()
                .filter(candidate -> candidate.toolId() == AiToolId.LAB_POLICY_READ)
                .allMatch(candidate -> candidate.description().contains("authorized knowledge documents")));
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void adminManagerKeepsBothRolesAndEveryBoundedManagedLab() {
        when(actorProvider.requireCurrentActor()).thenReturn(new AiCurrentActor(8L, AiAssistantSystemRole.ADMIN,
                java.util.Set.of(AiAssistantSystemRole.ADMIN, AiAssistantSystemRole.LAB_MANAGER)));
        when(laboratoryRepository.findAiCandidateLabsForManager(org.mockito.ArgumentMatchers.eq(8L), any()))
                .thenReturn(List.of(new AiLabContext.Laboratory(10L, "AI Lab", LabStatus.AVAILABLE, 30),
                                    new AiLabContext.Laboratory(11L, "Robotics", LabStatus.AVAILABLE, 20)));
        var candidates = catalog.candidates();
        assertTrue(candidates.stream().anyMatch(c -> c.toolId() == AiToolId.ADMIN_SYSTEM_SUMMARY));
        assertEquals(java.util.Set.of(10L, 11L), candidates.stream()
                .filter(c -> c.toolId() == AiToolId.LAB_SHIFT_CREATE_DRAFT)
                .map(c -> c.resource().resourceId()).collect(java.util.stream.Collectors.toSet()));
        assertTrue(candidates.stream().noneMatch(c -> c.toolId() == AiToolId.RESEARCH_TASK_PROPOSAL_DRAFT));
    }
}
