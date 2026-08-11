package com.web.labportalbackend.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.web.labportalbackend.ai.enums.AiAssistantDomain;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiAssistantSystemRole;
import com.web.labportalbackend.ai.enums.AiAssistantToolGroup;
import com.web.labportalbackend.ai.enums.AiQuotaPolicyReference;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AiAssistantProfileTest {

    @Test
    void rejectsNullKeyAndDomain() {
        assertThrows(IllegalArgumentException.class,
                () -> profile(null, AiAssistantDomain.LAB, Set.of(AiAssistantToolGroup.LAB_READ)));
        assertThrows(IllegalArgumentException.class,
                () -> profile(AiAssistantKey.LAB_ASSISTANT, null, Set.of(AiAssistantToolGroup.LAB_READ)));
    }

    @ParameterizedTest
    @MethodSource("mismatchedKeyDomains")
    void rejectsEveryMismatchedKeyDomain(AiAssistantKey key, AiAssistantDomain domain) {
        assertThrows(IllegalArgumentException.class,
                () -> profile(key, domain, Set.of(firstGroupFor(domain))));
    }

    @ParameterizedTest
    @MethodSource("crossDomainToolGroups")
    void rejectsEveryCrossDomainToolGroup(AiAssistantKey key, AiAssistantToolGroup group) {
        assertThrows(IllegalArgumentException.class,
                () -> profile(key, key.domain(), Set.of(group)));
    }

    @Test
    void copiesAndExposesImmutableValidRoleAndGroupSets() {
        Set<AiAssistantSystemRole> roles = EnumSet.of(AiAssistantSystemRole.STUDENT);
        Set<AiAssistantToolGroup> groups = EnumSet.of(AiAssistantToolGroup.LAB_READ, AiAssistantToolGroup.LAB_DRAFT);

        AiAssistantProfile profile = new AiAssistantProfile(AiAssistantKey.LAB_ASSISTANT, AiAssistantDomain.LAB,
                true, roles, "profile", "prompt-v1", null, "namespace", AiQuotaPolicyReference.AI_CONFIG_QUOTA,
                groups, "suite-v1");

        roles.clear();
        groups.clear();

        assertEquals(Set.of(AiAssistantSystemRole.STUDENT), profile.allowedSystemRoles());
        assertEquals(Set.of(AiAssistantToolGroup.LAB_READ, AiAssistantToolGroup.LAB_DRAFT), profile.toolGroups());
        assertThrows(UnsupportedOperationException.class, () -> profile.allowedSystemRoles().clear());
        assertThrows(UnsupportedOperationException.class, () -> profile.toolGroups().clear());
    }

    private static Stream<Arguments> mismatchedKeyDomains() {
        return Arrays.stream(AiAssistantKey.values())
                .flatMap(key -> Arrays.stream(AiAssistantDomain.values())
                        .filter(domain -> domain != key.domain())
                        .map(domain -> Arguments.of(key, domain)));
    }

    private static Stream<Arguments> crossDomainToolGroups() {
        return Arrays.stream(AiAssistantKey.values())
                .flatMap(key -> Arrays.stream(AiAssistantToolGroup.values())
                        .filter(group -> !group.belongsTo(key.domain()))
                        .map(group -> Arguments.of(key, group)));
    }

    private static AiAssistantToolGroup firstGroupFor(AiAssistantDomain domain) {
        return Arrays.stream(AiAssistantToolGroup.values())
                .filter(group -> group.belongsTo(domain))
                .findFirst()
                .orElseThrow();
    }

    private static AiAssistantProfile profile(AiAssistantKey key, AiAssistantDomain domain,
                                              Set<AiAssistantToolGroup> groups) {
        return new AiAssistantProfile(key, domain, true, Set.of(AiAssistantSystemRole.STUDENT),
                "profile", "prompt-v1", null, "namespace", AiQuotaPolicyReference.AI_CONFIG_QUOTA,
                groups, "suite-v1");
    }
}
