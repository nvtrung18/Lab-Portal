package com.web.labportalbackend.ai.security.impl;

import com.web.labportalbackend.ai.enums.AiAssistantDomain;
import com.web.labportalbackend.ai.enums.AiAssistantSystemRole;
import com.web.labportalbackend.ai.enums.AiCapability;
import com.web.labportalbackend.ai.enums.AiCapabilityDenialReason;
import com.web.labportalbackend.ai.enums.AiCapabilityEvidence;
import com.web.labportalbackend.ai.enums.AiResourceScope;
import com.web.labportalbackend.ai.enums.AiResourceType;
import com.web.labportalbackend.ai.security.AiCapabilityPermissionAdapter;
import com.web.labportalbackend.ai.service.AiCapabilityDecision;
import com.web.labportalbackend.ai.service.AiCapabilityRequest;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AiAdminCapabilityPermissionAdapter implements AiCapabilityPermissionAdapter {

    private final UserRepository userRepository;

    public AiAdminCapabilityPermissionAdapter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public AiAssistantDomain domain() {
        return AiAssistantDomain.ADMIN;
    }

    @Override
    public Evaluation evaluate(User actor, AiCapabilityRequest request, AiAssistantSystemRole selectedSystemRole) {
        if (selectedSystemRole != AiAssistantSystemRole.ADMIN || actor == null || !actor.hasRole("ADMIN")) {
            return Evaluation.denied(AiCapabilityDenialReason.ROLE_NOT_ALLOWED);
        }
        try {
            return switch (request.capability()) {
                case ADMIN_SYSTEM_SUMMARY, ADMIN_AUDIT_SUMMARY, ADMIN_CONFIG_DRAFT ->
                        allowGlobal(request.capability().resourceType());
                case ADMIN_USER_STATUS_LOOKUP, ADMIN_ACCOUNT_ACTION_DRAFT -> allowUserTarget(request);
                default -> Evaluation.denied(AiCapabilityDenialReason.DOMAIN_MISMATCH);
            };
        } catch (RuntimeException ex) {
            return Evaluation.denied(AiCapabilityDenialReason.RESOURCE_UNAVAILABLE);
        }
    }

    private Evaluation allowGlobal(AiResourceType type) {
        return Evaluation.allowed(new AiCapabilityDecision.ResolvedResource(
                        type, null, null, null, null, null, AiResourceScope.GLOBAL),
                Set.of(AiCapabilityEvidence.DERIVED_RESOURCE, AiCapabilityEvidence.EXISTING_PERMISSION));
    }

    private Evaluation allowUserTarget(AiCapabilityRequest request) {
        Long userId = request.resource().id();
        User target = userRepository.findById(userId)
                .filter(user -> !Boolean.TRUE.equals(user.getDeleted()))
                .orElse(null);
        if (target == null) {
            return Evaluation.denied(AiCapabilityDenialReason.RESOURCE_UNAVAILABLE);
        }
        if (target.hasRole("ADMIN")) {
            return Evaluation.denied(AiCapabilityDenialReason.PRIVATE_RESOURCE);
        }
        return Evaluation.allowed(new AiCapabilityDecision.ResolvedResource(
                        AiResourceType.USER_ACCOUNT, userId, null, null, null, null,
                        AiResourceScope.EXISTING_BUSINESS_PERMISSION),
                Set.of(AiCapabilityEvidence.DERIVED_RESOURCE, AiCapabilityEvidence.EXISTING_PERMISSION));
    }
}
