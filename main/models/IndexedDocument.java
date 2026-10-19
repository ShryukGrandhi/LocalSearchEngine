package models;

import java.util.Objects;

/** A document together with its searchable text, as held by the {@link SearchIndex}. */
public record IndexedDocument(Document document, IndexedText text) {

    public IndexedDocument {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(text, "text");
    }
}
