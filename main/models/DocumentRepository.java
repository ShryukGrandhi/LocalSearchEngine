package models;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Storage abstraction for imported documents (Repository pattern). Views and
 * controllers depend on this interface, never on the file layout.
 *
 * <p>Documents are immutable: there is no update operation. Saving a title that
 * already exists replaces the old document with a brand new one (re-upload).
 */
public interface DocumentRepository {

    /** All documents, ordered by {@link Document#TITLE_ORDER}. */
    List<Document> findAll();

    Optional<Document> findById(String id);

    /** Titles are unique ignoring case and surrounding whitespace. */
    Optional<Document> findByTitle(String title);

    /**
     * Stores a read-only copy of {@code content} under {@code title}. If a
     * document with the same title exists it is replaced.
     *
     * @throws InvalidDocumentException if the title is invalid
     * @throws StorageException         if the copy cannot be written
     */
    Document save(String title, String content);

    /** @throws StorageException if the document is unknown or unreadable */
    String loadContent(String id);

    /** Removes every listed document; unknown ids are ignored. */
    void delete(Collection<String> ids);
}
