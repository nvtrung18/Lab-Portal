package com.web.labportalbackend.ai.service.impl;

import com.web.labportalbackend.ai.enums.AiActionRiskBoundary;
import com.web.labportalbackend.ai.enums.AiAssistantDomain;
import com.web.labportalbackend.ai.enums.AiAssistantSystemRole;
import com.web.labportalbackend.ai.enums.AiCapabilityDecisionReason;
import com.web.labportalbackend.ai.enums.AiCapabilityDenialReason;
import com.web.labportalbackend.ai.enums.AiCapabilityEvidence;
import com.web.labportalbackend.ai.enums.AiRequestedAction;
import com.web.labportalbackend.ai.enums.AiResourceType;
import com.web.labportalbackend.ai.security.AiCapabilityPermissionAdapter;
import com.web.labportalbackend.ai.service.AiAssistantProfile;
import com.web.labportalbackend.ai.service.AiAssistantRegistry;
import com.web.labportalbackend.ai.service.AiAssistantRegistryException;
import com.web.labportalbackend.ai.service.AiAssistantRegistryFailure;
import com.web.labportalbackend.ai.service.AiCapabilityDecision;
import com.web.labportalbackend.ai.service.AiCapabilityDeniedException;
import com.web.labportalbackend.ai.service.AiCapabilityRequest;
import com.web.labportalbackend.ai.service.AiCapabilityResolver;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.enums.UserStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiCapabilityResolverImpl implements AiCapabilityResolver {

    private final AiAssistantRegistry registry;
    private final UserRepository userRepository;
    private final Map<AiAssistantDomain, AiCapabilityPermissionAdapter> adapters;

    public AiCapabilityResolverImpl(AiAssistantRegistry registry,
                                    UserRepository userRepository,
                                    List<AiCapabilityPermissionAdapter> adapters) {
        if (registry == null || userRepository == null) {
            throw new IllegalArgumentException("registry and userRepository are required");
        }
        this.registry = registry;
        this.userRepository = userRepository;
        this.adapters = validatedAdapters(adapters);
    }

    @Override
    @Transactional(readOnly = true)
    public AiCapabilityDecision resolve(AiCapabilityRequest request) {
        AiCapabilityDecision malformed = validateRequest(request);
        if (malformed != null) {
            return malformed;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || authentication.getName() == null || authentication.getName().isBlank()
                || "anonymousUser".equals(authentication.getName())) {
            return denied(request, AiCapabilityDecisionReason.DENIED_BY_REQUEST,
                    AiCapabilityDenialReason.UNAUTHENTICATED, request.capability().riskBoundary());
        }

        User actor;
        try {
            actor = userRepository.findByUsername(authentication.getName()).orElse(null);
        } catch (RuntimeException ex) {
            actor = null;
        }
        if (!isUsableActor(actor)) {
            return denied(request, AiCapabilityDecisionReason.DENIED_BY_REQUEST,
                    AiCapabilityDenialReason.ACTOR_UNAVAILABLE, request.capability().riskBoundary());
        }
        if (!request.actorId().equals(actor.getId())) {
            return denied(request, AiCapabilityDecisionReason.DENIED_BY_REQUEST,
                    AiCapabilityDenialReason.ACTOR_MISMATCH, request.capability().riskBoundary());
        }

        AiAssistantProfile profile;
        AiAssistantSystemRole selectedRole;
        try {
            AiAssistantProfile catalogProfile = registry.getProfile(request.assistantKey());
            if (!validProfileIdentity(catalogProfile, request.assistantKey())) {
                return denied(request, AiCapabilityDecisionReason.DENIED_BY_REGISTRY,
                        AiCapabilityDenialReason.ASSISTANT_MISCONFIGURED, request.capability().riskBoundary());
            }
            selectedRole = selectRole(actor, catalogProfile.allowedSystemRoles());
            if (selectedRole == null) {
                return denied(request, AiCapabilityDecisionReason.DENIED_BY_REGISTRY,
                        AiCapabilityDenialReason.ROLE_NOT_ALLOWED, request.capability().riskBoundary());
            }
            profile = registry.getAvailableProfile(request.assistantKey(), selectedRole);
            if (!validProfileIdentity(profile, request.assistantKey()) || profile.domain() != catalogProfile.domain()) {
                return denied(request, AiCapabilityDecisionReason.DENIED_BY_REGISTRY,
                        AiCapabilityDenialReason.ASSISTANT_MISCONFIGURED, request.capability().riskBoundary());
            }
        } catch (AiAssistantRegistryException ex) {
            return denied(request, AiCapabilityDecisionReason.DENIED_BY_REGISTRY,
                    mapRegistryFailure(ex.failure()), request.capability().riskBoundary());
        } catch (RuntimeException ex) {
            return denied(request, AiCapabilityDecisionReason.DENIED_BY_REGISTRY,
                    AiCapabilityDenialReason.ASSISTANT_MISCONFIGURED, request.capability().riskBoundary());
        }

        if (request.capability().domain() != profile.domain()) {
            return denied(request, AiCapabilityDecisionReason.DENIED_BY_DOMAIN,
                    AiCapabilityDenialReason.DOMAIN_MISMATCH, request.capability().riskBoundary());
        }

        AiCapabilityPermissionAdapter.Evaluation evaluation;
        try {
            evaluation = adapters.get(profile.domain()).evaluate(actor, request);
        } catch (RuntimeException ex) {
            evaluation = AiCapabilityPermissionAdapter.Evaluation.denied(
                    AiCapabilityDenialReason.RESOURCE_UNAVAILABLE);
        }
        if (evaluation == null || (!evaluation.allowed() && evaluation.denialReason() == null)) {
            return denied(request, AiCapabilityDecisionReason.DENIED_BY_RESOURCE_POLICY,
                    AiCapabilityDenialReason.RESOURCE_UNAVAILABLE, request.capability().riskBoundary());
        }
        if (!evaluation.allowed()) {
            return denied(request, AiCapabilityDecisionReason.DENIED_BY_RESOURCE_POLICY,
                    evaluation.denialReason(), request.capability().riskBoundary());
        }

        EnumSet<AiCapabilityEvidence> evidence = EnumSet.of(
                AiCapabilityEvidence.AUTHENTICATED_ACTOR,
                AiCapabilityEvidence.REGISTRY_PROFILE,
                AiCapabilityEvidence.SYSTEM_ROLE,
                AiCapabilityEvidence.CAPABILITY_CATALOG,
                AiCapabilityEvidence.NO_ADDITIONAL_GRANT,
                AiCapabilityEvidence.ACTION_RISK_BOUNDARY);
        evidence.addAll(evaluation.evidence());
        return new AiCapabilityDecision(true, request.assistantKey(), profile.domain(), request.capability(),
                evaluation.resolvedResource(), AiCapabilityDecisionReason.ALLOWED_BY_EFFECTIVE_PERMISSION,
                null, request.capability().riskBoundary(), evidence);
    }

    @Override
    @Transactional(readOnly = true)
    public AiCapabilityDecision requireAllowed(AiCapabilityRequest request) {
        AiCapabilityDecision decision = resolve(request);
        if (!decision.allowed()) {
            throw new AiCapabilityDeniedException(decision);
        }
        return decision;
    }

    private AiCapabilityDecision validateRequest(AiCapabilityRequest request) {
        if (request == null) {
            return denied(null, AiCapabilityDecisionReason.DENIED_BY_REQUEST,
                    AiCapabilityDenialReason.MALFORMED_REQUEST, null);
        }
        if (request.assistantKey() == null) {
            return denied(request, AiCapabilityDecisionReason.DENIED_BY_REQUEST,
                    AiCapabilityDenialReason.UNKNOWN_ASSISTANT, null);
        }
        if (request.capability() == null) {
            return denied(request, AiCapabilityDecisionReason.DENIED_BY_REQUEST,
                    AiCapabilityDenialReason.UNKNOWN_CAPABILITY, null);
        }
        if (request.actorId() == null || request.actorId() <= 0) {
            return denied(request, AiCapabilityDecisionReason.DENIED_BY_REQUEST,
                    AiCapabilityDenialReason.MALFORMED_REQUEST, request.capability().riskBoundary());
        }
        if (request.resource() == null) {
            return denied(request, AiCapabilityDecisionReason.DENIED_BY_REQUEST,
                    AiCapabilityDenialReason.RESOURCE_REQUIRED, request.capability().riskBoundary());
        }
        if (request.resource().type() != request.capability().resourceType()) {
            return denied(request, AiCapabilityDecisionReason.DENIED_BY_REQUEST,
                    AiCapabilityDenialReason.RESOURCE_TYPE_MISMATCH, request.capability().riskBoundary());
        }
        boolean global = isGlobal(request.resource().type());
        if ((global && request.resource().id() != null)
                || (!global && (request.resource().id() == null || request.resource().id() <= 0))) {
            return denied(request, AiCapabilityDecisionReason.DENIED_BY_REQUEST,
                    AiCapabilityDenialReason.MALFORMED_REQUEST, request.capability().riskBoundary());
        }
        if (!validParent(request)) {
            return denied(request, AiCapabilityDecisionReason.DENIED_BY_REQUEST,
                    AiCapabilityDenialReason.RESOURCE_TYPE_MISMATCH, request.capability().riskBoundary());
        }
        if (request.requestedAction() == AiRequestedAction.MUTATION) {
            return denied(request, AiCapabilityDecisionReason.DENIED_BY_ACTION_RISK,
                    AiCapabilityDenialReason.PROHIBITED_ACTION, AiActionRiskBoundary.PROHIBITED);
        }
        if (request.requestedAction() == null || request.requestedAction() != request.capability().action()) {
            return denied(request, AiCapabilityDecisionReason.DENIED_BY_ACTION_RISK,
                    AiCapabilityDenialReason.ACTION_NOT_ALLOWED, request.capability().riskBoundary());
        }
        return null;
    }

    private boolean validParent(AiCapabilityRequest request) {
        AiResourceType expected = request.capability().parentResourceType();
        if (expected == null) {
            return request.parentResource() == null;
        }
        return request.parentResource() != null
                && request.parentResource().type() == expected
                && request.parentResource().id() != null
                && request.parentResource().id() > 0;
    }

    private static Map<AiAssistantDomain, AiCapabilityPermissionAdapter> validatedAdapters(
            List<AiCapabilityPermissionAdapter> adapters) {
        if (adapters == null || adapters.size() != AiAssistantDomain.values().length) {
            throw new IllegalArgumentException("exactly one adapter per assistant domain is required");
        }
        Map<AiAssistantDomain, AiCapabilityPermissionAdapter> result = new EnumMap<>(AiAssistantDomain.class);
        for (AiCapabilityPermissionAdapter adapter : adapters) {
            if (adapter == null || adapter.domain() == null || result.put(adapter.domain(), adapter) != null) {
                throw new IllegalArgumentException("assistant domain adapters must be unique");
            }
        }
        if (result.size() != AiAssistantDomain.values().length) {
            throw new IllegalArgumentException("all assistant domain adapters are required");
        }
        return Map.copyOf(result);
    }

    private static boolean isUsableActor(User actor) {
        return actor != null && actor.getId() != null
                && Boolean.TRUE.equals(actor.getActive())
                && !Boolean.TRUE.equals(actor.getDeleted())
                && actor.getStatus() == UserStatus.ACTIVE;
    }

    private static AiAssistantSystemRole selectRole(User actor, Set<AiAssistantSystemRole> allowedRoles) {
        if (allowedRoles == null) {
            return null;
        }
        for (AiAssistantSystemRole role : AiAssistantSystemRole.values()) {
            if (allowedRoles.contains(role) && actor.hasRole(role.name())) {
                return role;
            }
        }
        return null;
    }

    private static boolean validProfileIdentity(AiAssistantProfile profile,
                                                com.web.labportalbackend.ai.enums.AiAssistantKey key) {
        return profile != null && profile.key() == key && profile.domain() != null
                && profile.allowedSystemRoles() != null;
    }

    private static AiCapabilityDenialReason mapRegistryFailure(AiAssistantRegistryFailure failure) {
        if (failure == null) {
            return AiCapabilityDenialReason.ASSISTANT_MISCONFIGURED;
        }
        return switch (failure) {
            case UNKNOWN_ASSISTANT -> AiCapabilityDenialReason.UNKNOWN_ASSISTANT;
            case ASSISTANT_UNAVAILABLE -> AiCapabilityDenialReason.ASSISTANT_DISABLED;
            case ROLE_INELIGIBLE -> AiCapabilityDenialReason.ROLE_NOT_ALLOWED;
            case MALFORMED_CATALOG -> AiCapabilityDenialReason.ASSISTANT_MISCONFIGURED;
        };
    }

    private static boolean isGlobal(AiResourceType type) {
        return type == AiResourceType.SYSTEM || type == AiResourceType.AUDIT_LOG
                || type == AiResourceType.SYSTEM_CONFIG;
    }

    private static AiCapabilityDecision denied(AiCapabilityRequest request,
                                               AiCapabilityDecisionReason decisionReason,
                                               AiCapabilityDenialReason denialReason,
                                               AiActionRiskBoundary riskBoundary) {
        return new AiCapabilityDecision(false,
                request == null ? null : request.assistantKey(),
                request == null || request.capability() == null ? null : request.capability().domain(),
                request == null ? null : request.capability(),
                null, decisionReason, denialReason, riskBoundary, Set.of());
    }
}
