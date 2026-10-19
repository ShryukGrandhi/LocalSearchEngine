package models;

import java.util.Locale;

/** Rules for document titles. Each title maps to exactly one document. */
public final class TitlePolicy {

    public static final int MAX_LENGTH = 200;

    private TitlePolicy() {
    }

    /**
     * @return the title with surrounding whitespace removed
     * @throws InvalidDocumentException if the title is empty, too long or has control characters
     */
    public static String validate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidDocumentException("Title must not be empty.");
        }
        String title = raw.strip();
        if (title.length() > MAX_LENGTH) {
            throw new InvalidDocumentException("Title must be at most " + MAX_LENGTH + " characters.");
        }
        for (int i = 0; i < title.length(); i++) {
            if (Character.isISOControl(title.charAt(i))) {
                throw new InvalidDocumentException("Title must not contain control characters.");
            }
        }
        return title;
    }

    /** Identity key: two titles with the same key are the same document. */
    public static String key(String title) {
        return title.strip().toLowerCase(Locale.ROOT);
    }
}
