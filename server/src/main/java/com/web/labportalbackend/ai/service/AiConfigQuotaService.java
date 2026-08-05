package com.web.labportalbackend.ai.service;

/**
 * Trusted-server-only admission decision point for future AI callers.
 * Callers must derive quota keys from authenticated, server-authoritative context.
 */
public interface AiConfigQuotaService {

    AiQuotaDecision evaluate(AiQuotaCheckRequest request);
}
