package com.web.labportalbackend.admin.website.scholar;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.labportalbackend.admin.systemconfig.entity.SystemConfigEntity;
import com.web.labportalbackend.admin.systemconfig.repository.SystemConfigRepository;
import com.web.labportalbackend.admin.website.dto.WebsiteContent;
import com.web.labportalbackend.admin.website.dto.WebsiteState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Stream;

/** Separate rows in existing JSON storage. Never rewrites CMS draft, published, or revision. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScholarSnapshotStore {
    static final String CMS_KEY = "PUBLIC_WEBSITE_CONTENT";
    static final String PREFIX = "WEBSITE_SCHOLAR_";
    private final SystemConfigRepository repository;
    private final ObjectMapper mapper;
    private final ScholarSyncProperties properties;
    public record Target(String memberId, String authorId) {}

    @Transactional(readOnly = true)
    public List<Target> targets() {
        return repository.findByConfigKeyAndDeletedFalse(CMS_KEY).map(entity -> {
            WebsiteState state = read(entity.getConfigValueJson(), WebsiteState.class);
            Map<String, Target> targets = new LinkedHashMap<>();
            Stream.of(state.draft(), state.published()).filter(Objects::nonNull)
                    .flatMap(content -> content.data().members().stream()).filter(member -> member.scholarProfile() != null)
                    .forEach(member -> ScholarAuthorId.fromUrl(member.scholarProfile().url()).ifPresent(id ->
                            targets.putIfAbsent(member.id() + ":" + id, new Target(member.id(), id))));
            return List.copyOf(targets.values());
        }).orElse(List.of());
    }

    @Transactional(readOnly = true)
    public Map<String, ScholarSyncState> states(Collection<String> authorIds) {
        if (authorIds.isEmpty()) return Map.of();
        Map<String, ScholarSyncState> result = new HashMap<>();
        for (SystemConfigEntity entity : repository.findByConfigKeyInAndDeletedFalse(authorIds.stream().distinct().map(id -> PREFIX + id).toList())) {
            try {
                ScholarSyncState state = read(entity.getConfigValueJson(), ScholarSyncState.class);
                if (state == null || !(PREFIX + state.authorId()).equals(entity.getConfigKey())
                        || (state.snapshot() != null && (state.snapshot().fetchedAt() == null || state.snapshot().publications() == null)))
                    throw new IllegalStateException("Invalid snapshot shape");
                result.put(state.authorId(), state);
            } catch (IllegalStateException ex) { log.warn("Scholar snapshot unreadable; CMS fallback retained"); }
        }
        return result;
    }

    @Transactional
    public ScholarSyncState begin(String authorId, boolean force, boolean configured, Instant now) {
        // A short CMS row lock serializes first snapshot creation across instances. It is NEVER changed.
        // The provider request happens after this transaction commits.
        repository.findByConfigKeyForUpdate(CMS_KEY).orElseThrow(() -> new IllegalArgumentException("Save the member in the CMS first."));
        SystemConfigEntity entity = repository.findByConfigKeyForUpdate(PREFIX + authorId).orElseGet(() -> {
            SystemConfigEntity fresh = new SystemConfigEntity(); fresh.setConfigKey(PREFIX + authorId); return fresh;
        });
        ScholarSyncState previous = entity.getConfigValueJson() == null ? ScholarSyncState.empty(authorId)
                : read(entity.getConfigValueJson(), ScholarSyncState.class);
        if ("RUNNING".equals(previous.status()) && previous.lastAttemptAt() != null
                && previous.lastAttemptAt().plus(Duration.ofHours(2)).isAfter(now)) return null;
        if (!force && previous.nextAttemptAt() != null && previous.nextAttemptAt().isAfter(now)) return null;
        if (force && previous.lastAttemptAt() != null && previous.lastAttemptAt().plusSeconds(60).isAfter(now)) return null;
        ScholarSyncState next = new ScholarSyncState(authorId, configured ? "RUNNING" : "CONFIGURATION_REQUIRED", now,
                previous.lastSuccessAt(), now.plus(configured ? Duration.ofHours(2) : Duration.ofDays(1)),
                configured ? UUID.randomUUID().toString() : null, configured ? null : "API_KEY_MISSING",
                configured ? previous.errors() : failures(previous, now, "API_KEY_MISSING"), previous.snapshot());
        entity.setConfigValueJson(write(next)); repository.save(entity); return next;
    }

    @Transactional
    public void finish(String authorId, String runId, ScholarSnapshot snapshot, String error, Instant now) {
        SystemConfigEntity entity = repository.findByConfigKeyForUpdate(PREFIX + authorId).orElseThrow();
        ScholarSyncState previous = read(entity.getConfigValueJson(), ScholarSyncState.class);
        if (!Objects.equals(runId, previous.runId()) || !"RUNNING".equals(previous.status())) return;
        if (snapshot != null && write(snapshot).getBytes(StandardCharsets.UTF_8).length > properties.getMaxSnapshotBytes()) {
            snapshot = null; error = "SNAPSHOT_SIZE_LIMIT_REACHED_RETAINED";
        }
        boolean success = snapshot != null && error == null;
        if (!success && error == null) error = "SYNC_FAILED";
        if (!success) log.warn("Scholar snapshot for author {} retained: {}", authorId, error);
        ScholarSyncState next = new ScholarSyncState(authorId, success ? "SUCCEEDED" : "FAILED", previous.lastAttemptAt(),
                success ? now : previous.lastSuccessAt(), now.plus(Duration.ofDays(success ? 7 : 1)), null, error,
                success ? previous.errors() : failures(previous, now, error), success ? snapshot : previous.snapshot());
        entity.setConfigValueJson(write(next)); repository.save(entity);
    }

    private List<ScholarSyncState.Failure> failures(ScholarSyncState previous, Instant now, String error) {
        List<ScholarSyncState.Failure> errors = new ArrayList<>(previous.errors() == null ? List.of() : previous.errors());
        errors.add(new ScholarSyncState.Failure(now, error));
        return List.copyOf(errors.subList(Math.max(0, errors.size() - 10), errors.size()));
    }

    /** Only source fields are overlaid. Names, roles, portraits, affiliation and content are preserved. */
    @Transactional(readOnly = true)
    public List<WebsiteContent.Member> overlay(List<WebsiteContent.Member> members) {
        Map<String, ScholarSyncState> snapshots = states(members.stream().filter(m -> m.scholarProfile() != null)
                .flatMap(m -> ScholarAuthorId.fromUrl(m.scholarProfile().url()).stream()).distinct().toList());
        return members.stream().map(member -> {
            var profile = member.scholarProfile();
            if (profile == null) return member;
            ScholarSyncState state = ScholarAuthorId.fromUrl(profile.url()).map(snapshots::get).orElse(null);
            if (state == null || state.snapshot() == null) return member;
            ScholarSnapshot snapshot = state.snapshot();
            var merged = new WebsiteContent.ScholarProfile(profile.url(), profile.affiliation(), profile.interests(),
                    profile.homepageUrl(), snapshot.fetchedAt().atZone(ZoneOffset.UTC).toLocalDate().toString(),
                    snapshot.metrics() == null ? profile.metrics() : snapshot.metrics(), snapshot.publications());
            return new WebsiteContent.Member(member.id(), member.title(), member.description(), member.visible(), member.role(),
                    member.initials(), member.area(), member.email(), member.color(), member.image(), member.userId(), merged);
        }).toList();
    }

    private <T> T read(String json, Class<T> type) {
        try { return mapper.readValue(json, type); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Stored website data cannot be read."); }
    }
    private String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Scholar snapshot cannot be saved."); }
    }
}
