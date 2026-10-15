package com.web.labportalbackend.admin.website.scholar;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ScholarSyncServiceTest {
    final ScholarSnapshotStore store = mock(ScholarSnapshotStore.class);
    final ScholarProvider provider = mock(ScholarProvider.class);
    final ScholarSyncProperties properties = new ScholarSyncProperties();
    final Executor executor = Runnable::run;
    final ScholarSyncService service = new ScholarSyncService(store, provider, properties, executor);
    ScholarSyncState running() { return new ScholarSyncState("author", "RUNNING", Instant.now(), null, null, "run", null, List.of(), null); }
    @Test void schedulerSkipsWithoutKeyOrWhenDisabled() {
        service.syncDue(); properties.setEnabled(true); service.syncDue(); verifyNoInteractions(store, provider);
    }
    @Test void manualRequestRequiresSavedProfileAndNoKeyNeverContactsProvider() {
        when(store.targets()).thenReturn(List.of()); assertThrows(IllegalArgumentException.class, () -> service.syncNow("unknown"));
        when(store.targets()).thenReturn(List.of(new ScholarSnapshotStore.Target("m", "author")));
        when(store.begin(eq("author"), eq(true), eq(false), any())).thenReturn(new ScholarSyncState("author","CONFIGURATION_REQUIRED",null,null,null,null,"API_KEY_MISSING",List.of(),null));
        service.syncNow("author"); verifyNoInteractions(provider);
    }
    @Test void schedulerDeduplicatesAndChecksPersistedDueDateBeforeProviderCall() {
        properties.setEnabled(true); properties.setApiKey("test-only");
        when(store.targets()).thenReturn(List.of(new ScholarSnapshotStore.Target("m1","author"),new ScholarSnapshotStore.Target("m2","author")));
        when(store.begin(eq("author"),eq(false),eq(true),any())).thenReturn(null);
        service.syncDue(); verify(store, times(1)).begin(eq("author"),eq(false),eq(true),any()); verifyNoInteractions(provider);
    }
    @Test void providerFailureKeepsSnapshotThroughStoreAndUsesOnlySafeCode() {
        properties.setApiKey("test-only"); when(store.targets()).thenReturn(List.of(new ScholarSnapshotStore.Target("m","author")));
        when(store.begin(eq("author"),eq(true),eq(true),any())).thenReturn(running());
        when(provider.fetch("author")).thenThrow(new ScholarSyncException("PROVIDER_HTTP_429"));
        service.syncNow("author"); verify(store).finish(eq("author"),eq("run"),isNull(),eq("PROVIDER_HTTP_429"),any());
    }
    @Test void queueCoalescesRequestsAndDoesNotStartDatabaseLeaseUntilWorkerRuns() {
        properties.setApiKey("test-only");
        when(store.targets()).thenReturn(List.of(new ScholarSnapshotStore.Target("m","author")));
        java.util.List<Runnable> jobs = new java.util.ArrayList<>();
        var queuedService = new ScholarSyncService(store, provider, properties, jobs::add);
        queuedService.syncNow("author"); queuedService.syncNow("author");
        assertEquals(1, jobs.size()); verify(store, never()).begin(anyString(), anyBoolean(), anyBoolean(), any());
        jobs.getFirst().run(); verify(store).begin(eq("author"), eq(true), eq(true), any());
    }
    @Test void futureDueDateDoesNotQueueOrClaimAJob() {
        properties.setEnabled(true); properties.setApiKey("test-only");
        when(store.targets()).thenReturn(List.of(new ScholarSnapshotStore.Target("m","author")));
        var state = new ScholarSyncState("author","SUCCEEDED",Instant.now(),Instant.now(),Instant.now().plusSeconds(604800),null,null,List.of(),null);
        when(store.states(anyCollection())).thenReturn(java.util.Map.of("author",state));
        service.syncDue(); verify(store,never()).begin(anyString(),anyBoolean(),anyBoolean(),any());verifyNoInteractions(provider);
    }
    @Test void expiredLeaseOffersManualRetryInStatusInsteadOfDisablingTheButtonForever() {
        when(store.targets()).thenReturn(List.of(new ScholarSnapshotStore.Target("m","author")));
        var state = new ScholarSyncState("author","RUNNING",Instant.now().minusSeconds(10800),null,null,"old",null,List.of(),null);
        when(store.states(anyCollection())).thenReturn(java.util.Map.of("author",state));
        assertEquals("RETRY_REQUIRED",service.status().members().getFirst().status());
    }
    @Test void queueRejectionIsRecordedAndRetainsPreviousData() {
        properties.setApiKey("test-only");when(store.targets()).thenReturn(List.of(new ScholarSnapshotStore.Target("m","author")));
        when(store.begin(eq("author"),eq(true),eq(true),any())).thenReturn(running());
        var unavailable = new ScholarSyncService(store,provider,properties,job->{throw new java.util.concurrent.RejectedExecutionException();});
        unavailable.syncNow("author");verify(store).finish(eq("author"),eq("run"),isNull(),eq("QUEUE_UNAVAILABLE"),any());verifyNoInteractions(provider);
    }
    @Test void unexpectedErrorNeverPublishesRawExceptionOrApiKey() {
        properties.setApiKey("test-only"); when(store.targets()).thenReturn(List.of(new ScholarSnapshotStore.Target("m","author")));
        when(store.begin(eq("author"),eq(true),eq(true),any())).thenReturn(running());
        when(provider.fetch("author")).thenThrow(new IllegalStateException("secret-url-and-key"));
        service.syncNow("author"); verify(store).finish(eq("author"),eq("run"),isNull(),eq("SYNC_FAILED"),any());
    }
}
