package controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CancellationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import models.CaseInsensitivePhraseStrategy;
import models.Document;
import models.FileDocumentRepository;
import models.SearchIndex;
import models.SearchResult;

class SearchControllerTest {

    @TempDir
    Path home;

    private SearchIndex index;
    private SearchController search;

    @BeforeEach
    void setUp() {
        index = new SearchIndex();
        LibraryController library = new LibraryController(new FileDocumentRepository(home), index);
        library.importFile(LibraryControllerTest.DATA.resolve("networking.txt"), "Networking");
        library.importFile(LibraryControllerTest.DATA.resolve("algorithms.txt"), "Algorithms");
        library.importFile(LibraryControllerTest.DATA.resolve("databases.txt"), "Databases");
        search = new SearchController(index, new CaseInsensitivePhraseStrategy());
    }

    @Test
    void returnsOnlyDocumentsContainingThePhraseOrderedByTitle() {
        List<SearchResult> results = search.search("COMPUTER ALGORITHM");
        assertEquals(List.of("Algorithms"), titles(results));
    }

    @Test
    void underscoresAndDoubleSpacesDoNotMatch() {
        // networking.txt contains "Computer_Algorithm" and "Computer  Algorithm" only
        assertTrue(titles(search.search("computer algorithm")).stream().noneMatch("Networking"::equals));
    }

    @Test
    void resultsAreSortedByTitle() {
        assertEquals(List.of("Algorithms", "Databases", "Networking"), titles(search.search("and")));
    }

    @Test
    void snippetShowsFirstMatchWithContext() {
        SearchResult result = search.search("computer algorithm").get(0);
        assertEquals("computer algorithm", result.snippet().matched());
        assertTrue(result.snippet().before().length() <= 40);
        assertTrue(result.snippet().after().length() <= 40);
        assertTrue(result.snippet().before().endsWith("A "), result.snippet().before());
    }

    @Test
    void noMatchesGivesEmptyList() {
        assertTrue(search.search("quantum chromodynamics").isEmpty());
    }

    @Test
    void blankQueryIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> search.search("  "));
    }

    @Test
    void cancelledSearchStops() {
        assertThrows(CancellationException.class, () -> search.search("the", () -> true));
    }

    @Test
    void searchingLargeCorpusIsFast() {
        SearchIndex big = new SearchIndex();
        String filler = "lorem ipsum dolor sit amet consectetur adipiscing elit ".repeat(20_000); // ~1 MB
        for (int i = 0; i < 30; i++) {
            String content = i == 17 ? filler + " the needle phrase " + filler : filler;
            big.put(new Document("id" + i, "doc " + i, content.length(), Instant.EPOCH, ""), content);
        }
        SearchController bigSearch = new SearchController(big, new CaseInsensitivePhraseStrategy());
        bigSearch.search("needle phrase"); // warm up the JIT

        long start = System.nanoTime();
        List<SearchResult> results = bigSearch.search("Needle Phrase");
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertEquals(List.of("doc 17"), titles(results));
        assertTrue(millis < 500, "search over ~33 MB took " + millis + " ms");
    }

    private static List<String> titles(List<SearchResult> results) {
        return results.stream().map(r -> r.document().title()).toList();
    }
}
