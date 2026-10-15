package com.web.labportalbackend.admin.website.scholar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.labportalbackend.admin.website.dto.WebsiteContent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class SerpApiScholarClient implements ScholarProvider {
    private static final int PAGE_SIZE = 100;
    private final ScholarSyncProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http;

    @Autowired
    public SerpApiScholarClient(ScholarSyncProperties properties, ObjectMapper mapper) {
        this(properties, mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    SerpApiScholarClient(ScholarSyncProperties properties, ObjectMapper mapper, HttpClient http) {
        this.properties = properties; this.mapper = mapper; this.http = http;
    }

    @Override
    public ScholarSnapshot fetch(String authorId) {
        if (!properties.configured()) throw new ScholarSyncException("API_KEY_MISSING");
        if (!authorId.matches("[A-Za-z0-9_-]{1,64}")) throw new ScholarSyncException("INVALID_SCHOLAR_ID");
        if (properties.getMaxPages() < 1 || properties.getMaxPages() > 1000) throw new ScholarSyncException("INVALID_SYNC_LIMITS");
        Instant deadline = Instant.now().plus(Duration.ofHours(1));
        Map<String, WebsiteContent.ScholarPublication> articles = new LinkedHashMap<>();
        WebsiteContent.ScholarMetrics metrics = null;
        Instant sourceUpdatedAt = null;
        int offset = 0;
        String offsetParameter = "start";
        for (int page = 0; page < Math.max(1, properties.getMaxPages()); page++) {
            if (Instant.now().isAfter(deadline)) throw new ScholarSyncException("RUN_TIME_LIMIT_REACHED_RETAINED");
            JsonNode root = requestPage(authorId, offset, offsetParameter);
            if (root.hasNonNull("error") || !"Success".equals(root.path("search_metadata").path("status").asText()))
                throw new ScholarSyncException("PROVIDER_SEARCH_FAILED");
            JsonNode rows = root.path("articles");
            if (!rows.isArray()) throw new ScholarSyncException("PROVIDER_ARTICLES_MISSING");
            if (page == 0) {
                metrics = metrics(root.path("cited_by").path("table"));
                sourceUpdatedAt = providerTime(root.path("search_metadata").path("processed_at").asText(null));
            }
            int before = articles.size();
            for (JsonNode row : rows) {
                String title = row.path("title").asText("");
                String link = row.path("link").asText("");
                String citationId = row.path("citation_id").asText("");
                if (title.isBlank() || !httpUrl(link)) throw new ScholarSyncException("PROVIDER_INVALID_ARTICLE");
                // Stable Scholar article ID deduplicates overlap between pages; URL is the fallback identity.
                String key = citationId.isBlank() ? articleIdentity(link) : "citation:" + citationId;
                articles.putIfAbsent(key, new WebsiteContent.ScholarPublication(title,
                        row.path("authors").asText(""), row.path("publication").asText(""),
                        year(row.get("year")), integer(row.path("cited_by").get("value")), link));
            }
            String next = root.path("serpapi_pagination").path("next").asText("");
            if (next.isBlank() && rows.size() < PAGE_SIZE) {
                if (articles.isEmpty()) throw new ScholarSyncException("EMPTY_RESULT_RETAINED");
                return new ScholarSnapshot(metrics, java.util.List.copyOf(articles.values()), Instant.now(), sourceUpdatedAt);
            }
            if (rows.isEmpty() || articles.size() == before) throw new ScholarSyncException("PROVIDER_PAGINATION_STALLED");
            // Read only the offset from provider pagination, never follow arbitrary URLs or forward our key there.
            int nextOffset = offset + rows.size();
            offsetParameter = "start";
            if (!next.isBlank()) {
                try {
                    URI uri = URI.create(next);
                    boolean found = false;
                    for (String part : (uri.getRawQuery() == null ? "" : uri.getRawQuery()).split("&")) {
                        String[] pair = part.split("=", 2);
                        if (pair.length == 2 && (pair[0].equals("start") || pair[0].equals("cstart"))) {
                            nextOffset = Integer.parseInt(URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
                            offsetParameter = pair[0]; found = true; break;
                        }
                    }
                    if (!found || nextOffset <= offset) throw new IllegalArgumentException();
                } catch (IllegalArgumentException ex) { throw new ScholarSyncException("PROVIDER_INVALID_PAGINATION"); }
            }
            offset = nextOffset;
        }
        // Never commit the first N pages as if they were a full profile.
        throw new ScholarSyncException("PAGE_LIMIT_REACHED_RETAINED");
    }

    protected JsonNode requestPage(String authorId, int offset, String offsetParameter) {
        String key = URLEncoder.encode(properties.getApiKey(), StandardCharsets.UTF_8);
        URI uri = URI.create("https://serpapi.com/search.json?engine=google_scholar_author&hl=en&author_id="
                + authorId + "&num=" + PAGE_SIZE + "&" + offsetParameter + "=" + offset + "&api_key=" + key);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(
                Math.max(1, Math.min(120, properties.getRequestTimeoutSeconds())))).header("Accept", "application/json").GET().build();
        var responseFuture = http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
        try {
            HttpResponse<byte[]> response = responseFuture.get(Math.max(1, Math.min(120, properties.getRequestTimeoutSeconds())), java.util.concurrent.TimeUnit.SECONDS);
            if (response.statusCode() != 200) throw new ScholarSyncException("PROVIDER_HTTP_" + response.statusCode());
            if (response.body().length > 4_000_000) throw new ScholarSyncException("PROVIDER_RESPONSE_TOO_LARGE");
            return mapper.readTree(response.body());
        } catch (ScholarSyncException ex) { throw ex; }
        catch (InterruptedException ex) {
            responseFuture.cancel(true); Thread.currentThread().interrupt(); throw new ScholarSyncException("PROVIDER_INTERRUPTED");
        } catch (Exception ex) { responseFuture.cancel(true); throw new ScholarSyncException("PROVIDER_UNAVAILABLE_OR_INVALID_JSON"); }
    }

    private static Integer year(JsonNode value) {
        Integer number = integer(value); return number == null || number < 1 || number > 9999 ? null : number;
    }

    static WebsiteContent.ScholarMetrics metrics(JsonNode table) {
        if (!table.isArray()) return null;
        JsonNode citations = null, h = null, i10 = null;
        for (JsonNode row : table) {
            if (row.has("citations")) citations = row.get("citations");
            if (row.has("h_index")) h = row.get("h_index");
            if (row.has("i10_index")) i10 = row.get("i10_index");
        }
        if (citations == null && h == null && i10 == null) return null;
        java.util.Set<String> periods = new java.util.HashSet<>();
        for (JsonNode metric : new JsonNode[]{citations, h, i10}) {
            if (metric == null) continue;
            var fields = metric.fieldNames();
            while (fields.hasNext()) { String field = fields.next(); if (field.matches("since_\\d{4}")) periods.add(field); }
        }
        String since = periods.size() == 1 ? periods.iterator().next() : null;
        return new WebsiteContent.ScholarMetrics(metric(citations, "all"), metric(h, "all"), metric(i10, "all"),
                since == null ? null : Integer.parseInt(since.substring(6)),
                metric(citations, since), metric(h, since), metric(i10, since));
    }

    private static Integer metric(JsonNode node, String field) { return node == null || field == null ? null : integer(node.get(field)); }
    private static Integer integer(JsonNode value) {
        if (value == null || value.isNull()) return null;
        try { int number = Integer.parseInt(value.asText().replace(",", "")); return number >= 0 ? number : null; }
        catch (NumberFormatException ex) { return null; }
    }
    private static String articleIdentity(String link) {
        URI uri = URI.create(link);
        if ("scholar.google.com".equalsIgnoreCase(uri.getHost()) && uri.getRawQuery() != null) {
            for (String part : uri.getRawQuery().split("&")) {
                String[] pair = part.split("=", 2);
                if (pair.length == 2 && pair[0].equals("citation_for_view")) {
                    try { return "citation:" + URLDecoder.decode(pair[1], StandardCharsets.UTF_8); }
                    catch (IllegalArgumentException ex) { return "url:" + link; }
                }
            }
        }
        return "url:" + link;
    }
    private static boolean httpUrl(String value) {
        try { URI uri = URI.create(value); return ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                && uri.getHost() != null && uri.getUserInfo() == null; }
        catch (IllegalArgumentException ex) { return false; }
    }
    private static Instant providerTime(String value) {
        if (value == null) return null;
        try { return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")).toInstant(ZoneOffset.UTC); }
        catch (java.time.format.DateTimeParseException ex) { return null; }
    }
}
