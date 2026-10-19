package models;

/** Builds the short excerpts shown in the main table and the search results. */
public final class Snippets {

    /** Use case #3: the main table shows the first (up to) 500 characters. */
    public static final int PREVIEW_LENGTH = 500;

    /** Use case #2: up to 40 characters before and after the first match. */
    public static final int CONTEXT_LENGTH = 40;

    private Snippets() {
    }

    public static String preview(String content) {
        return flatten(content.substring(0, Math.min(PREVIEW_LENGTH, content.length())));
    }

    public static Snippet around(String content, Match match) {
        int from = Math.max(0, match.start() - CONTEXT_LENGTH);
        int to = Math.min(content.length(), match.end() + CONTEXT_LENGTH);
        return new Snippet(flatten(content.substring(from, to)), match.start() - from, match.length());
    }

    /** Line breaks and tabs become spaces so a snippet fits on one table row. Length is preserved. */
    static String flatten(String text) {
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == '\r' || chars[i] == '\n' || chars[i] == '\t') {
                chars[i] = ' ';
            }
        }
        return new String(chars);
    }
}
