package com.web.labportalbackend.ai.service.impl;

import com.web.labportalbackend.ai.enums.AiActionRiskBoundary;
import com.web.labportalbackend.ai.enums.AiAssistantDomain;
import com.web.labportalbackend.ai.enums.AiAssistantToolGroup;
import com.web.labportalbackend.ai.enums.AiCapability;
import com.web.labportalbackend.ai.enums.AiRequestedAction;
import com.web.labportalbackend.ai.enums.AiResourceType;
import com.web.labportalbackend.ai.enums.AiToolArgument;
import com.web.labportalbackend.ai.enums.AiToolId;
import com.web.labportalbackend.ai.service.AiToolDefinition;
import com.web.labportalbackend.ai.service.AiToolRegistry;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Code-owned finite allowlist. It contains metadata only and cannot dispatch work. */
@Service
public class AiToolRegistryServiceImpl implements AiToolRegistry {

    public static final String SCHEMA_VERSION = "v1";
    /**
     * Fixed catalog identity boundary. IDs must never be rebound to another capability,
     * even when a supplied catalog remains otherwise complete and internally coherent.
     */
    private static final List<CatalogEntry> CANONICAL_CATALOG = List.of(
            entry(AiToolId.ADMIN_SYSTEM_SUMMARY, AiAssistantToolGroup.ADMIN_READ, AiCapability.ADMIN_SYSTEM_SUMMARY),
            entry(AiToolId.ADMIN_AUDIT_SUMMARY, AiAssistantToolGroup.ADMIN_READ, AiCapability.ADMIN_AUDIT_SUMMARY),
            entry(AiToolId.ADMIN_USER_STATUS_LOOKUP, AiAssistantToolGroup.ADMIN_READ, AiCapability.ADMIN_USER_STATUS_LOOKUP),
            entry(AiToolId.ADMIN_CONFIG_DRAFT, AiAssistantToolGroup.ADMIN_DRAFT, AiCapability.ADMIN_CONFIG_DRAFT),
            entry(AiToolId.ADMIN_ACCOUNT_ACTION_DRAFT, AiAssistantToolGroup.ADMIN_DRAFT, AiCapability.ADMIN_ACCOUNT_ACTION_DRAFT),
            entry(AiToolId.LAB_POLICY_READ, AiAssistantToolGroup.LAB_READ, AiCapability.LAB_POLICY_READ),
            entry(AiToolId.LAB_SLOT_READ, AiAssistantToolGroup.LAB_READ, AiCapability.LAB_SLOT_READ),
            entry(AiToolId.LAB_AVAILABLE_SLOTS_READ, AiAssistantToolGroup.LAB_READ,
                    AiCapability.LAB_AVAILABLE_SLOTS_READ),
            entry(AiToolId.LAB_OWN_BOOKING_READ, AiAssistantToolGroup.LAB_READ, AiCapability.LAB_OWN_BOOKING_READ),
            entry(AiToolId.LAB_MANAGED_SUMMARY, AiAssistantToolGroup.LAB_READ, AiCapability.LAB_MANAGED_SUMMARY),
            entry(AiToolId.LAB_SHIFT_CREATE_DRAFT, AiAssistantToolGroup.LAB_DRAFT,
                    AiCapability.LAB_SHIFT_CREATE_DRAFT),
            entry(AiToolId.LAB_BOOKING_DRAFT, AiAssistantToolGroup.LAB_DRAFT, AiCapability.LAB_BOOKING_DRAFT),
            entry(AiToolId.LAB_CHECKIN_GUIDANCE, AiAssistantToolGroup.LAB_READ, AiCapability.LAB_CHECKIN_GUIDANCE),
            entry(AiToolId.RESEARCH_PROJECT_SUMMARY, AiAssistantToolGroup.RESEARCH_READ, AiCapability.RESEARCH_PROJECT_SUMMARY),
            entry(AiToolId.RESEARCH_GROUP_SUMMARY, AiAssistantToolGroup.RESEARCH_READ, AiCapability.RESEARCH_GROUP_SUMMARY),
            entry(AiToolId.RESEARCH_ASSIGNED_TASK_READ, AiAssistantToolGroup.RESEARCH_READ, AiCapability.RESEARCH_ASSIGNED_TASK_READ),
            entry(AiToolId.RESEARCH_TASK_PROPOSAL_DRAFT, AiAssistantToolGroup.RESEARCH_DRAFT, AiCapability.RESEARCH_TASK_PROPOSAL_DRAFT),
            entry(AiToolId.RESEARCH_TASK_SUGGESTION_DRAFT, AiAssistantToolGroup.RESEARCH_DRAFT, AiCapability.RESEARCH_TASK_SUGGESTION_DRAFT),
            entry(AiToolId.RESEARCH_REPORT_REVIEW_DRAFT, AiAssistantToolGroup.RESEARCH_DRAFT, AiCapability.RESEARCH_REPORT_REVIEW_DRAFT));
    private static final Map<AiToolId, AiCapability> EXPECTED_CAPABILITY_BY_ID = CANONICAL_CATALOG.stream()
            .collect(Collectors.toUnmodifiableMap(CatalogEntry::id, CatalogEntry::capability));
    private final Map<String, AiToolDefinition> definitionsByExternalId;
    private final Map<AiToolId, AiToolDefinition> definitionsById;
    private final Map<AiCapability, AiToolDefinition> definitionsByCapability;

