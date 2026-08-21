package com.web.labportalbackend.ai.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.web.labportalbackend.ai.enums.AiCapability;
import com.web.labportalbackend.ai.enums.AiToolId;
import com.web.labportalbackend.ai.service.AiToolDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiToolRegistryServiceImplTest {

    /** Independent expected mapping: never derive this assertion from the catalog under test. */
    private static final Map<AiToolId, AiCapability> EXPECTED_CAPABILITY_BY_ID = Map.ofEntries(
            Map.entry(AiToolId.ADMIN_SYSTEM_SUMMARY, AiCapability.ADMIN_SYSTEM_SUMMARY),
            Map.entry(AiToolId.ADMIN_AUDIT_SUMMARY, AiCapability.ADMIN_AUDIT_SUMMARY),
            Map.entry(AiToolId.ADMIN_USER_STATUS_LOOKUP, AiCapability.ADMIN_USER_STATUS_LOOKUP),
            Map.entry(AiToolId.ADMIN_CONFIG_DRAFT, AiCapability.ADMIN_CONFIG_DRAFT),
            Map.entry(AiToolId.ADMIN_ACCOUNT_ACTION_DRAFT, AiCapability.ADMIN_ACCOUNT_ACTION_DRAFT),
            Map.entry(AiToolId.LAB_POLICY_READ, AiCapability.LAB_POLICY_READ),
            Map.entry(AiToolId.LAB_SLOT_READ, AiCapability.LAB_SLOT_READ),
            Map.entry(AiToolId.LAB_OWN_BOOKING_READ, AiCapability.LAB_OWN_BOOKING_READ),
            Map.entry(AiToolId.LAB_MANAGED_SUMMARY, AiCapability.LAB_MANAGED_SUMMARY),
            Map.entry(AiToolId.LAB_BOOKING_DRAFT, AiCapability.LAB_BOOKING_DRAFT),
            Map.entry(AiToolId.LAB_CHECKIN_GUIDANCE, AiCapability.LAB_CHECKIN_GUIDANCE),
            Map.entry(AiToolId.RESEARCH_PROJECT_SUMMARY, AiCapability.RESEARCH_PROJECT_SUMMARY),
            Map.entry(AiToolId.RESEARCH_GROUP_SUMMARY, AiCapability.RESEARCH_GROUP_SUMMARY),
            Map.entry(AiToolId.RESEARCH_ASSIGNED_TASK_READ, AiCapability.RESEARCH_ASSIGNED_TASK_READ),
            Map.entry(AiToolId.RESEARCH_TASK_PROPOSAL_DRAFT, AiCapability.RESEARCH_TASK_PROPOSAL_DRAFT),
            Map.entry(AiToolId.RESEARCH_TASK_SUGGESTION_DRAFT, AiCapability.RESEARCH_TASK_SUGGESTION_DRAFT),
            Map.entry(AiToolId.RESEARCH_REPORT_REVIEW_DRAFT, AiCapability.RESEARCH_REPORT_REVIEW_DRAFT));

    @Test
    void exposesAnExactBijectionOfAllFixedToolIdsAndCapabilities() {
        AiToolRegistryServiceImpl registry = new AiToolRegistryServiceImpl();

        assertEquals(EXPECTED_CAPABILITY_BY_ID.size(), AiToolRegistryServiceImpl.defaultDefinitions().size());
        for (AiToolDefinition definition : AiToolRegistryServiceImpl.defaultDefinitions()) {
            assertEquals(definition, registry.get(definition.id()));
            assertEquals(definition, registry.get(definition.capability()));
            assertEquals(EXPECTED_CAPABILITY_BY_ID.get(definition.id()), definition.capability());
            assertEquals(definition.domain(), definition.group().domain());
            assertEquals(definition.domain(), definition.capability().domain());
            assertEquals(definition.action(), definition.capability().action());
            assertEquals(definition.riskBoundary(), definition.capability().riskBoundary());
        }
        assertEquals(AiCapability.values().length, EXPECTED_CAPABILITY_BY_ID.size());
    }

    @Test
    void resolvesOnlyExactCanonicalExternalToolIds() {
        AiToolRegistryServiceImpl registry = new AiToolRegistryServiceImpl();

        assertEquals(AiToolId.LAB_SLOT_READ, registry.get("lab.slot.read").id());
        assertNull(registry.get("LAB.SLOT.READ"));
        assertNull(registry.get("lab.slot.read "));
        assertNull(registry.get("model.random.tool"));
        assertNull(registry.get((String) null));
    }

    @Test
    void rejectsMissingDuplicateAndCapabilitySwappedCatalogs() {
        List<AiToolDefinition> definitions = AiToolRegistryServiceImpl.defaultDefinitions();
        assertThrows(IllegalArgumentException.class,
                () -> new AiToolRegistryServiceImpl(definitions.subList(0, definitions.size() - 1)));
        assertThrows(IllegalArgumentException.class, () -> new AiToolRegistryServiceImpl(List.of(definitions.get(0), definitions.get(0))));

        List<AiToolDefinition> swapped = new ArrayList<>(definitions);
        swapped.set(0, withId(definitions.get(0).id(), definitions.get(1)));
        swapped.set(1, withId(definitions.get(1).id(), definitions.get(0)));
        assertThrows(IllegalArgumentException.class, () -> new AiToolRegistryServiceImpl(swapped));
    }

    @Test
    void rejectsDescriptorInvariantViolationsBeforeCatalogConstruction() {
        AiToolDefinition definition = AiToolRegistryServiceImpl.defaultDefinitions().getFirst();

        assertThrows(IllegalArgumentException.class, () -> new AiToolDefinition(definition.id(), definition.domain(),
                definition.group(), definition.capability(), AiCapability.ADMIN_AUDIT_SUMMARY.resourceType(),
                definition.parentResourceType(), definition.action(), definition.riskBoundary(), definition.schemaVersion(),
                definition.arguments()));
    }

    private static AiToolDefinition withId(AiToolId id, AiToolDefinition source) {
        return new AiToolDefinition(id, source.domain(), source.group(), source.capability(), source.resourceType(),
                source.parentResourceType(), source.action(), source.riskBoundary(), source.schemaVersion(), source.arguments());
    }
}
