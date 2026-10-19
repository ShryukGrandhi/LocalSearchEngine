package models;

import java.util.List;
import java.util.Optional;

/**
 * Immutable position within a document's matches. {@link #next()} and
 * {@link #previous()} wrap around (use case #4: "in a circular way").
 */
public record MatchCursor(List<Match> matches, int index) {

    public MatchCursor {
        matches = List.copyOf(matches);
        boolean valid = matches.isEmpty() ? index == -1 : index >= 0 && index < matches.size();
        if (!valid) {
            throw new IllegalArgumentException("index " + index + " out of range for " + matches.size() + " matches");
        }
    }

    public static MatchCursor of(List<Match> matches) {
        return new MatchCursor(matches, matches.isEmpty() ? -1 : 0);
    }

    public boolean isEmpty() {
        return matches.isEmpty();
    }

    public int count() {
        return matches.size();
    }

    /** 1-based position for display, 0 when there are no matches. */
    public int position() {
        return index + 1;
    }

    public Optional<Match> current() {
        return isEmpty() ? Optional.empty() : Optional.of(matches.get(index));
    }

    public MatchCursor next() {
        return isEmpty() ? this : new MatchCursor(matches, (index + 1) % matches.size());
    }

    public MatchCursor previous() {
        return isEmpty() ? this : new MatchCursor(matches, (index - 1 + matches.size()) % matches.size());
    }
}
