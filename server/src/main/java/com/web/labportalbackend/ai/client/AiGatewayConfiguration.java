package com.web.labportalbackend.ai.client;

import java.net.URI;

public record AiGatewayConfiguration(String baseUrl, String internalServiceToken) {

    public AiGatewayConfiguration {
        baseUrl = validatedBaseUrl(baseUrl);
        if (internalServiceToken == null || internalServiceToken.isBlank()) {
            throw new IllegalArgumentException("AI gateway token must be configured");
        }
    }

    private static String validatedBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("AI gateway base URL must be configured");
        }
        try {
            URI uri = URI.create(value.trim());
            if (!uri.isAbsolute() || uri.getHost() == null
                    || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("AI gateway base URL is invalid");
            }
            return uri.toString();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("AI gateway base URL is invalid");
        }
    }
}
