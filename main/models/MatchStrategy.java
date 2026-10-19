package models;

import java.util.List;
import java.util.Optional;

/**
 * Defines what "a document matches a query" means (Strategy pattern). The
 * matching rules are the part of the spec most likely to change, so they live
 * behind this interface and can be swapped without touching the index or the UI.
 */
public interface MatchStrategy {

    /**
     * A folded substring that every match is guaranteed to contain. The
     * {@link SearchIndex} uses it to skip documents that cannot match.
     */
    String indexKey(Query query);

    Optional<Match> findFirst(IndexedText text, Query query);

    /** All non-overlapping matches, in document order. */
    List<Match> findAll(IndexedText text, Query query);
}
