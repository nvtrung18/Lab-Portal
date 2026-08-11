package com.web.labportalbackend.ai.service;

import com.web.labportalbackend.ai.enums.AiActionRiskBoundary;
import com.web.labportalbackend.ai.enums.AiAssistantDomain;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiAssistantSystemRole;
import com.web.labportalbackend.ai.enums.AiCapability;
import com.web.labportalbackend.ai.enums.AiCapabilityDecisionReason;
import com.web.labportalbackend.ai.enums.AiCapabilityDenialReason;
import com.web.labportalbackend.ai.enums.AiCapabilityEvidence;
import com.web.labportalbackend.ai.enums.AiResourceScope;
import com.web.labportalbackend.ai.enums.AiResourceType;
import java.time.Instant;
import java.util.Set;

public record AiCapabilityDecision(
        boolean allowed,
        Long acceptedActorId,
        AiAssistantSystemRole selectedSystemRole,
        AiAssistantKey assistantKey,
        AiAssistantDomain domain,
        AiCapability capability,
        ResolvedResource resolvedResource,
        AiCapabilityDecisionReason decisionReason,
        AiCapabilityDenialReason denialReason,
        AiActionRiskBoundary riskBoundary,
        Set<AiCapabilityEvidence> evidence,
        CheckinGuidancePolicySnapshot checkinGuidancePolicySnapshot) {

    public AiCapabilityDecision {
        if (decisionReason == null) {
            throw new IllegalArgumentException("decisionReason is required");
        }
        if (allowed == (denialReason != null)) {
            throw new IllegalArgumentException("allowed and denialReason are inconsistent");
        }
        if (allowed && (acceptedActorId == null || acceptedActorId <= 0)) {
            throw new IllegalArgumentException("allowed decision requires an accepted actor ID");
        }
        if (allowed && selectedSystemRole == null) {
            throw new IllegalArgumentException("allowed decision requires a selected system role");
        }
        if (!allowed && selectedSystemRole != null) {
            throw new IllegalArgumentException("denied decision must not contain a selected system role");
        }
        if (!allowed && acceptedActorId != null) {
            throw new IllegalArgumentException("denied decision must not contain an accepted actor ID");
        }
        if (allowed && resolvedResource == null) {
            throw new IllegalArgumentException("allowed decision requires a resolved resource");
        }
        boolean checkinGuidance = capability == AiCapability.LAB_CHECKIN_GUIDANCE;
        if (checkinGuidance && allowed && checkinGuidancePolicySnapshot == null) {
            throw new IllegalArgumentException("allowed check-in guidance requires a policy snapshot");
        }
        if (checkinGuidance && !allowed && checkinGuidancePolicySnapshot != null) {
            throw new IllegalArgumentException("denied check-in guidance must not contain a policy snapshot");
        }
        if (!checkinGuidance && checkinGuidancePolicySnapshot != null) {
            throw new IllegalArgumentException("only check-in guidance may contain a policy snapshot");
        }
        evidence = evidence == null ? Set.of() : Set.copyOf(evidence);
    }

    public record CheckinGuidancePolicySnapshot(Instant endInclusive) {
        public CheckinGuidancePolicySnapshot {
            if (endInclusive == null) {
                throw new IllegalArgumentException("check-in guidance end is required");
            }
        }
    }

    public record ResolvedResource(
            AiResourceType type,
            Long id,
            Long labId,
            Long projectId,
            Long groupId,
            Long taskId,
            AiResourceScope effectiveScope) {

        public ResolvedResource {
            if (type == null || effectiveScope == null) {
                throw new IllegalArgumentException("resolved resource type and scope are required");
            }
        }

        /**
         * The permission adapters construct this identity from authoritative persistence data.
         * Keep the shape check here so every consumer rejects a malformed identity consistently.
         */
        public boolean hasValidIdentityShape() {
            if (effectiveScope == AiResourceScope.GLOBAL) {
                return isGlobal() && id == null && labId == null && projectId == null
                        && groupId == null && taskId == null;
            }
            if (!positive(id)) {
                return false;
            }
            return switch (type) {
                case SYSTEM, AUDIT_LOG, SYSTEM_CONFIG -> false;
                case USER_ACCOUNT -> labId == null && projectId == null && groupId == null && taskId == null;
                case LABORATORY -> id.equals(labId) && projectId == null && groupId == null && taskId == null;
                case TIME_SLOT, BOOKING -> positive(labId) && projectId == null && groupId == null && taskId == null;
                case PROJECT -> positive(labId) && id.equals(projectId) && groupId == null && taskId == null;
                case GROUP -> positive(labId) && positive(projectId) && id.equals(groupId) && taskId == null;
                case TASK -> positive(labId) && positive(projectId) && positive(groupId) && id.equals(taskId);
                case REPORT -> positive(labId) && positive(projectId) && positive(groupId)
                        && (taskId == null || positive(taskId));
            };
        }

        private boolean isGlobal() {
            return type == AiResourceType.SYSTEM || type == AiResourceType.AUDIT_LOG
                    || type == AiResourceType.SYSTEM_CONFIG;
        }

        private static boolean positive(Long value) {
            return value != null && value > 0;
        }
    }
}
