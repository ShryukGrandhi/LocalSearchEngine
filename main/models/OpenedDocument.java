package models;

import java.util.List;
import java.util.Objects;

/** A document opened for reading (use case #4): its text and the matches of the current query. */
public record OpenedDocument(Document document, IndexedText text, List<Match> matches) {

    public OpenedDocument {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(text, "text");
        matches = List.copyOf(matches);
    }

    /**
     * Text prepared for a Swing text component, which treats '\r' as a visible
     * character. CRLF becomes " \n" and a lone CR becomes "\n", so the length
     * (and therefore every match offset) is unchanged.
     */
    public static String displayText(String content) {
        char[] chars = content.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == '\r') {
                boolean crlf = i + 1 < chars.length && chars[i + 1] == '\n';
                chars[i] = crlf ? ' ' : '\n';
            }
        }
        return new String(chars);
    }
}
