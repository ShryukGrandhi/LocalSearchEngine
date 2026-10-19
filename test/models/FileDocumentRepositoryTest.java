package models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileDocumentRepositoryTest {

    @TempDir
    Path root;

    @Test
    void savedDocumentCanBeLoadedBack() {
        FileDocumentRepository repo = new FileDocumentRepository(root);
        Document doc = repo.save("Notes", "hello world");
        assertEquals("hello world", repo.loadContent(doc.id()));
        assertEquals(11, doc.sizeBytes());
        assertEquals("hello world", doc.preview());
    }

    @Test
    void findAllIsOrderedByTitleIgnoringCase() {
        FileDocumentRepository repo = new FileDocumentRepository(root);
        repo.save("charlie", "c");
        repo.save("Alpha", "a");
        repo.save("beta", "b");
        assertEquals(List.of("Alpha", "beta", "charlie"), repo.findAll().stream().map(Document::title).toList());
    }

    @Test
    void savingSameTitleReplacesTheDocument() {
        FileDocumentRepository repo = new FileDocumentRepository(root);
        Document first = repo.save("Notes", "version one");
        Document second = repo.save("NOTES", "version two");
        assertNotEquals(first.id(), second.id());
        assertEquals(1, repo.findAll().size());
        assertEquals("version two", repo.loadContent(second.id()));
        assertThrows(StorageException.class, () -> repo.loadContent(first.id()));
    }

    @Test
    void storedCopyIsReadOnly() throws IOException {
        FileDocumentRepository repo = new FileDocumentRepository(root);
        Document doc = repo.save("Notes", "content");
        Path copy = root.resolve("docs").resolve(doc.id() + ".txt");
        assertFalse(Files.isWritable(copy));
    }

    @Test
    void libraryPersistsAcrossInstances() {
        Document doc = new FileDocumentRepository(root).save("Persisted: \"title\" = yes", "text");
        FileDocumentRepository reopened = new FileDocumentRepository(root);
        Document loaded = reopened.findById(doc.id()).orElseThrow();
        assertEquals(doc.title(), loaded.title());
        assertEquals(doc.importedAt(), loaded.importedAt());
        assertEquals("text", reopened.loadContent(doc.id()));
    }

    @Test
    void deleteRemovesDocumentsAndTheirFiles() throws IOException {
        FileDocumentRepository repo = new FileDocumentRepository(root);
        Document a = repo.save("a", "1");
        Document b = repo.save("b", "2");
        repo.delete(List.of(a.id(), "unknown-id"));
        assertEquals(List.of(b), repo.findAll());
        try (Stream<Path> files = Files.list(root.resolve("docs"))) {
            assertEquals(1, files.count());
        }
        assertTrue(new FileDocumentRepository(root).findById(a.id()).isEmpty());
    }

    @Test
    void deletingNothingIsANoOp() {
        FileDocumentRepository repo = new FileDocumentRepository(root);
        repo.delete(List.of("missing"));
        assertTrue(repo.findAll().isEmpty());
    }

    @Test
    void orphanedCatalogEntryIsSkipped() throws IOException {
        Document doc = new FileDocumentRepository(root).save("gone", "x");
        Path copy = root.resolve("docs").resolve(doc.id() + ".txt");
        copy.toFile().setWritable(true);
        Files.delete(copy);
        assertTrue(new FileDocumentRepository(root).findAll().isEmpty());
    }

    @Test
    void temporarilyMissingCopyKeepsItsCatalogEntry() throws IOException {
        Document doc = new FileDocumentRepository(root).save("flaky", "content");
        Path copy = root.resolve("docs").resolve(doc.id() + ".txt");
        Path parked = root.resolve("parked.txt");
        copy.toFile().setWritable(true);
        Files.move(copy, parked);

        FileDocumentRepository whileMissing = new FileDocumentRepository(root);
        assertTrue(whileMissing.findAll().isEmpty());
        whileMissing.save("other", "x"); // rewrites the catalog

        Files.move(parked, copy);
        assertEquals("flaky", new FileDocumentRepository(root).findById(doc.id()).orElseThrow().title());
    }

    @Test
    void corruptCatalogIsReportedAsStorageError() throws IOException {
        Files.writeString(root.resolve("catalog.properties"), "x.title=\\uZZZZ");
        assertThrows(StorageException.class, () -> new FileDocumentRepository(root));
    }

    @Test
    void invalidTitleIsRejected() {
        FileDocumentRepository repo = new FileDocumentRepository(root);
        assertThrows(InvalidDocumentException.class, () -> repo.save("  ", "x"));
    }

    @Test
    void unusableRootIsReported() throws IOException {
        Path file = Files.writeString(root.resolve("not-a-folder"), "x");
        assertThrows(StorageException.class, () -> new FileDocumentRepository(file));
    }
}
