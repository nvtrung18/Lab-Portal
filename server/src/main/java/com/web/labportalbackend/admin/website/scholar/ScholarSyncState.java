package com.web.labportalbackend.admin.website.scholar;

import java.time.Instant;
import java.util.List;

public record ScholarSyncState(String authorId, String status, Instant lastAttemptAt, Instant lastSuccessAt,
                               Instant nextAttemptAt, String runId, String error,
                               List<Failure> errors, ScholarSnapshot snapshot) {
    public record Failure(Instant at, String code) {}
    public static ScholarSyncState empty(String id) {
        return new ScholarSyncState(id, "NEVER_SYNCED", null, null, null, null, null, List.of(), null);
    }
}
