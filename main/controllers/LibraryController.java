package controllers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import models.AsciiText;
import models.Document;
import models.DocumentRepository;
import models.InvalidDocumentException;
import models.SearchIndex;
import models.StorageException;
import models.TitlePolicy;

/**
 * Use cases #1 (import) and #3 (list and delete). Keeps the repository and the
 * search index in step and tells listeners whenever the library changes.
 *
 * <p>Methods may block on disk I/O; views must call them off the Swing thread.
 */
public final class LibraryController {

    /** "Several megabytes" in the spec; leaves headroom without risking memory. */
    public static final long MAX_FILE_BYTES = 64L * 1024 * 1024;

    private final DocumentRepository repository;
    private final SearchIndex index;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public LibraryController(DocumentRepository repository, SearchIndex index) {
        this.repository = repository;
        this.index = index;
    }

    /**
     * Reads every stored document into the search index. Takes the controller
     * lock per document and re-checks it still exists, so an import or delete
     * made while the library is loading is never undone by a stale snapshot.
     *
     * @return titles that could not be loaded (empty when all succeeded)
     */
    public List<String> loadIndex() {
        List<String> failed = new ArrayList<>();
        for (Document doc : repository.findAll()) {
            synchronized (this) {
                if (repository.findById(doc.id()).isEmpty()) {
                    continue; // deleted or replaced since the snapshot
                }
                try {
                    index.put(doc, repository.loadContent(doc.id()));
                } catch (StorageException e) {
                    failed.add(doc.title());
                }
            }
        }
        fireChanged();
        return List.copyOf(failed);
    }

    public List<Document> listDocuments() {
        return repository.findAll();
    }

    public Optional<Document> findDocument(String id) {
        return repository.findById(id);
    }

    /** @throws InvalidDocumentException if the title is invalid */
    public boolean titleExists(String title) {
        return repository.findByTitle(TitlePolicy.validate(title)).isPresent();
    }

    /**
     * Imports a read-only copy of {@code source}. An existing document with the
     * same title is replaced (re-upload).
     *
     * @throws InvalidDocumentException if the title or the file is not acceptable
     * @throws StorageException         if reading or storing fails
     */
    public synchronized Document importFile(Path source, String title) {
        String validTitle = TitlePolicy.validate(title);
        String content = readAscii(source);
        Optional<Document> replaced = repository.findByTitle(validTitle);
        Document document = repository.save(validTitle, content);
        replaced.ifPresent(old -> index.remove(old.id()));
        index.put(document, content);
        fireChanged();
        return document;
    }

    public synchronized void delete(Collection<String> ids) {
        if (ids.isEmpty()) {
            return;
        }
        repository.delete(ids);
        ids.forEach(index::remove);
        fireChanged();
    }

    /** Listeners run on the calling (background) thread. */
    public void addChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    public void removeChangeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void fireChanged() {
        listeners.forEach(Runnable::run);
    }

    private static String readAscii(Path source) {
        try {
            if (!Files.isRegularFile(source)) {
                throw new InvalidDocumentException("Not a file: " + source);
            }
            long size = Files.size(source);
            if (size > MAX_FILE_BYTES) {
                throw new InvalidDocumentException(String.format(
                        "This file is %.1f MB; the limit is %d MB.",
                        size / (1024.0 * 1024.0), MAX_FILE_BYTES / (1024 * 1024)));
            }
            return AsciiText.decode(Files.readAllBytes(source));
        } catch (NoSuchFileException e) {
            throw new InvalidDocumentException("File not found: " + source);
        } catch (IOException e) {
            throw new StorageException("Could not read " + source + ": " + e.getMessage(), e);
        }
    }
}
