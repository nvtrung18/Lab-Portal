package com.web.labportalbackend.admin.website.scholar;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

@Service
@Slf4j
public class ScholarSyncService {
    private final ScholarSnapshotStore store;
    private final ScholarProvider provider;
    private final ScholarSyncProperties properties;
    private final Executor executor;
    private final Set<String> queued = ConcurrentHashMap.newKeySet();
    public ScholarSyncService(ScholarSnapshotStore store, ScholarProvider provider, ScholarSyncProperties properties,
                              @Qualifier("scholarSyncExecutor") Executor executor) {
        this.store = store; this.provider = provider; this.properties = properties; this.executor = executor;
    }
    public record MemberStatus(String memberId, String authorId, String status, Instant lastAttemptAt,
                               Instant lastSuccessAt, Instant nextAttemptAt, Instant sourceUpdatedAt,
                               int publicationCount, String error, List<ScholarSyncState.Failure> errors) {}
    public record Status(boolean configured, boolean enabled, List<MemberStatus> members) {}
    public Status status() {
        var targets = store.targets();
        var states = store.states(targets.stream().map(ScholarSnapshotStore.Target::authorId).toList());
        return new Status(properties.configured(), properties.isEnabled(), targets.stream().map(target -> {
            var state = states.getOrDefault(target.authorId(), ScholarSyncState.empty(target.authorId()));
            String displayStatus = state.status();
            if ("RUNNING".equals(displayStatus) && state.lastAttemptAt() != null
                    && !state.lastAttemptAt().plus(java.time.Duration.ofHours(2)).isAfter(Instant.now())) displayStatus = "RETRY_REQUIRED";
            if (queued.contains(target.authorId()) && !"RUNNING".equals(displayStatus)) displayStatus = "QUEUED";
            return new MemberStatus(target.memberId(), target.authorId(), displayStatus, state.lastAttemptAt(), state.lastSuccessAt(),
                    state.nextAttemptAt(), state.snapshot() == null ? null : state.snapshot().sourceUpdatedAt(),
                    state.snapshot() == null ? 0 : state.snapshot().publications().size(), state.error(), state.errors());
        }).toList());
    }
    public void syncNow(String authorId) {
        if (store.targets().stream().noneMatch(target -> target.authorId().equals(authorId)))
            throw new IllegalArgumentException("This Scholar profile is not saved in the CMS.");
        submit(authorId, true);
    }
    @Scheduled(initialDelayString = "${website.scholar.initial-delay-ms:60000}",
            fixedDelayString = "${website.scholar.poll-delay-ms:3600000}")
    public void syncDue() {
        if (!properties.isEnabled() || !properties.configured()) return;
        try {
            var ids = store.targets().stream().map(ScholarSnapshotStore.Target::authorId).distinct().toList();
            var states = store.states(ids);
            Instant now = Instant.now();
            for (String authorId : ids) {
                var state = states.get(authorId);
                if (state != null && state.nextAttemptAt() != null && state.nextAttemptAt().isAfter(now)) continue;
                try { submit(authorId, false); }
                catch (Exception ex) { log.warn("Scholar sync could not be scheduled for author {}", authorId); }
            }
        } catch (Exception ex) { log.warn("Scholar discovery failed; all existing snapshots retained"); }
    }
    private void submit(String authorId, boolean force) {
        if (!properties.configured()) { store.begin(authorId, force, false, Instant.now()); return; }
        if (!queued.add(authorId)) return;
        try {
            executor.execute(() -> {
                try {
                    // Acquire the durable cross-instance lease only when the worker starts, not while queued.
                    ScholarSyncState claim = store.begin(authorId, force, true, Instant.now());
                    if (claim != null && "RUNNING".equals(claim.status())) run(claim);
                } catch (Exception ex) { log.warn("Scholar worker could not start for author {}; old data retained", authorId); }
                finally { queued.remove(authorId); }
            });
        } catch (RuntimeException ex) {
            queued.remove(authorId);
            var claim = store.begin(authorId, force, true, Instant.now());
            if (claim != null && "RUNNING".equals(claim.status()))
                store.finish(authorId, claim.runId(), null, "QUEUE_UNAVAILABLE", Instant.now());
            log.warn("Scholar queue is full for author {}; existing data retained", authorId);
        }
    }
    private void run(ScholarSyncState claim) {
        ScholarSnapshot snapshot = null; String error = null;
        try { snapshot = provider.fetch(claim.authorId()); }
        catch (ScholarSyncException ex) { error = ex.getMessage(); }
        catch (Exception ex) { error = "SYNC_FAILED"; }
        try {
            store.finish(claim.authorId(), claim.runId(), snapshot, error, Instant.now());
            if (error != null) log.warn("Scholar sync author {} failed: {}. Existing data retained.", claim.authorId(), error);
        } catch (Exception ex) { log.warn("Scholar snapshot persistence failed for author {}; old data retained", claim.authorId()); }
    }
}
