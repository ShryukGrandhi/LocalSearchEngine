package controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import models.Document;
import models.FileDocumentRepository;
import models.InvalidDocumentException;
import models.SearchIndex;

class LibraryControllerTest {

    static final Path DATA = Path.of("test", "data");

    @TempDir
    Path home;

    private FileDocumentRepository repository;
    private SearchIndex index;
    private LibraryController library;

    @BeforeEach
    void setUp() {
        repository = new FileDocumentRepository(home);
        index = new SearchIndex();
        library = new LibraryController(repository, index);
    }

    @Test
    void importStoresAndIndexesTheFile() {
        Document doc = library.importFile(DATA.resolve("algorithms.txt"), "Algorithms");
        assertEquals(List.of(doc), library.listDocuments());
        assertTrue(index.get(doc.id()).isPresent());
        assertTrue(library.titleExists("algorithms"));
    }

    @Test
    void importedCopyIsIndependentOfTheOriginal() throws IOException {
        Path original = Files.writeString(home.resolve("original.txt"), "first version");
        Document doc = library.importFile(original, "Doc");
        Files.writeString(original, "edited afterwards");
        assertEquals("first version", repository.loadContent(doc.id()));
    }

    @Test
    void reimportWithSameTitleReplacesOldDocumentEverywhere() throws IOException {
        Document first = library.importFile(DATA.resolve("algorithms.txt"), "Doc");
        Document second = library.importFile(DATA.resolve("databases.txt"), "doc");
        assertEquals(List.of(second), library.listDocuments());
        assertTrue(index.get(first.id()).isEmpty());
        assertEquals(1, index.size());
    }

    @Test
    void nonAsciiFileIsRejected() {
        assertThrows(InvalidDocumentException.class,
                () -> library.importFile(DATA.resolve("not-ascii.txt"), "Bad"));
        assertTrue(library.listDocuments().isEmpty());
    }

    @Test
    void missingFileAndFolderAreRejected() {
        assertThrows(InvalidDocumentException.class, () -> library.importFile(home.resolve("nope.txt"), "X"));
        assertThrows(InvalidDocumentException.class, () -> library.importFile(home, "X"));
    }

    @Test
    void invalidTitleIsRejectedBeforeReadingTheFile() {
        assertThrows(InvalidDocumentException.class, () -> library.importFile(home.resolve("nope.txt"), " "));
    }

    @Test
    void deleteRemovesFromRepositoryAndIndex() {
        Document a = library.importFile(DATA.resolve("algorithms.txt"), "A");
        Document b = library.importFile(DATA.resolve("databases.txt"), "B");
        library.delete(List.of(a.id()));
        assertEquals(List.of(b), library.listDocuments());
        assertTrue(index.get(a.id()).isEmpty());
        assertFalse(library.findDocument(a.id()).isPresent());
    }

    @Test
    void listenersHearImportsAndDeletesButNotEmptyDeletes() {
        AtomicInteger events = new AtomicInteger();
        Runnable listener = events::incrementAndGet;
        library.addChangeListener(listener);
        Document doc = library.importFile(DATA.resolve("algorithms.txt"), "A");
        library.delete(List.of());
        library.delete(List.of(doc.id()));
        assertEquals(2, events.get());
        library.removeChangeListener(listener);
        library.importFile(DATA.resolve("algorithms.txt"), "A");
        assertEquals(2, events.get());
    }

    @Test
    void loadIndexRestoresSearchabilityAfterRestart() {
        Document doc = library.importFile(DATA.resolve("algorithms.txt"), "A");
        SearchIndex freshIndex = new SearchIndex();
        LibraryController restarted = new LibraryController(new FileDocumentRepository(home), freshIndex);
        assertTrue(restarted.loadIndex().isEmpty());
        assertTrue(freshIndex.get(doc.id()).isPresent());
    }

    @Test
    void loadIndexReportsUnreadableDocuments() throws IOException {
        Document doc = library.importFile(DATA.resolve("algorithms.txt"), "Broken");
        FileDocumentRepository reopened = new FileDocumentRepository(home);
        Path copy = home.resolve("docs").resolve(doc.id() + ".txt");
        copy.toFile().setWritable(true);
        Files.delete(copy);
        assertEquals(List.of("Broken"), new LibraryController(reopened, new SearchIndex()).loadIndex());
    }
}
