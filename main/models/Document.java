package models;

import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;

/**
 * Immutable metadata for one imported document. The full text lives in the
 * {@link DocumentRepository}; only a short preview is kept here so the main
 * table can be rendered without touching the disk.
 */
public record Document(String id, String title, long sizeBytes, Instant importedAt, String preview) {

    /** Case-insensitive title order used everywhere documents are listed. */
    public static final Comparator<Document> TITLE_ORDER =
            Comparator.comparing(Document::title, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Document::title);

    public Document {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(importedAt, "importedAt");
        Objects.requireNonNull(preview, "preview");
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must be >= 0");
        }
    }
}
