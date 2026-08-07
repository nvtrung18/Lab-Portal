package com.web.labportalbackend.ai.security.impl;

import static com.web.labportalbackend.ai.enums.AiCapabilityDenialReason.PRIVATE_RESOURCE;
import static com.web.labportalbackend.ai.enums.AiCapabilityDenialReason.RESOURCE_UNAVAILABLE;
import static com.web.labportalbackend.ai.enums.AiCapabilityDenialReason.ROLE_NOT_ALLOWED;
import static com.web.labportalbackend.ai.enums.AiCapabilityDenialReason.DOMAIN_MISMATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiCapability;
import com.web.labportalbackend.ai.enums.AiRequestedAction;
import com.web.labportalbackend.ai.enums.AiResourceType;
import com.web.labportalbackend.ai.security.AiCapabilityPermissionAdapter;
import com.web.labportalbackend.ai.service.AiCapabilityRequest;
import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiAdminCapabilityPermissionAdapterTest {

    @Mock UserRepository userRepository;
    @InjectMocks AiAdminCapabilityPermissionAdapter adapter;

    @Test
    void allowsAdminGlobalReadWithoutReadingSensitiveData() {
        assertTrue(adapter.evaluate(user(1L, "ADMIN"), request(
                AiCapability.ADMIN_SYSTEM_SUMMARY, AiResourceType.SYSTEM, null,
                AiRequestedAction.READ)).allowed());
        assertTrue(adapter.evaluate(user(1L, "ADMIN"), request(
                AiCapability.ADMIN_AUDIT_SUMMARY, AiResourceType.AUDIT_LOG, null,
                AiRequestedAction.READ)).allowed());
        assertTrue(adapter.evaluate(user(1L, "ADMIN"), request(
                AiCapability.ADMIN_CONFIG_DRAFT, AiResourceType.SYSTEM_CONFIG, null,
                AiRequestedAction.DRAFT)).allowed());

        verifyNoInteractions(userRepository);
    }

    @Test
    void deniesNonAdminBeforeTargetLookup() {
        AiCapabilityPermissionAdapter.Evaluation result = adapter.evaluate(user(2L, "STUDENT"), request(
                AiCapability.ADMIN_USER_STATUS_LOOKUP, AiResourceType.USER_ACCOUNT, 9L, AiRequestedAction.READ));

        assertDenied(result, ROLE_NOT_ALLOWED);
        verifyNoInteractions(userRepository);
    }

    @Test
    void allowsNonAdminTargetButNeverAdminTarget() {
        when(userRepository.findById(9L)).thenReturn(Optional.of(user(9L, "STUDENT")));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, "ADMIN")));
        User deleted = user(11L, "STUDENT");
        deleted.setDeleted(true);
        when(userRepository.findById(11L)).thenReturn(Optional.of(deleted));
        when(userRepository.findById(12L)).thenThrow(new IllegalStateException("database detail"));

        assertTrue(adapter.evaluate(user(1L, "ADMIN"), request(
                AiCapability.ADMIN_ACCOUNT_ACTION_DRAFT, AiResourceType.USER_ACCOUNT, 9L,
                AiRequestedAction.DRAFT)).allowed());
        AiCapabilityPermissionAdapter.Evaluation adminTarget = adapter.evaluate(user(1L, "ADMIN"), request(
                AiCapability.ADMIN_ACCOUNT_ACTION_DRAFT, AiResourceType.USER_ACCOUNT, 10L,
                AiRequestedAction.DRAFT));
        assertDenied(adminTarget, PRIVATE_RESOURCE);
        assertDenied(adapter.evaluate(user(1L, "ADMIN"), request(
                AiCapability.ADMIN_USER_STATUS_LOOKUP, AiResourceType.USER_ACCOUNT, 11L,
                AiRequestedAction.READ)), RESOURCE_UNAVAILABLE);
        assertDenied(adapter.evaluate(user(1L, "ADMIN"), request(
                AiCapability.ADMIN_USER_STATUS_LOOKUP, AiResourceType.USER_ACCOUNT, 12L,
                AiRequestedAction.READ)), RESOURCE_UNAVAILABLE);
        assertDenied(adapter.evaluate(user(1L, "ADMIN"), request(
                AiCapability.ADMIN_USER_STATUS_LOOKUP, AiResourceType.USER_ACCOUNT, 13L,
                AiRequestedAction.READ)), RESOURCE_UNAVAILABLE);
    }

    @Test
    void rejectsCrossDomainCapabilityBeforeAnyAdminResourceLookup() {
        AiCapabilityPermissionAdapter.Evaluation result = adapter.evaluate(user(1L, "ADMIN"), request(
                AiCapability.LAB_POLICY_READ, AiResourceType.LABORATORY, 10L, AiRequestedAction.READ));

        assertDenied(result, DOMAIN_MISMATCH);
        verifyNoInteractions(userRepository);
    }

    private static AiCapabilityRequest request(AiCapability capability, AiResourceType type, Long id,
                                               AiRequestedAction action) {
        return new AiCapabilityRequest(AiAssistantKey.ADMIN_ASSISTANT, 1L, capability,
                new AiCapabilityRequest.ResourceReference(type, id), null, action);
    }

    private static User user(Long id, String role) {
        User user = new User();
        user.setId(id);
        user.setActive(true);
        user.setDeleted(false);
        user.addRole(new Role(role, role));
        return user;
    }

    private static void assertDenied(AiCapabilityPermissionAdapter.Evaluation evaluation,
                                     com.web.labportalbackend.ai.enums.AiCapabilityDenialReason reason) {
        assertFalse(evaluation.allowed());
        assertEquals(reason, evaluation.denialReason());
        assertNull(evaluation.resolvedResource());
        assertTrue(evaluation.evidence().isEmpty());
    }
}
