package models;

import java.util.Objects;

/** One row of the search results table: a matching document and its first match. */
public record SearchResult(Document document, Match firstMatch, Snippet snippet) {

    public SearchResult {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(firstMatch, "firstMatch");
        Objects.requireNonNull(snippet, "snippet");
    }
}
