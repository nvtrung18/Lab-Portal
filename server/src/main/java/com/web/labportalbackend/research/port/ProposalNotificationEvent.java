package com.web.labportalbackend.research.port;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record ProposalNotificationEvent(
        int schemaVersion,
        ProposalNotificationType type,
        Long proposalId,
        Long actorId,
        Long projectId,
        Long groupId,
        List<Long> recipientUserIds,
        Long createdTaskId,
        Instant occurredAt
) {

    public ProposalNotificationEvent {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("Unsupported proposal notification schema version");
        }
        Objects.requireNonNull(type, "Proposal notification type is required");
        Objects.requireNonNull(proposalId, "Proposal ID is required");
        Objects.requireNonNull(actorId, "Actor ID is required");
        Objects.requireNonNull(projectId, "Project ID is required");
        Objects.requireNonNull(groupId, "Group ID is required");
        Objects.requireNonNull(recipientUserIds, "Recipient user IDs are required");
        Objects.requireNonNull(occurredAt, "Occurrence time is required");

        recipientUserIds = List.copyOf(recipientUserIds);
        if (recipientUserIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Recipient user IDs cannot contain null");
        }
        if (new HashSet<>(recipientUserIds).size() != recipientUserIds.size()) {
            throw new IllegalArgumentException("Recipient user IDs must be distinct");
        }
        for (int index = 1; index < recipientUserIds.size(); index++) {
            if (recipientUserIds.get(index - 1).compareTo(recipientUserIds.get(index)) >= 0) {
                throw new IllegalArgumentException("Recipient user IDs must be sorted ascending");
            }
        }

        if (type == ProposalNotificationType.APPROVED && createdTaskId == null) {
            throw new IllegalArgumentException("Approved proposal notification requires a created task ID");
        }
        if (type != ProposalNotificationType.APPROVED && createdTaskId != null) {
            throw new IllegalArgumentException("Created task ID is only valid for approved proposal notifications");
        }
    }

    public enum ProposalNotificationType {
        SUBMITTED,
        APPROVED,
        REJECTED
    }
}
