package com.web.labportalbackend.ai.service;

/** Spring-owned outcome derived only from the canonical tool risk boundary. */
public enum AiToolActionGateDecision {
    ALLOW_READ_ONLY,
    RETURN_DRAFT_ONLY,
    REQUIRE_CONFIRMATION,
    REQUIRE_APPROVAL,
    DENY
}
