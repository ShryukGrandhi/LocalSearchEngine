package models;

import java.util.Objects;

/**
 * A search phrase such as {@code "computer algorithm"}. Leading and trailing
 * whitespace is dropped; whitespace between terms is kept exactly, because the
 * spec says the spacing in the middle must match.
 */
public record Query(String text) {

    public Query {
        Objects.requireNonNull(text, "text");
        if (text.isBlank()) {
            throw new IllegalArgumentException("Query must not be empty.");
        }
    }

    public static Query parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Query must not be empty.");
        }
        return new Query(raw.strip());
    }
}
