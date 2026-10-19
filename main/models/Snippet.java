package models;

import java.util.Objects;

/** A short excerpt of a document with the matched term marked by offsets. */
public record Snippet(String text, int matchOffset, int matchLength) {

    public Snippet {
        Objects.requireNonNull(text, "text");
        if (matchOffset < 0 || matchLength < 0 || matchOffset + matchLength > text.length()) {
            throw new IllegalArgumentException("Match lies outside the snippet");
        }
    }

    public String before() {
        return text.substring(0, matchOffset);
    }

    public String matched() {
        return text.substring(matchOffset, matchOffset + matchLength);
    }

    public String after() {
        return text.substring(matchOffset + matchLength);
    }
}
