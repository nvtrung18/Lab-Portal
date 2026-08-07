package com.web.labportalbackend.ai.security;

import com.web.labportalbackend.ai.enums.AiAssistantDomain;
import com.web.labportalbackend.ai.enums.AiCapabilityDenialReason;
import com.web.labportalbackend.ai.enums.AiCapabilityEvidence;
import com.web.labportalbackend.ai.service.AiCapabilityDecision;
import com.web.labportalbackend.ai.service.AiCapabilityRequest;
import com.web.labportalbackend.auth.entity.User;
import java.util.Set;

public interface AiCapabilityPermissionAdapter {

    AiAssistantDomain domain();

    Evaluation evaluate(User actor, AiCapabilityRequest request);

    record Evaluation(
            boolean allowed,
            AiCapabilityDecision.ResolvedResource resolvedResource,
            AiCapabilityDenialReason denialReason,
            Set<AiCapabilityEvidence> evidence) {

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
            return new Evaluation(true, resource, null, evidence);
        }

        public static Evaluation denied(AiCapabilityDenialReason reason) {
            return new Evaluation(false, null, reason, Set.of());
        }
    }
}
