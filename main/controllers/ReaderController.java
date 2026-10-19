package controllers;

import java.util.List;

import models.Document;
import models.DocumentRepository;
import models.IndexedDocument;
import models.IndexedText;
import models.Match;
import models.MatchStrategy;
import models.OpenedDocument;
import models.Query;
import models.SearchIndex;
import models.StorageException;

/** Use case #4: open a document and locate every match of a query in it. */
public final class ReaderController {

    private final DocumentRepository repository;
    private final SearchIndex index;
    private final MatchStrategy strategy;

    public ReaderController(DocumentRepository repository, SearchIndex index, MatchStrategy strategy) {
        this.repository = repository;
        this.index = index;
        this.strategy = strategy;
    }

    /**
     * @param rawQuery may be blank, in which case the document opens with no matches
     * @throws StorageException if the document was deleted or cannot be read
     */
    public OpenedDocument open(String documentId, String rawQuery) {
        Document document = repository.findById(documentId)
                .orElseThrow(() -> new StorageException("This document is no longer in the library."));
        IndexedText text = index.get(documentId)
                .map(IndexedDocument::text)
                .orElseGet(() -> IndexedText.of(repository.loadContent(documentId)));
        return new OpenedDocument(document, text, find(text, rawQuery));
    }

    /** All matches of {@code rawQuery} in an already opened text; empty for a blank query. */
    public List<Match> find(IndexedText text, String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return List.of();
        }
        return strategy.findAll(text, Query.parse(rawQuery));
    }
}
