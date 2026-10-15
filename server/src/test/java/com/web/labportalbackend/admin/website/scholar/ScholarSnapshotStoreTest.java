package com.web.labportalbackend.admin.website.scholar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.labportalbackend.admin.systemconfig.entity.SystemConfigEntity;
import com.web.labportalbackend.admin.systemconfig.repository.SystemConfigRepository;
import com.web.labportalbackend.admin.website.dto.WebsiteContent;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScholarSnapshotStoreTest {
    final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    final SystemConfigRepository repository = mock(SystemConfigRepository.class);
    final ScholarSyncProperties properties = new ScholarSyncProperties();
    final ScholarSnapshotStore store = new ScholarSnapshotStore(repository, mapper, properties);
    final Instant now = Instant.parse("2026-10-15T01:00:00Z");
    final ScholarSnapshot old = new ScholarSnapshot(null, List.of(new WebsiteContent.ScholarPublication("Old", "", "", null, null, "https://example.org/old")), now.minus(Duration.ofDays(8)), null);
    SystemConfigEntity row(ScholarSyncState state) throws Exception {
        var entity = new SystemConfigEntity(); entity.setConfigKey(ScholarSnapshotStore.PREFIX + "author");
        entity.setConfigValueJson(mapper.writeValueAsString(state)); return entity;
    }
    ScholarSyncState state(String status, Instant attempt, Instant next) {
        return new ScholarSyncState("author", status, attempt, old.fetchedAt(), next, "run", null, List.of(), old);
    }
    void lock(SystemConfigEntity entity) {
        when(repository.findByConfigKeyForUpdate(ScholarSnapshotStore.CMS_KEY)).thenReturn(Optional.of(new SystemConfigEntity()));
        when(repository.findByConfigKeyForUpdate(ScholarSnapshotStore.PREFIX + "author")).thenReturn(Optional.of(entity));
    }
    @Test void claimIsDurableAndNoCmsWritesOrConcurrentDuplicate() throws Exception {
        var entity = row(state("SUCCEEDED", now.minus(Duration.ofDays(8)), now.minusSeconds(1))); lock(entity);
        var claim = store.begin("author", false, true, now);
        assertEquals("RUNNING", claim.status()); assertEquals(old, claim.snapshot());
        assertNull(store.begin("author", true, true, now.plusSeconds(90)));
        verify(repository, times(1)).save(entity);
        verify(repository, never()).save(argThat(e -> ScholarSnapshotStore.CMS_KEY.equals(e.getConfigKey())));
    }
    @Test void waitsSevenDaysAndRecoversExpiredLease() throws Exception {
        var entity = row(state("SUCCEEDED", now.minusSeconds(120), now.plus(Duration.ofDays(6)))); lock(entity);
        assertNull(store.begin("author", false, true, now));
        entity.setConfigValueJson(mapper.writeValueAsString(state("RUNNING", now.minus(Duration.ofHours(3)), now.minusSeconds(1))));
        assertEquals("RUNNING", store.begin("author", false, true, now).status());
    }
    @Test void failureAndMissingKeyRetainPreviousSnapshotAndSuccessTimestamp() throws Exception {
        var entity = row(state("RUNNING", now, now.plusSeconds(7200))); lock(entity);
        store.finish("author", "run", null, "PROVIDER_HTTP_429", now);
        var saved = mapper.readValue(entity.getConfigValueJson(), ScholarSyncState.class);
        assertEquals("FAILED", saved.status()); assertEquals(old, saved.snapshot()); assertEquals(old.fetchedAt(), saved.lastSuccessAt());
        assertEquals(1, saved.errors().size()); assertEquals(now.plus(Duration.ofDays(1)), saved.nextAttemptAt());
        var missing = store.begin("author", true, false, now.plusSeconds(61));
        assertEquals("CONFIGURATION_REQUIRED", missing.status()); assertEquals(old, missing.snapshot());
    }
    @Test void successSchedulesExactlySevenDaysAndRejectsStaleWorker() throws Exception {
        var entity = row(state("RUNNING", now, now.plusSeconds(7200))); lock(entity);
        var fresh = new ScholarSnapshot(null, old.publications(), now, null);
        store.finish("author", "other-run", fresh, null, now); verify(repository, never()).save(any());
        store.finish("author", "run", fresh, null, now);
        var saved = mapper.readValue(entity.getConfigValueJson(), ScholarSyncState.class);
        assertEquals(now.plus(Duration.ofDays(7)), saved.nextAttemptAt()); assertEquals(fresh, saved.snapshot()); assertEquals("SUCCEEDED", saved.status());
    }
    @Test void oversizedSnapshotIsFailureAndDoesNotReplaceOldData() throws Exception {
        properties.setMaxSnapshotBytes(1);
        var entity = row(state("RUNNING", now, null)); lock(entity);
        store.finish("author", "run", old, null, now);
        var saved = mapper.readValue(entity.getConfigValueJson(), ScholarSyncState.class);
        assertEquals("FAILED", saved.status()); assertEquals(old, saved.snapshot()); assertTrue(saved.error().contains("SIZE_LIMIT"));
    }
    @Test void overlaysMoreThan100WithoutChangingEditedProfileFieldsOrCmsPayload() throws Exception {
        List<WebsiteContent.ScholarPublication> articles = new ArrayList<>();
        for (int i=0;i<101;i++) articles.add(new WebsiteContent.ScholarPublication("Paper "+i,"A","Venue",2026,0,"https://example.org/"+i));
        var fresh = new ScholarSnapshot(null, articles, now, null);
        var entity = row(new ScholarSyncState("author","SUCCEEDED",now,now,null,null,null,List.of(),fresh));
        when(repository.findByConfigKeyInAndDeletedFalse(anyCollection())).thenReturn(List.of(entity));
        var text = new WebsiteContent.LocalizedText("Edited English", "Đã biên tập");
        var profile = new WebsiteContent.ScholarProfile("https://scholar.google.com/citations?user=author",text,List.of(text),"https://example.org","2026-01-01",null,old.publications());
        var member = new WebsiteContent.Member("m",text,text,true,"Core Member","A","area","public@example.org","#ffffff","/image.png",7L,profile);
        String before = mapper.writeValueAsString(member);
        var merged = store.overlay(List.of(member)).getFirst();
        assertEquals(101, merged.scholarProfile().publications().size()); assertEquals(member.title(), merged.title());
        assertEquals(member.description(), merged.description()); assertEquals(member.role(), merged.role()); assertEquals(member.image(), merged.image());
        assertEquals(member.userId(), merged.userId()); assertEquals(profile.affiliation(), merged.scholarProfile().affiliation());
        assertEquals(before, mapper.writeValueAsString(member)); verify(repository, never()).save(any());
    }
}
