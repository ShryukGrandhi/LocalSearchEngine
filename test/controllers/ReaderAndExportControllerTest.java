package controllers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import models.CaseInsensitivePhraseStrategy;
import models.Document;
import models.FileDocumentRepository;
import models.Match;
import models.OpenedDocument;
import models.SearchIndex;
import models.StorageException;

/** Use cases #4 (read) and #5 (download). */
class ReaderAndExportControllerTest {

    @TempDir
    Path home;

    @TempDir
    Path downloads;

    private LibraryController library;
    private ReaderController reader;
    private final ExportController exporter = new ExportController();
    private Document algorithms;

    @BeforeEach
    void setUp() {
        FileDocumentRepository repository = new FileDocumentRepository(home);
        SearchIndex index = new SearchIndex();
        library = new LibraryController(repository, index);
        reader = new ReaderController(repository, index, new CaseInsensitivePhraseStrategy());
        algorithms = library.importFile(LibraryControllerTest.DATA.resolve("algorithms.txt"), "Algorithms");
    }

    @Test
    void openFindsEveryMatch() {
        OpenedDocument opened = reader.open(algorithms.id(), "computer algorithm");
        assertEquals(3, opened.matches().size());
        for (Match match : opened.matches()) {
            String found = opened.text().original().substring(match.start(), match.end());
            assertEquals("computer algorithm", found.toLowerCase());
        }
    }

    @Test
    void openWithBlankQueryHasNoMatches() {
        assertTrue(reader.open(algorithms.id(), "").matches().isEmpty());
    }

    @Test
    void openFallsBackToDiskWhenNotIndexed() {
        FileDocumentRepository repository = new FileDocumentRepository(home);
        ReaderController coldReader = new ReaderController(repository, new SearchIndex(),
                new CaseInsensitivePhraseStrategy());
        assertEquals(3, coldReader.open(algorithms.id(), "computer algorithm").matches().size());
    }

    @Test
    void openingDeletedDocumentFails() {
        library.delete(List.of(algorithms.id()));
        assertThrows(StorageException.class, () -> reader.open(algorithms.id(), "x"));
    }

    @Test
    void matchOffsetsSurviveCrlfDisplayConversion() {
        Document crlf = library.importFile(LibraryControllerTest.DATA.resolve("crlf.txt"), "CRLF");
        OpenedDocument opened = reader.open(crlf.id(), "computer algorithm");
        Match match = opened.matches().get(0);
        String display = OpenedDocument.displayText(opened.text().original());
        assertEquals("computer algorithm", display.substring(match.start(), match.end()));
    }

    @Test
    void exportWritesExactOriginalBytes() throws IOException {
        Path source = LibraryControllerTest.DATA.resolve("crlf.txt");
        Document crlf = library.importFile(source, "CRLF");
        Path target = downloads.resolve("out.txt");
        exporter.export(reader.open(crlf.id(), "").text().original(), target);
        assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(target));
    }

    @Test
    void exportOverwritesExistingFile() throws IOException {
        Path target = Files.writeString(downloads.resolve("out.txt"), "old content that is longer");
        exporter.export("new", target);
        assertEquals("new", Files.readString(target));
    }

    @Test
    void exportToMissingFolderOrToAFolderFails() {
        assertThrows(StorageException.class, () -> exporter.export("x", downloads.resolve("no/such/dir/a.txt")));
        assertThrows(StorageException.class, () -> exporter.export("x", downloads));
    }

    @Test
    void suggestedFileNamesAreSafe() {
        assertEquals("a_b_c.txt", ExportController.suggestFileName("a/b:c"));
        assertEquals("notes.txt", ExportController.suggestFileName("notes.txt"));
        assertEquals("document.txt", ExportController.suggestFileName("///"));
    }
}
