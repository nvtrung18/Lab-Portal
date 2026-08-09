package com.web.labportalbackend.ai.security;

import com.web.labportalbackend.ai.enums.AiAssistantDomain;
import com.web.labportalbackend.ai.enums.AiAssistantSystemRole;
import com.web.labportalbackend.ai.enums.AiCapabilityDenialReason;
import com.web.labportalbackend.ai.enums.AiCapabilityEvidence;
import com.web.labportalbackend.ai.service.AiCapabilityDecision;
import com.web.labportalbackend.ai.service.AiCapabilityRequest;
import com.web.labportalbackend.auth.entity.User;
import java.util.Set;

public interface AiCapabilityPermissionAdapter {

    AiAssistantDomain domain();

    /** The resolver supplies the only role this evaluation may authorize. */
    Evaluation evaluate(User actor, AiCapabilityRequest request, AiAssistantSystemRole selectedSystemRole);

    record Evaluation(
            boolean allowed,
            AiCapabilityDecision.ResolvedResource resolvedResource,
            AiCapabilityDenialReason denialReason,
            Set<AiCapabilityEvidence> evidence,
            AiCapabilityDecision.CheckinGuidancePolicySnapshot checkinGuidancePolicySnapshot) {

        public Evaluation {
            if (allowed == (denialReason != null)) {
                throw new IllegalArgumentException("adapter evaluation is inconsistent");
            }
            if (allowed && resolvedResource == null) {
                throw new IllegalArgumentException("allowed evaluation requires a resolved resource");
            }
            evidence = evidence == null ? Set.of() : Set.copyOf(evidence);
        }

        public static Evaluation allowed(AiCapabilityDecision.ResolvedResource resource,
                                         Set<AiCapabilityEvidence> evidence) {
            return new Evaluation(true, resource, null, evidence, null);
        }

        public static Evaluation allowed(AiCapabilityDecision.ResolvedResource resource,
                                         Set<AiCapabilityEvidence> evidence,
                                         AiCapabilityDecision.CheckinGuidancePolicySnapshot snapshot) {
            return new Evaluation(true, resource, null, evidence, snapshot);
        }

        public static Evaluation denied(AiCapabilityDenialReason reason) {
            return new Evaluation(false, null, reason, Set.of(), null);
        }
    }
}
