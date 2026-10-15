package com.web.labportalbackend.admin.website.scholar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SerpApiScholarClientTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private ScholarSyncProperties config() { var p = new ScholarSyncProperties(); p.setApiKey("test-only"); return p; }
    private JsonNode page(String articles, String next, boolean metrics) throws Exception {
        return mapper.readTree("{\"search_metadata\":{\"status\":\"Success\",\"processed_at\":\"2026-10-15 01:02:03 UTC\"},\"articles\":["
                + articles + "],\"serpapi_pagination\":{\"next\":\"" + next + "\"}"
                + (metrics ? ",\"cited_by\":{\"table\":[{\"citations\":{\"all\":123,\"since_2021\":99}},{\"h_index\":{\"all\":7,\"since_2021\":6}},{\"i10_index\":{\"all\":5,\"since_2021\":4}}]}" : "") + "}");
    }
    private String article(int i) { return "{\"title\":\"Paper " + i + "\",\"citation_id\":\"author:" + i
            + "\",\"link\":\"https://scholar.google.com/citations?citation_for_view=author:" + i + "\",\"year\":\"2026\",\"authors\":\"A, B\",\"publication\":\"Journal\",\"cited_by\":{\"value\":0}}"; }
    private SerpApiScholarClient provider(ScholarSyncProperties p, List<JsonNode> pages, List<Integer> offsets) {
        return new SerpApiScholarClient(p, mapper) {
            @Override protected JsonNode requestPage(String id, int offset, String parameter) {
                offsets.add(offset); return pages.get(offsets.size() - 1);
            }
        };
    }
    @Test void extractsOnlyValidGoogleScholarAuthorIdentifiers() {
        assertEquals("KfjB5lEAAAAJ", ScholarAuthorId.fromUrl("https://scholar.google.com/citations?hl=vi&user=KfjB5lEAAAAJ").orElseThrow());
        assertTrue(ScholarAuthorId.fromUrl("https://attacker.example/citations?user=abc").isEmpty());
        assertTrue(ScholarAuthorId.fromUrl("https://scholar.google.com.attacker.example/citations?user=abc").isEmpty());
        assertTrue(ScholarAuthorId.fromUrl("https://scholar.google.com/citations?user=a&user=b").isEmpty());
        assertTrue(ScholarAuthorId.fromUrl("https://scholar.google.com/citations?user=a%26api_key%3Db").isEmpty());
    }
    @Test void fetchesMoreThan100AndDeduplicatesOverlappingPagesWithProviderOffsets() throws Exception {
        List<String> rows = new ArrayList<>(); for (int i = 0; i < 100; i++) rows.add(article(i));
        var pages = List.of(page(String.join(",", rows), "https://serpapi.com/search.json?cstart=100", true),
                page(article(99).replace("\"citation_id\":\"author:99\",", "") + "," + article(100), "", false));
        List<Integer> offsets = new ArrayList<>();
        var snapshot = provider(config(), pages, offsets).fetch("author");
        assertEquals(101, snapshot.publications().size());
        assertEquals(List.of(0, 100), offsets);
        assertEquals(0, snapshot.publications().getFirst().citations());
        assertEquals(123, snapshot.metrics().citations()); assertEquals(2021, snapshot.metrics().sinceYear());
        assertEquals(6, snapshot.metrics().recentHIndex());
        assertEquals("2026-10-15T01:02:03Z", snapshot.sourceUpdatedAt().toString());
    }
    @Test void fullPageWithoutNextStillFetchesNextPageInsteadOfTruncatingAt100() throws Exception {
        List<String> rows = new ArrayList<>(); for (int i = 0; i < 100; i++) rows.add(article(i));
        List<Integer> offsets = new ArrayList<>();
        var result = provider(config(), List.of(page(String.join(",", rows), "", true), page("", "", false)), offsets).fetch("author");
        assertEquals(100, result.publications().size()); assertEquals(List.of(0, 100), offsets);
    }
    @Test void missingSourceValuesRemainUnknownRatherThanInventingZero() throws Exception {
        var result = provider(config(), List.of(page("{\"title\":\"Unknown\",\"link\":\"https://example.org/p\"}", "", false)), new ArrayList<>()).fetch("author");
        assertNull(result.metrics()); assertNull(result.publications().getFirst().year());
        assertNull(result.publications().getFirst().citations()); assertEquals("", result.publications().getFirst().authors());
    }
    @Test void rejectsPartialResultsWhenLimitReachedOrLaterPageFails() throws Exception {
        var p = config(); p.setMaxPages(1);
        var pages = List.of(page(article(0), "https://serpapi.com/search.json?start=1", true));
        assertEquals("PAGE_LIMIT_REACHED_RETAINED", assertThrows(ScholarSyncException.class,
                () -> provider(p, pages, new ArrayList<>()).fetch("author")).getMessage());
        var error = mapper.readTree("{\"error\":\"provider secret must not escape\"}");
        assertEquals("PROVIDER_SEARCH_FAILED", assertThrows(ScholarSyncException.class, () -> provider(config(),
                List.of(pages.getFirst(), error), new ArrayList<>()).fetch("author")).getMessage());
    }
    @Test void rejectsStalledPaginationAndEmptyOrMalformedProfiles() throws Exception {
        var repeated = page(article(1), "https://serpapi.com/search.json?start=100", true);
        assertThrows(ScholarSyncException.class, () -> provider(config(), List.of(repeated, repeated), new ArrayList<>()).fetch("author"));
        var empty = page("", "", true);
        assertEquals("EMPTY_RESULT_RETAINED", assertThrows(ScholarSyncException.class,
                () -> provider(config(), List.of(empty), new ArrayList<>()).fetch("author")).getMessage());
        var invalid = page("{\"title\":\"Bad\",\"link\":\"javascript:alert(1)\"}", "", false);
        assertThrows(ScholarSyncException.class, () -> provider(config(), List.of(invalid), new ArrayList<>()).fetch("author"));
    }
    @Test void readsRecentMetricsEvenWhenCitationTotalIsMissing() throws Exception {
        var table = mapper.readTree("[{\"h_index\":{\"all\":7,\"since_2021\":6}}]");
        var metrics = SerpApiScholarClient.metrics(table);
        assertNull(metrics.citations()); assertEquals(2021, metrics.sinceYear()); assertEquals(6, metrics.recentHIndex());
    }
    @Test void absentKeyDoesNotCallProvider() {
        var p = new ScholarSyncProperties(); List<Integer> calls = new ArrayList<>();
        assertEquals("API_KEY_MISSING", assertThrows(ScholarSyncException.class,
                () -> provider(p, List.of(), calls).fetch("author")).getMessage()); assertTrue(calls.isEmpty());
    }
}
