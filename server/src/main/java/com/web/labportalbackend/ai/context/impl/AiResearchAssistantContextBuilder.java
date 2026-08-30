package com.web.labportalbackend.ai.context.impl;

import com.web.labportalbackend.ai.context.AiBoundedList;
import com.web.labportalbackend.ai.context.AiContextReadDeniedException;
import com.web.labportalbackend.ai.context.AiDomainContext;
import com.web.labportalbackend.ai.context.AiDomainContextBuilder;
import com.web.labportalbackend.ai.context.AiLabContext;
import com.web.labportalbackend.ai.context.AiResearchAssistantContext;
import com.web.labportalbackend.ai.context.AiResearchReportContext;
import com.web.labportalbackend.ai.context.TrustedContextInput;
import com.web.labportalbackend.ai.enums.AiAssistantDomain;
import com.web.labportalbackend.ai.enums.AiCapability;
import com.web.labportalbackend.ai.enums.AiResourceType;
import com.web.labportalbackend.ai.service.AiResearchContext;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.MilestoneRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.repository.ReportRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class AiResearchAssistantContextBuilder implements AiDomainContextBuilder {

    private static final int GROUP_LIMIT = 20;
    private static final int MILESTONE_LIMIT = 20;
    private static final int TASK_LIMIT = 25;
    private final ProjectRepository projectRepository;
    private final GroupRepository groupRepository;
    private final MilestoneRepository milestoneRepository;
    private final TaskRepository taskRepository;
    private final ReportRepository reportRepository;
    private final LaboratoryRepository laboratoryRepository;

    public AiResearchAssistantContextBuilder(ProjectRepository projectRepository,
                                             GroupRepository groupRepository,
                                             MilestoneRepository milestoneRepository,
                                             TaskRepository taskRepository,
                                             ReportRepository reportRepository,
                                             LaboratoryRepository laboratoryRepository) {
        this.projectRepository = projectRepository;
        this.groupRepository = groupRepository;
        this.milestoneRepository = milestoneRepository;
        this.taskRepository = taskRepository;
        this.reportRepository = reportRepository;
        this.laboratoryRepository = laboratoryRepository;
    }

    @Override public AiAssistantDomain domain() { return AiAssistantDomain.RESEARCH; }

    @Override
    public AiDomainContext build(TrustedContextInput input) {
        if (input.decision().domain() != domain()) {
            throw new AiContextReadDeniedException();
        }
        var resource = input.decision().resolvedResource();
        if (resource.projectId() == null || resource.labId() == null) {
            throw new AiContextReadDeniedException();
        }
        Long actorId = input.actorId();
        String selectedRoleName = input.decision().selectedSystemRole().name();
        Long projectId = resource.projectId();
        AiResearchReportContext report = requireSelectedCurrent(input, projectId, selectedRoleName);
        AiResearchContext.Project project = projectRepository.findAiContextProject(actorId, resource.labId(), projectId, selectedRoleName)
                .orElseThrow(AiContextReadDeniedException::new);
        AiLabContext.Laboratory lab = laboratoryRepository.findAiContextLaboratory(actorId, resource.labId(), selectedRoleName)
                .orElseThrow(AiContextReadDeniedException::new);
        Long selectedGroupId = resource.type() == AiResourceType.PROJECT ? null : resource.groupId();
        Long selectedTaskId = resource.type() == AiResourceType.TASK ? resource.taskId() : null;
        Long selectedReportId = resource.type() == AiResourceType.REPORT ? resource.id() : null;
        List<AiResearchContext.Group> groups = groupRepository.findAiContextGroups(actorId, projectId, selectedGroupId,
                PageRequest.of(0, GROUP_LIMIT + 1), selectedRoleName);
        List<AiResearchContext.Milestone> milestones = milestoneRepository.findAiContextMilestones(actorId, projectId,
                selectedGroupId, selectedTaskId, selectedReportId,
                PageRequest.of(0, MILESTONE_LIMIT + 1), selectedRoleName);
        List<AiResearchContext.Task> tasks = taskRepository.findAiContextTasks(actorId, projectId,
                selectedGroupId, selectedTaskId, selectedReportId,
                PageRequest.of(0, TASK_LIMIT + 1), selectedRoleName);
        AiBoundedList<AiResearchContext.Group> boundedGroups = AiBoundedList.fromOverfetch(groups, GROUP_LIMIT);
        AiBoundedList<AiResearchContext.Milestone> boundedMilestones = AiBoundedList.fromOverfetch(milestones, MILESTONE_LIMIT);
        AiBoundedList<AiResearchContext.Task> boundedTasks = AiBoundedList.fromOverfetch(tasks, TASK_LIMIT);
        AiResearchContext research = new AiResearchContext(
                new AiResearchContext.Identity(actorId, List.of()),
                new AiResearchContext.Laboratory(lab.id(), lab.name()), project,
                boundedGroups.values(), boundedMilestones.values(), boundedTasks.values());
        return new AiResearchAssistantContext(research,
                boundedGroups, boundedMilestones, boundedTasks,
                report,
                resource.id(), input.decision().capability().action().name().equals("DRAFT"));
    }

    private AiResearchReportContext requireSelectedCurrent(TrustedContextInput input, Long projectId,
                                                            String selectedRoleName) {
        var resource = input.decision().resolvedResource();
        Long id = resource.id();
        if (resource.type() == AiResourceType.REPORT) {
            if (id == null) {
                throw new AiContextReadDeniedException();
            }
            return reportRepository.findAiContextReport(input.actorId(), projectId, id, selectedRoleName)
                    .orElseThrow(AiContextReadDeniedException::new);
        }
        boolean allowed = switch (resource.type()) {
            case PROJECT -> true;
            case GROUP -> id != null && groupRepository.findAiContextGroup(input.actorId(), projectId, id, selectedRoleName).isPresent();
            case TASK -> id != null && taskRepository.existsAiContextTask(input.actorId(), projectId, id, selectedRoleName);
            default -> false;
        };
        if (!allowed) throw new AiContextReadDeniedException();
        return null;
    }
}
