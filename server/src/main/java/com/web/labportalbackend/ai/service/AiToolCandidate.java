package com.web.labportalbackend.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiResourceType;
import com.web.labportalbackend.ai.enums.AiToolId;

public record AiToolCandidate(
        AiAssistantKey assistantKey,
        String schemaVersion,
        AiToolId toolId,
        String description,
        ResourceReference resource,
        ResourceReference parentResource) {

    public AiToolCandidate {
        if (assistantKey == null || schemaVersion == null || schemaVersion.isBlank() || toolId == null
                || description == null || description.isBlank() || description.length() > 512 || resource == null) {
            throw new IllegalArgumentException("AI tool candidate is invalid");
        }
    }

    public ObjectNode toPlanningCandidate(ObjectMapper objectMapper) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("assistantKey", assistantKey.name());
        node.put("schemaVersion", schemaVersion);
        node.put("toolId", toolId.value());
        node.put("description", description);
        node.set("resource", resource.toJson(objectMapper));
        if (parentResource == null) {
            node.putNull("parentResource");
        } else {
            node.set("parentResource", parentResource.toJson(objectMapper));
        }
        return node;
    }

    public ObjectNode toCanonicalToolRequest(ObjectMapper objectMapper) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("assistantKey", assistantKey.name());
        node.put("schemaVersion", schemaVersion);
        node.put("toolId", toolId.value());
        ObjectNode arguments = objectMapper.createObjectNode();
        arguments.set("resource", resource.toJson(objectMapper));
        if (parentResource != null) {
            arguments.set("parentResource", parentResource.toJson(objectMapper));
        }
        node.set("arguments", arguments);
        return node;
    }

    public record ResourceReference(AiResourceType resourceType, Long resourceId) {
        public ResourceReference {
            if (resourceType == null || (!global(resourceType) && (resourceId == null || resourceId <= 0))
                    || (global(resourceType) && resourceId != null)) {
                throw new IllegalArgumentException("AI candidate resource is invalid");
            }
        }

        private ObjectNode toJson(ObjectMapper objectMapper) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("resourceType", resourceType.name());
            if (resourceId == null) {
                node.putNull("resourceId");
            } else {
                node.put("resourceId", resourceId);
            }
            return node;
        }

        private static boolean global(AiResourceType type) {
            return type == AiResourceType.SYSTEM || type == AiResourceType.AUDIT_LOG
                    || type == AiResourceType.SYSTEM_CONFIG;
        }
    }
}