    public AiToolRegistryServiceImpl() {
        this(defaultDefinitions());
    }

    AiToolRegistryServiceImpl(List<AiToolDefinition> definitions) {
        this.definitionsById = byId(definitions);
        this.definitionsByExternalId = this.definitionsById.values().stream()
                .collect(Collectors.toUnmodifiableMap(definition -> definition.id().value(), definition -> definition));
        this.definitionsByCapability = byCapability(definitions);
    }

    @Override
    public AiToolDefinition get(String externalToolId) {
        return externalToolId == null ? null : definitionsByExternalId.get(externalToolId);
    }

    @Override
    public AiToolDefinition get(AiToolId toolId) {
        return toolId == null ? null : definitionsById.get(toolId);
    }

    @Override
    public AiToolDefinition get(AiCapability capability) {
        return capability == null ? null : definitionsByCapability.get(capability);
    }

    static List<AiToolDefinition> defaultDefinitions() {
        return CANONICAL_CATALOG.stream()
                .map(entry -> definition(entry.id(), entry.group(), entry.capability()))
                .toList();
    }

    private static AiToolDefinition definition(AiToolId id, AiAssistantToolGroup group, AiCapability capability) {
        Set<AiToolArgument> arguments = capability.parentResourceType() == null
                ? Set.of(AiToolArgument.RESOURCE)
                : Set.of(AiToolArgument.RESOURCE, AiToolArgument.PARENT_RESOURCE);
        return new AiToolDefinition(id, capability.domain(), group, capability, capability.resourceType(),
                capability.parentResourceType(), capability.action(), capability.riskBoundary(), SCHEMA_VERSION, arguments);
    }

    private static Map<AiToolId, AiToolDefinition> byId(List<AiToolDefinition> definitions) {
        if (definitions == null || definitions.size() != AiToolId.values().length) {
            throw new IllegalArgumentException("tool catalog must contain every fixed tool ID");
        }
        Map<AiToolId, AiToolDefinition> byId = new EnumMap<>(AiToolId.class);
        for (AiToolDefinition definition : definitions) {
            if (definition == null || byId.put(definition.id(), definition) != null) {
                throw new IllegalArgumentException("tool catalog has a duplicate or null ID");
            }
            if (EXPECTED_CAPABILITY_BY_ID.get(definition.id()) != definition.capability()) {
                throw new IllegalArgumentException("tool catalog ID/capability mapping is not canonical");
            }
        }
        if (byId.size() != AiToolId.values().length) {
            throw new IllegalArgumentException("tool catalog is incomplete");
        }
        return Map.copyOf(byId);
    }

    private static Map<AiCapability, AiToolDefinition> byCapability(List<AiToolDefinition> definitions) {
        if (definitions == null || definitions.size() != AiCapability.values().length) {
            throw new IllegalArgumentException("tool catalog must contain every capability");
        }
        Map<AiCapability, AiToolDefinition> byCapability = new EnumMap<>(AiCapability.class);
        for (AiToolDefinition definition : definitions) {
            if (definition == null || byCapability.put(definition.capability(), definition) != null) {
                throw new IllegalArgumentException("tool catalog has a duplicate or null capability");
            }
        }
        if (byCapability.size() != AiCapability.values().length) {
            throw new IllegalArgumentException("tool catalog is incomplete");
        }
        return Map.copyOf(byCapability);
    }

    private static CatalogEntry entry(AiToolId id, AiAssistantToolGroup group, AiCapability capability) {
        return new CatalogEntry(id, group, capability);
    }

    private record CatalogEntry(AiToolId id, AiAssistantToolGroup group, AiCapability capability) {
    }
}
