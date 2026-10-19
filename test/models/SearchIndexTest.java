package models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SearchIndexTest {

    private SearchIndex index;

    @BeforeEach
    void setUp() {
        index = new SearchIndex();
        index.put(doc("a"), "The Computer Algorithm");
        index.put(doc("b"), "relational databases");
        index.put(doc("c"), "computing machinery");
    }

    @Test
    void candidatesAreNarrowedByTrigrams() {
        assertEquals(Set.of("a"), ids(index.candidates("computer")));
        assertEquals(Set.of("a", "c"), ids(index.candidates("comput")));
    }

    @Test
    void unknownTrigramGivesNoCandidates() {
        assertTrue(index.candidates("zzz").isEmpty());
    }

    @Test
    void shortKeysReturnEveryDocument() {
        assertEquals(Set.of("a", "b", "c"), ids(index.candidates("co")));
    }

    @Test
    void candidatesMayBeSupersetButNeverMissAMatch() {
        // "abcab" contains every trigram of "cabc" (cab, abc) without containing "cabc"
        index.put(doc("d"), "abcab");
        assertTrue(ids(index.candidates("cabc")).contains("d"));
    }

    @Test
    void removeDropsDocumentAndItsPostings() {
        index.remove("a");
        assertTrue(index.candidates("computer").isEmpty());
        assertTrue(index.get("a").isEmpty());
        assertEquals(2, index.size());
    }

    @Test
    void putReplacesExistingDocument() {
        index.put(doc("a"), "now about databases");
        assertTrue(index.candidates("computer").isEmpty());
        assertEquals(Set.of("a", "b"), ids(index.candidates("databases")));
    }

    @Test
    void getReturnsOriginalAndFoldedText() {
        IndexedText text = index.get("a").orElseThrow().text();
        assertEquals("The Computer Algorithm", text.original());
        assertEquals("the computer algorithm", text.folded());
    }

    @Test
    void gramsAreDistinct() {
        assertEquals(1, SearchIndex.gramsOf("aaaaaa").length);
        assertEquals(0, SearchIndex.gramsOf("ab").length);
    }

    private static Set<String> ids(List<IndexedDocument> docs) {
        return docs.stream().map(d -> d.document().id()).collect(Collectors.toSet());
    }

    private static Document doc(String id) {
        return new Document(id, "title-" + id, 1, Instant.EPOCH, "");
    }
}
