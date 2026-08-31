package com.web.labportalbackend.ai.context;

import com.web.labportalbackend.ai.enums.AiActionRiskBoundary;
import com.web.labportalbackend.ai.enums.AiAssistantDomain;
import com.web.labportalbackend.ai.enums.AiResourceType;
import com.web.labportalbackend.ai.enums.AiToolArgument;
import com.web.labportalbackend.ai.rag.enums.AiKnowledgeNamespace;
import com.web.labportalbackend.ai.rag.service.AiAuthorizedRetrieval;
import com.web.labportalbackend.ai.service.AiCapabilityDecision;
import com.web.labportalbackend.ai.service.AiToolDefinition;
import java.util.ArrayList;
import java.util.List;

/**
 * Explicit allowlist for the only Spring-authorized data placed under P8's authorizedContext field.
 */
public record AiPythonAuthorizedContext(
        AiAssistantDomain domain,
        String contextVersion,
        AiDomainContext context,
        List<AllowedTool> allowedTools,
        List<ResourceReference> resources,
        AiAuthorizedRetrieval authorizedRetrieval) {

    public AiPythonAuthorizedContext {
        if (domain == null || contextVersion == null || contextVersion.isBlank() || context == null
                || !matchesDomainContext(domain, context)
                || allowedTools == null || allowedTools.size() != 1
                || resources == null || resources.isEmpty() || authorizedRetrieval == null
                || !AiKnowledgeNamespace.forDomain(domain).value().equals(authorizedRetrieval.namespace())) {
            throw new AiContextReadDeniedException();
        }
        allowedTools = List.copyOf(allowedTools);
        resources = List.copyOf(resources);
    }

    public static AiPythonAuthorizedContext from(AiAuthorizedContext authorized,
                                                  AiAuthorizedRetrieval authorizedRetrieval) {
        if (authorized == null || authorized.resource() == null
                || !authorized.resource().hasValidIdentityShape()
                || authorized.toolPolicy() == null || authorized.toolPolicy().descriptor() == null) {
            throw new AiContextReadDeniedException();
        }
        AiToolDefinition tool = authorized.toolPolicy().descriptor();
        if (tool.domain() != authorized.domain()
                || tool.capability() != authorized.capability()
                || !tool.matchesResolvedResource(authorized.resource())) {
            throw new AiContextReadDeniedException();
        }

        List<ResourceReference> references = new ArrayList<>();
        references.add(new ResourceReference(authorized.resource().type(), authorized.resource().id()));
        if (tool.parentResourceType() != null) {
            Long parentId = parentId(authorized.resource(), tool.parentResourceType());
            if (parentId == null || parentId <= 0) {
                throw new AiContextReadDeniedException();
            }
            references.add(new ResourceReference(tool.parentResourceType(), parentId));
        }

        return new AiPythonAuthorizedContext(
                authorized.domain(),
                authorized.contextVersion(),
                authorized.context(),
                List.of(AllowedTool.from(tool)),
                references,
                authorizedRetrieval);
    }

    private static Long parentId(AiCapabilityDecision.ResolvedResource resource,
                                 AiResourceType parentType) {
        return switch (parentType) {
            case LABORATORY -> resource.labId();
            case PROJECT -> resource.projectId();
            case GROUP -> resource.groupId();
            case TASK -> resource.taskId();
            default -> null;
        };
    }

    public record AllowedTool(
            String toolId,
            String schemaVersion,
            AiResourceType resourceType,
            AiResourceType parentResourceType,
            List<String> argumentNames,
            AiActionRiskBoundary riskBoundary) {

        static AllowedTool from(AiToolDefinition tool) {
            List<String> arguments = tool.arguments().stream()
                    .sorted()
                    .map(AiPythonAuthorizedContext::argumentName)
                    .toList();
            return new AllowedTool(tool.id().value(), tool.schemaVersion(), tool.resourceType(),
                    tool.parentResourceType(), arguments, tool.riskBoundary());
        }
    }

    public record ResourceReference(AiResourceType resourceType, Long resourceId) {
        public ResourceReference {
            if (resourceType == null || (isGlobal(resourceType) ? resourceId != null
                    : resourceId == null || resourceId <= 0)) {
                throw new AiContextReadDeniedException();
            }
        }
    }

    private static String argumentName(AiToolArgument argument) {
        return switch (argument) {
            case RESOURCE -> "resource";
            case PARENT_RESOURCE -> "parentResource";
        };
    }

    private static boolean isGlobal(AiResourceType type) {
        return type == AiResourceType.SYSTEM || type == AiResourceType.AUDIT_LOG
                || type == AiResourceType.SYSTEM_CONFIG;
    }

    private static boolean matchesDomainContext(AiAssistantDomain domain, AiDomainContext context) {
        return switch (domain) {
            case ADMIN -> context instanceof AiAdminContext;
            case LAB -> context instanceof AiLabContext;
            case RESEARCH -> context instanceof AiResearchAssistantContext;
        };
    }
}
