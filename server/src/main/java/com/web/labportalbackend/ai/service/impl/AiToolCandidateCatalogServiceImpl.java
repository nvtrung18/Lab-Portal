package com.web.labportalbackend.ai.service.impl;

import com.web.labportalbackend.ai.context.AiLabContext;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiAssistantSystemRole;
import com.web.labportalbackend.ai.enums.AiCapability;
import com.web.labportalbackend.ai.enums.AiResourceType;
import com.web.labportalbackend.ai.service.AiCapabilityResolver;
import com.web.labportalbackend.ai.service.AiCapabilityRequest;
import com.web.labportalbackend.ai.service.AiCapabilityDecision;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.web.labportalbackend.ai.service.AiCurrentActor;
import com.web.labportalbackend.ai.service.AiCurrentActorProvider;
import com.web.labportalbackend.ai.service.AiToolCandidate;
import com.web.labportalbackend.ai.service.AiToolCandidateCatalog;
import com.web.labportalbackend.ai.service.AiToolDefinition;
import com.web.labportalbackend.ai.service.AiToolRegistry;
import com.web.labportalbackend.ai.service.AiResearchToolCandidateResource;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.EnumMap;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiToolCandidateCatalogServiceImpl implements AiToolCandidateCatalog {

    private static final int LAB_LIMIT = 50;
    private static final int BOOKING_LIMIT = 40;
    private static final int RESEARCH_RESOURCE_LIMIT = 20;

    private final AiCurrentActorProvider currentActorProvider;
    private final AiToolRegistry toolRegistry;
    private final LaboratoryRepository laboratoryRepository;
    private final BookingRepository bookingRepository;
    private final ProjectRepository projectRepository;
    private final GroupRepository groupRepository;
    private final TaskRepository taskRepository;
    private final AiCapabilityResolver capabilityResolver;
    private final ObjectMapper objectMapper;
    private static final Map<AiResourceType, Pattern> EXPLICIT_IDS = Map.of(
            AiResourceType.USER_ACCOUNT, idPattern("user|tài\\s*khoản|người\\s*dùng"),
            AiResourceType.LABORATORY, idPattern("lab|phòng\\s*thí\\s*nghiệm"),
            AiResourceType.TIME_SLOT, idPattern("slot|ca"),
            AiResourceType.BOOKING, idPattern("booking|lịch\\s*đặt"),
            AiResourceType.PROJECT, idPattern("project|dự\\s*án"),
            AiResourceType.GROUP, idPattern("group|nhóm"),
            AiResourceType.TASK, idPattern("task|công\\s*việc|nhiệm\\s*vụ"),
            AiResourceType.REPORT, idPattern("report|báo\\s*cáo"));

    public AiToolCandidateCatalogServiceImpl(AiCurrentActorProvider currentActorProvider,
                                             AiToolRegistry toolRegistry,
                                             LaboratoryRepository laboratoryRepository,
                                             BookingRepository bookingRepository,
                                             ProjectRepository projectRepository,
                                             GroupRepository groupRepository,
                                             TaskRepository taskRepository,
                                             AiCapabilityResolver capabilityResolver,
                                             ObjectMapper objectMapper) {
        this.currentActorProvider = currentActorProvider;
        this.toolRegistry = toolRegistry;
        this.laboratoryRepository = laboratoryRepository;
        this.bookingRepository = bookingRepository;
        this.projectRepository = projectRepository;
        this.groupRepository = groupRepository;
        this.taskRepository = taskRepository;
        this.capabilityResolver = capabilityResolver;
        this.objectMapper = objectMapper;
    }

    private static Pattern idPattern(String names) {
        return Pattern.compile("(?iu)\\b(?:" + names + ")\\s*(?:ID\\s*[:#]?\\s*|[:#]\\s*)?([+-]?\\d+)\\b");
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiToolCandidate> candidates(String input) {
        String message = input;
        try {
            var envelope = objectMapper.readTree(input);
            if (envelope.path("dialogueVersion").asInt() == 1 && envelope.path("message").isTextual()) {
                message = envelope.path("message").textValue();
            }
        } catch (JsonProcessingException ignored) {
            // Plain input is supported by callers outside the conversation envelope.
        }
        Map<AiResourceType, Set<Long>> ids = new EnumMap<>(AiResourceType.class);
        for (var entry : EXPLICIT_IDS.entrySet()) {
            var matcher = entry.getValue().matcher(message);
            while (matcher.find() && ids.values().stream().mapToInt(Set::size).sum() < 8) {
                var values = ids.computeIfAbsent(entry.getKey(), key -> new LinkedHashSet<>());
                try { values.add(Long.parseLong(matcher.group(1))); }
                catch (NumberFormatException ignored) { /* An invalid explicit ID must not select another resource. */ }
            }
        }
        if (ids.isEmpty()) return candidates();
        AiCurrentActor actor = currentActorProvider.requireCurrentActor();
        List<AiToolCandidate> resolved = new ArrayList<>();
        Map<Long, Long> groupProjects = new LinkedHashMap<>();
        // IDs seed lookup only; they never route a request or supply business authority.
        for (AiCapability capability : AiCapability.values()) {
            for (Long id : ids.getOrDefault(capability.resourceType(), Set.of())) {
                Set<Long> parents = capability.parentResourceType() == null ? Set.of()
                        : ids.getOrDefault(capability.parentResourceType(), Set.of());
                // Multiple explicit parents require clarification, never an arbitrary first match.
                if (parents.size() > 1) continue;
                Long parent = capability.parentResourceType() == null ? null
                        : parents.stream().findFirst().orElse(groupProjects.get(id));
                AiAssistantKey assistant = switch (capability.domain()) {
                    case ADMIN -> AiAssistantKey.ADMIN_ASSISTANT;
                    case LAB -> AiAssistantKey.LAB_ASSISTANT;
                    case RESEARCH -> AiAssistantKey.RESEARCH_ASSISTANT;
                };
                try {
                    AiCapabilityDecision allowed = capabilityResolver.resolve(new AiCapabilityRequest(
                            assistant, actor.id(), capability,
                            new AiCapabilityRequest.ResourceReference(capability.resourceType(), id),
                            capability.parentResourceType() == null ? null
                                    : new AiCapabilityRequest.ResourceReference(capability.parentResourceType(), parent),
                            capability.action()));
                    if (allowed == null || !allowed.allowed()) continue;
                    var resource = allowed.resolvedResource();
                    if (resource == null || !matchesMention(ids, AiResourceType.LABORATORY, resource.labId())
                            || !matchesMention(ids, AiResourceType.PROJECT, resource.projectId())
                            || !matchesMention(ids, AiResourceType.GROUP, resource.groupId())) continue;
                    if (capability == AiCapability.RESEARCH_GROUP_SUMMARY && resource.projectId() != null) {
                        groupProjects.put(id, resource.projectId());
                    }
                    resolved.add(candidate(capability, "Authorized " + capability.name() + " for "
                            + capability.resourceType() + " #" + id, id, parent));
                } catch (IllegalArgumentException ignored) {
                    // Omit unavailable/unauthorized IDs without disclosing whether they exist.
                }
            }
        }
        return resolved.stream().distinct().limit(200).toList();
    }

    private static boolean matchesMention(Map<AiResourceType, Set<Long>> ids, AiResourceType type, Long id) {
        return id == null || !ids.containsKey(type) || ids.get(type).contains(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiToolCandidate> candidates() {
        AiCurrentActor actor = currentActorProvider.requireCurrentActor();
        List<AiToolCandidate> result = new ArrayList<>();
        for (AiAssistantSystemRole role : AiAssistantSystemRole.values()) {
            if (!actor.systemRoles().contains(role)) continue;
            AiCurrentActor scopedActor = new AiCurrentActor(actor.id(), role, actor.systemRoles());
            result.addAll(switch (role) {
                case ADMIN -> adminCandidates();
                case LAB_MANAGER -> appendResearch(managerCandidates(actor.id()), scopedActor);
                case STUDENT -> appendResearch(studentCandidates(actor.id()), scopedActor);
            });
        }
        return result.stream().distinct().limit(200).toList();
    }

    private List<AiToolCandidate> adminCandidates() {
        return List.of(
                candidate(AiCapability.ADMIN_SYSTEM_SUMMARY, "Summarize current Lab Portal system status", null),
                candidate(AiCapability.ADMIN_AUDIT_SUMMARY, "Summarize authorized audit activity", null),
                candidate(AiCapability.ADMIN_CONFIG_DRAFT, "Draft a system configuration proposal without applying it", null));
    }

    private List<AiToolCandidate> managerCandidates(Long actorId) {
        List<AiToolCandidate> result = new ArrayList<>();
        for (var lab : laboratoryRepository.findAiCandidateLabsForManager(actorId, PageRequest.of(0, 10))) {
        String labLabel = safeLabel(lab.name(), lab.id());
        result.addAll(List.of(
                candidate(AiCapability.LAB_AVAILABLE_SLOTS_READ,
                        "List future available time slots for managed Lab " + labLabel, lab.id()),
                candidate(AiCapability.LAB_MANAGED_SUMMARY,
                        "List and summarize current time slots in managed Lab " + labLabel, lab.id()),
                candidate(AiCapability.LAB_SHIFT_CREATE_DRAFT,
                        "Create a confirmation preview for a new time slot in managed Lab " + labLabel,
                        lab.id()),
                candidate(AiCapability.LAB_POLICY_READ,
                        "Answer informational questions from authorized knowledge documents for managed Lab "
                                + labLabel + ", including policies, procedures, equipment, materials, safety, and guidance",
                        lab.id())));
        }
        return List.copyOf(result);
    }

    private List<AiToolCandidate> studentCandidates(Long actorId) {
        List<AiToolCandidate> result = new ArrayList<>();
        List<AiLabContext.Laboratory> labs = laboratoryRepository.findAiCandidateLabsForStudent(
                actorId, PageRequest.of(0, LAB_LIMIT));
        for (AiLabContext.Laboratory lab : labs) {
            String labLabel = safeLabel(lab.name(), lab.id());
            result.add(candidate(AiCapability.LAB_AVAILABLE_SLOTS_READ,
                    "List future available time slots for joined Lab " + labLabel, lab.id()));
            result.add(candidate(AiCapability.LAB_POLICY_READ,
                    "Answer informational questions from authorized knowledge documents for joined Lab "
                            + labLabel + ", including policies, procedures, equipment, materials, safety, and guidance",
                    lab.id()));
        }
        bookingRepository.findAiCandidateOwnBookings(actorId, PageRequest.of(0, BOOKING_LIMIT))
                .forEach(booking -> result.add(candidate(AiCapability.LAB_OWN_BOOKING_READ,
                        "Show own booking " + booking.id() + " with status " + booking.status()
                                + " starting " + booking.slot().startTime(), booking.id())));
        return List.copyOf(result);
    }

    private AiToolCandidate candidate(AiCapability capability, String description, Long resourceId) {
        return candidate(capability, description, resourceId, null);
    }

    private AiToolCandidate candidate(AiCapability capability,
                                      String description,
                                      Long resourceId,
                                      Long parentResourceId) {
        AiToolDefinition definition = toolRegistry.get(capability);
        if (definition == null) {
            throw new IllegalStateException("Canonical AI tool catalog is incomplete");
        }
        AiAssistantKey assistantKey = switch (definition.domain()) {
            case ADMIN -> AiAssistantKey.ADMIN_ASSISTANT;
            case LAB -> AiAssistantKey.LAB_ASSISTANT;
            case RESEARCH -> AiAssistantKey.RESEARCH_ASSISTANT;
        };
        return new AiToolCandidate(assistantKey, definition.schemaVersion(),
                definition.id(), description,
                new AiToolCandidate.ResourceReference(definition.resourceType(), resourceId),
                definition.parentResourceType() == null ? null
                        : new AiToolCandidate.ResourceReference(definition.parentResourceType(), parentResourceId));
    }

    private List<AiToolCandidate> appendResearch(List<AiToolCandidate> initial, AiCurrentActor actor) {
        List<AiToolCandidate> result = new ArrayList<>(initial);
        String role = actor.role().name();
        var page = PageRequest.of(0, RESEARCH_RESOURCE_LIMIT);
        projectRepository.findAiToolCandidateProjects(actor.id(), role, page).forEach(project ->
                result.add(candidate(AiCapability.RESEARCH_PROJECT_SUMMARY,
                        "Summarize authorized research project " + safeLabel(project.label(), project.id()),
                        project.id())));
        groupRepository.findAiToolCandidateGroups(actor.id(), role, page).forEach(group -> {
            result.add(candidate(AiCapability.RESEARCH_GROUP_SUMMARY,
                    "Summarize authorized research group " + safeLabel(group.label(), group.id()), group.id()));
            if (actor.systemRoles().equals(Set.of(AiAssistantSystemRole.STUDENT))) {
                result.add(candidate(AiCapability.RESEARCH_TASK_PROPOSAL_DRAFT,
                        "Draft a task proposal for research group " + safeLabel(group.label(), group.id()),
                        group.id(), group.projectId()));
            }
        });
        taskRepository.findAiToolCandidateTasks(actor.id(), role, page).forEach(task -> {
            result.add(candidate(AiCapability.RESEARCH_ASSIGNED_TASK_READ,
                    "Read authorized research task " + safeLabel(task.label(), task.id()), task.id()));
            result.add(candidate(AiCapability.RESEARCH_TASK_SUGGESTION_DRAFT,
                    "Draft guidance for research task " + safeLabel(task.label(), task.id()), task.id()));
        });
        if (result.size() > 200) {
            return List.copyOf(result.subList(0, 200));
        }
        return List.copyOf(result);
    }

    private static String safeLabel(String name, Long id) {
        String label = name == null || name.isBlank() ? "#" + id : name.trim();
        return label.length() <= 100 ? label : label.substring(0, 100);
    }
}
