package models;

import java.util.Objects;

/**
 * Document text paired with its case-folded twin. Folding is done one char at a
 * time, so both strings always have the same length and a match offset found in
 * {@code folded} is valid in {@code original} too.
 */
public record IndexedText(String original, String folded) {

    public IndexedText {
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(folded, "folded");
        if (original.length() != folded.length()) {
            throw new IllegalArgumentException("original and folded text must have equal length");
        }
    }

    public static IndexedText of(String original) {
        return new IndexedText(original, fold(original));
    }

    /** Length-preserving lower-casing (unlike {@link String#toLowerCase()}). */
    public static String fold(String text) {
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            chars[i] = Character.toLowerCase(chars[i]);
        }
        return new String(chars);
    }

    public int length() {
        return original.length();
    }
}
