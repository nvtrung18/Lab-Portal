package com.web.labportalbackend.ai.context;

import java.util.List;

/** Immutable list metadata; omitted count is deliberately not guessed. */
public record AiBoundedList<T>(List<T> values, int returnedCount, int limit, boolean truncated) {
    public AiBoundedList {
        values = List.copyOf(values == null ? List.of() : values);
        if (limit <= 0 || returnedCount != values.size() || returnedCount > limit) {
            throw new IllegalArgumentException("invalid bounded list metadata");
        }
    }

    public static <T> AiBoundedList<T> fromOverfetch(List<T> rows, int limit) {
        List<T> supplied = rows == null ? List.of() : rows;
        boolean truncated = supplied.size() > limit;
        List<T> values = truncated ? supplied.subList(0, limit) : supplied;
        return new AiBoundedList<>(values, values.size(), limit, truncated);
    }
}
