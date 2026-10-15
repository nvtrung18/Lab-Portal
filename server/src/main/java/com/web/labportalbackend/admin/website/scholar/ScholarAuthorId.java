package com.web.labportalbackend.admin.website.scholar;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class ScholarAuthorId {
    private ScholarAuthorId() {}

    public static Optional<String> fromUrl(String value) {
        if (value == null) return Optional.empty();
        try {
            URI uri = URI.create(value.trim());
            // URLs are identifiers only: never fetch a URL supplied by the CMS.
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) || uri.getUserInfo() != null
                    || !"scholar.google.com".equalsIgnoreCase(uri.getHost())
                    || !"/citations".equals(uri.getPath()) || uri.getRawQuery() == null) return Optional.empty();
            String found = null;
            for (String part : uri.getRawQuery().split("&")) {
                String[] pair = part.split("=", 2);
                if ("user".equals(pair[0]) && pair.length == 2) {
                    if (found != null) return Optional.empty();
                    found = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                }
            }
            return found != null && found.matches("[A-Za-z0-9_-]{1,64}") ? Optional.of(found) : Optional.empty();
        } catch (IllegalArgumentException ex) { return Optional.empty(); }
    }
}
