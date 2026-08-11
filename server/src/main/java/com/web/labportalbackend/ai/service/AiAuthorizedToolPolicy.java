package com.web.labportalbackend.ai.service;

/** A policy projects exactly one static descriptor; it grants no execution capability. */
public record AiAuthorizedToolPolicy(AiToolDefinition descriptor) {
    public AiAuthorizedToolPolicy {
        if (descriptor == null) {
            throw new IllegalArgumentException("authorized tool policy requires one descriptor");
        }
    }
}
