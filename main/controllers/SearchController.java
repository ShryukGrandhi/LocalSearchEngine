package controllers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

import models.Document;
import models.IndexedDocument;
import models.Match;
import models.MatchStrategy;
import models.Query;
import models.SearchIndex;
import models.SearchResult;
import models.Snippets;

/** Use case #2: find every document that matches a query. */
public final class SearchController {

    private static final Comparator<SearchResult> BY_TITLE =
            Comparator.comparing(SearchResult::document, Document.TITLE_ORDER);

    private final SearchIndex index;
    private final MatchStrategy strategy;

    public SearchController(SearchIndex index, MatchStrategy strategy) {
        this.index = index;
        this.strategy = strategy;
    }

    public List<SearchResult> search(String rawQuery) {
        return search(rawQuery, () -> false);
    }

    /**
     * @param isCancelled polled between documents so a newer query can abort this one
     * @return matches ordered by title, each with a snippet around its first match
     * @throws IllegalArgumentException if the query is blank
     * @throws CancellationException    if {@code isCancelled} turns true
     */
    public List<SearchResult> search(String rawQuery, BooleanSupplier isCancelled) {
        Query query = Query.parse(rawQuery);
        List<SearchResult> results = new ArrayList<>();
        for (IndexedDocument candidate : index.candidates(strategy.indexKey(query))) {
            if (isCancelled.getAsBoolean()) {
                throw new CancellationException("Search cancelled");
            }
            Optional<Match> first = strategy.findFirst(candidate.text(), query);
            first.ifPresent(match -> results.add(new SearchResult(
                    candidate.document(), match, Snippets.around(candidate.text().original(), match))));
        }
        results.sort(BY_TITLE);
        return List.copyOf(results);
    }
}
