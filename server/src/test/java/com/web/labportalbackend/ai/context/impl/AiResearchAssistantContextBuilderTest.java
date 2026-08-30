package com.web.labportalbackend.ai.context.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.web.labportalbackend.ai.context.*;
import com.web.labportalbackend.ai.enums.*;
import com.web.labportalbackend.ai.service.AiCapabilityDecision;
import com.web.labportalbackend.ai.service.AiResearchContext;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.enums.ProjectStatus;
import com.web.labportalbackend.research.enums.ReportStatus;
import com.web.labportalbackend.research.repository.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.Test;

class AiResearchAssistantContextBuilderTest {
    private static final String STUDENT_ROLE = AiAssistantSystemRole.STUDENT.name();
    @Test void currentAcceptedActorIsBoundToEveryResearchProjection() {
        ProjectRepository projects = mock(ProjectRepository.class); GroupRepository groups = mock(GroupRepository.class);
        MilestoneRepository milestones = mock(MilestoneRepository.class); TaskRepository tasks = mock(TaskRepository.class);
        ReportRepository reports = mock(ReportRepository.class); LaboratoryRepository labs = mock(LaboratoryRepository.class);
        when(projects.findAiContextProject(7L, 10L, 20L, STUDENT_ROLE)).thenReturn(Optional.of(
                new AiResearchContext.Project(20L, "P", "Project", ProjectStatus.DRAFT, null, null)));
        when(labs.findAiContextLaboratory(7L, 10L, STUDENT_ROLE)).thenReturn(Optional.of(new AiLabContext.Laboratory(10L, "Lab", null)));
        when(groups.findAiContextGroups(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.isNull(), any(), org.mockito.ArgumentMatchers.eq(STUDENT_ROLE))).thenReturn(List.of());
        when(milestones.findAiContextMilestones(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), any(), org.mockito.ArgumentMatchers.eq(STUDENT_ROLE))).thenReturn(List.of());
        when(tasks.findAiContextTasks(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), any(), org.mockito.ArgumentMatchers.eq(STUDENT_ROLE))).thenReturn(List.of());
        AiResearchAssistantContextBuilder builder = new AiResearchAssistantContextBuilder(projects, groups, milestones, tasks, reports, labs);
        AiResearchAssistantContext context = (AiResearchAssistantContext) builder.build(input());
        assertEquals(7L, context.research().identity().userId());
        verify(projects).findAiContextProject(7L, 10L, 20L, STUDENT_ROLE); verify(labs).findAiContextLaboratory(7L, 10L, STUDENT_ROLE);
        verify(groups).findAiContextGroups(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.isNull(), any(), org.mockito.ArgumentMatchers.eq(STUDENT_ROLE));
        verify(milestones).findAiContextMilestones(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), any(), org.mockito.ArgumentMatchers.eq(STUDENT_ROLE));
        verify(tasks).findAiContextTasks(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), any(), org.mockito.ArgumentMatchers.eq(STUDENT_ROLE));
    }

    @Test
    void overfetchesAndAccuratelyMarksTruncatedResearchLists() {
        ProjectRepository projects = mock(ProjectRepository.class); GroupRepository groups = mock(GroupRepository.class);
        MilestoneRepository milestones = mock(MilestoneRepository.class); TaskRepository tasks = mock(TaskRepository.class);
        ReportRepository reports = mock(ReportRepository.class); LaboratoryRepository labs = mock(LaboratoryRepository.class);
        when(projects.findAiContextProject(7L, 10L, 20L, STUDENT_ROLE)).thenReturn(Optional.of(
                new AiResearchContext.Project(20L, "P", "Project", ProjectStatus.DRAFT, null, null)));
        when(labs.findAiContextLaboratory(7L, 10L, STUDENT_ROLE)).thenReturn(Optional.of(new AiLabContext.Laboratory(10L, "Lab", null)));
        List<AiResearchContext.Group> rows = IntStream.range(0, 21)
                .mapToObj(id -> new AiResearchContext.Group((long) id, "group-" + id, null)).toList();
        when(groups.findAiContextGroups(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.isNull(), any(), org.mockito.ArgumentMatchers.eq(STUDENT_ROLE))).thenReturn(rows);
        when(milestones.findAiContextMilestones(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), any(), org.mockito.ArgumentMatchers.eq(STUDENT_ROLE))).thenReturn(List.of());
        when(tasks.findAiContextTasks(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), any(), org.mockito.ArgumentMatchers.eq(STUDENT_ROLE))).thenReturn(List.of());
        AiResearchAssistantContextBuilder builder = new AiResearchAssistantContextBuilder(projects, groups, milestones, tasks, reports, labs);

        AiResearchAssistantContext context = (AiResearchAssistantContext) builder.build(input());

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(groups).findAiContextGroups(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.isNull(), page.capture(), org.mockito.ArgumentMatchers.eq(STUDENT_ROLE));
        assertEquals(21, page.getValue().getPageSize());
        assertEquals(20, context.groups().values().size());
        assertEquals(true, context.groups().truncated());
        assertEquals(20, context.research().groups().size());
    }

    @Test
    void groupSummaryUsesResolverCarriedProjectForSelectedRoleProjections() {
        ProjectRepository projects = mock(ProjectRepository.class); GroupRepository groups = mock(GroupRepository.class);
        MilestoneRepository milestones = mock(MilestoneRepository.class); TaskRepository tasks = mock(TaskRepository.class);
        ReportRepository reports = mock(ReportRepository.class); LaboratoryRepository labs = mock(LaboratoryRepository.class);
        AiResearchContext.Project project = new AiResearchContext.Project(
                20L, "P", "Project", ProjectStatus.ONGOING, null, null);
        AiResearchContext.Group group = new AiResearchContext.Group(30L, "Group", null);
        when(groups.findAiContextGroup(7L, 20L, 30L, STUDENT_ROLE)).thenReturn(Optional.of(group));
        when(projects.findAiContextProject(7L, 10L, 20L, STUDENT_ROLE)).thenReturn(Optional.of(project));
        when(labs.findAiContextLaboratory(7L, 10L, STUDENT_ROLE)).thenReturn(Optional.of(
                new AiLabContext.Laboratory(10L, "Lab", null)));
        when(groups.findAiContextGroups(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.eq(30L), any(), org.mockito.ArgumentMatchers.eq(STUDENT_ROLE)))
                .thenReturn(List.of(group));
        when(milestones.findAiContextMilestones(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.eq(30L), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), any(), org.mockito.ArgumentMatchers.eq(STUDENT_ROLE)))
                .thenReturn(List.of());
        when(tasks.findAiContextTasks(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.eq(30L), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), any(), org.mockito.ArgumentMatchers.eq(STUDENT_ROLE)))
                .thenReturn(List.of());
        AiResearchAssistantContextBuilder builder = new AiResearchAssistantContextBuilder(
                projects, groups, milestones, tasks, reports, labs);

        AiResearchAssistantContext context = (AiResearchAssistantContext) builder.build(groupInput());

        assertEquals(20L, context.research().project().id());
        assertEquals(30L, context.selectedResourceId());
        verify(groups).findAiContextGroup(7L, 20L, 30L, STUDENT_ROLE);
    }

    @Test
    void selectedManagerProjectAnchorDenialStopsBuilderBeforeOtherProjections() {
        ProjectRepository projects = mock(ProjectRepository.class); GroupRepository groups = mock(GroupRepository.class);
        MilestoneRepository milestones = mock(MilestoneRepository.class); TaskRepository tasks = mock(TaskRepository.class);
        ReportRepository reports = mock(ReportRepository.class); LaboratoryRepository labs = mock(LaboratoryRepository.class);
        AiResearchAssistantContextBuilder builder = new AiResearchAssistantContextBuilder(
                projects, groups, milestones, tasks, reports, labs);

        assertThrows(AiContextReadDeniedException.class, () -> builder.build(managerInput()));

        verify(projects).findAiContextProject(7L, 10L, 20L, AiAssistantSystemRole.LAB_MANAGER.name());
        verifyNoInteractions(groups, milestones, tasks, reports, labs);
    }

    @Test
    void reportReviewProjectsOnlyTheFreshlyAuthorizedReportContent() {
        ProjectRepository projects = mock(ProjectRepository.class); GroupRepository groups = mock(GroupRepository.class);
        MilestoneRepository milestones = mock(MilestoneRepository.class); TaskRepository tasks = mock(TaskRepository.class);
        ReportRepository reports = mock(ReportRepository.class); LaboratoryRepository labs = mock(LaboratoryRepository.class);
        AiResearchReportContext report = new AiResearchReportContext(
                40L, 20L, 30L, 35L, 31L, 2, "Authorized report", "Completed work",
                "Bounded result", "Limited sample", "Repeat experiment", "Needs evidence",
                "https://evidence.example/report-40", ReportStatus.SUBMITTED);
        when(reports.findAiContextReport(7L, 20L, 40L, STUDENT_ROLE)).thenReturn(Optional.of(report));
        when(projects.findAiContextProject(7L, 10L, 20L, STUDENT_ROLE)).thenReturn(Optional.of(
                new AiResearchContext.Project(20L, "P", "Project", ProjectStatus.ONGOING, null, null)));
        when(labs.findAiContextLaboratory(7L, 10L, STUDENT_ROLE)).thenReturn(Optional.of(
                new AiLabContext.Laboratory(10L, "Lab", null)));
        when(groups.findAiContextGroups(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.eq(30L), any(), org.mockito.ArgumentMatchers.eq(STUDENT_ROLE))).thenReturn(List.of());
        when(milestones.findAiContextMilestones(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.eq(30L), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(40L), any(), org.mockito.ArgumentMatchers.eq(STUDENT_ROLE))).thenReturn(List.of());
        when(tasks.findAiContextTasks(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.eq(30L), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(40L), any(), org.mockito.ArgumentMatchers.eq(STUDENT_ROLE))).thenReturn(List.of());
        AiResearchAssistantContextBuilder builder = new AiResearchAssistantContextBuilder(
                projects, groups, milestones, tasks, reports, labs);

        AiResearchAssistantContext context = (AiResearchAssistantContext) builder.build(reportInput());

        assertEquals(report, context.report());
        assertEquals(40L, context.selectedResourceId());
        assertEquals(true, context.draftOnly());
    }

    private static TrustedContextInput input() {
        AiCapabilityDecision d = new AiCapabilityDecision(true, 7L, com.web.labportalbackend.ai.enums.AiAssistantSystemRole.STUDENT, AiAssistantKey.RESEARCH_ASSISTANT,
                AiAssistantDomain.RESEARCH, AiCapability.RESEARCH_PROJECT_SUMMARY,
                new AiCapabilityDecision.ResolvedResource(AiResourceType.PROJECT, 20L, 10L, 20L, null, null, AiResourceScope.EXISTING_BUSINESS_PERMISSION),
                AiCapabilityDecisionReason.ALLOWED_BY_EFFECTIVE_PERMISSION, null, AiActionRiskBoundary.READ_ONLY, Set.of(), null);
        return new TrustedContextInput(d, 7L, null, Instant.now());
    }

    private static TrustedContextInput groupInput() {
        AiCapabilityDecision decision = new AiCapabilityDecision(true, 7L, AiAssistantSystemRole.STUDENT,
                AiAssistantKey.RESEARCH_ASSISTANT, AiAssistantDomain.RESEARCH,
                AiCapability.RESEARCH_GROUP_SUMMARY,
                new AiCapabilityDecision.ResolvedResource(AiResourceType.GROUP, 30L, 10L, 20L, 30L,
                        null, AiResourceScope.GROUP_MEMBER),
                AiCapabilityDecisionReason.ALLOWED_BY_EFFECTIVE_PERMISSION, null,
                AiActionRiskBoundary.READ_ONLY, Set.of(), null);
        return new TrustedContextInput(decision, 7L, null, Instant.now());
    }

    private static TrustedContextInput managerInput() {
        AiCapabilityDecision decision = new AiCapabilityDecision(true, 7L, AiAssistantSystemRole.LAB_MANAGER,
                AiAssistantKey.RESEARCH_ASSISTANT, AiAssistantDomain.RESEARCH,
                AiCapability.RESEARCH_PROJECT_SUMMARY,
                new AiCapabilityDecision.ResolvedResource(AiResourceType.PROJECT, 20L, 10L, 20L, null,
                        null, AiResourceScope.MANAGED_LAB),
                AiCapabilityDecisionReason.ALLOWED_BY_EFFECTIVE_PERMISSION, null,
                AiActionRiskBoundary.READ_ONLY, Set.of(), null);
        return new TrustedContextInput(decision, 7L, null, Instant.now());
    }

    private static TrustedContextInput reportInput() {
        AiCapabilityDecision decision = new AiCapabilityDecision(true, 7L, AiAssistantSystemRole.STUDENT,
                AiAssistantKey.RESEARCH_ASSISTANT, AiAssistantDomain.RESEARCH,
                AiCapability.RESEARCH_REPORT_REVIEW_DRAFT,
                new AiCapabilityDecision.ResolvedResource(AiResourceType.REPORT, 40L, 10L, 20L, 30L,
                        31L, AiResourceScope.GROUP_LEADER),
                AiCapabilityDecisionReason.ALLOWED_BY_EFFECTIVE_PERMISSION, null,
                AiActionRiskBoundary.DRAFT_ONLY, Set.of(), null);
        return new TrustedContextInput(decision, 7L, null, Instant.now());
    }
}
