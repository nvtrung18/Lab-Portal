# P14-T2 Security Regression Evidence

- Date: 2026-09-01 (Asia/Saigon)
- Result: `BUILD SUCCESS`
- Targeted tests: 316 run; 0 failures; 0 errors; 0 skipped
- Duration: 1 minute 21 seconds

## Denial matrix

| Boundary | Regression coverage |
|---|---|
| Cross-domain assistant/tool requests | `AiAssistantProfileTest`, `AiCapabilityResolverImplTest` |
| Cross-group research access | `AiResearchCapabilityPermissionAdapterTest`, `AiContextAuthorizationProjectionIntegrationTest` |
| Cross-lab access | `AiLabCapabilityPermissionAdapterTest`, `AiContextAuthorizationProjectionIntegrationTest` |
| Missing, stale, or mismatched grants | `AiContextAuthorizationProjectionIntegrationTest`, `AiCapabilityResolverImplTest` |
| Disabled or unauthorized assistants | `AiAssistantAvailabilityServiceImplTest`, `AiAssistantControllerTest` |
| Prohibited, mismatched, or unconfirmed tools | `AiToolPolicyResolverImplTest`, `AiToolExecutionServiceImplTest` |
| Face profile and booking ownership | `FaceProfileControllerTest`, `FaceCheckinServiceImplTest` |
| Notification ownership | `NotificationControllerTest`, `NotificationServiceImplTest` |

The targeted suite verifies fail-closed RBAC, ownership, domain, resource, and
grant behavior for the sensitive surfaces named by P14-T2. It supplements the
full P14-T1 backend suite and does not replace deployed-environment penetration
testing or the MySQL/Testcontainers tests gated by local infrastructure.
