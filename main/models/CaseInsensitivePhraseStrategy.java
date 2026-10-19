package models;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The matching rule from the spec: the whole query must appear in the text,
 * ignoring case, with the exact same spacing between terms. The query may sit
 * inside a longer phrase or word ("The computer algorithm", "Computer
 * Algorithms"), but every term must be there in full, so "Computing algorithms"
 * and "computer randomized algorithms" do not match.
 */
public final class CaseInsensitivePhraseStrategy implements MatchStrategy {

    @Override
    public String indexKey(Query query) {
        return IndexedText.fold(query.text());
    }

    @Override
    public Optional<Match> findFirst(IndexedText text, Query query) {
        String needle = indexKey(query);
        int start = text.folded().indexOf(needle);
        return start < 0 ? Optional.empty() : Optional.of(new Match(start, needle.length()));
    }

    @Override
    public List<Match> findAll(IndexedText text, Query query) {
        String needle = indexKey(query);
        String haystack = text.folded();
        List<Match> matches = new ArrayList<>();
        int start = haystack.indexOf(needle);
        while (start >= 0) {
            matches.add(new Match(start, needle.length()));
            start = haystack.indexOf(needle, start + needle.length());
        }
        return List.copyOf(matches);
    }
}
