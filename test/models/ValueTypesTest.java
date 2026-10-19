package models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Small value types: Query, Match, IndexedText, Snippet, Document, OpenedDocument. */
class ValueTypesTest {

    @Test
    void queryStripsOuterWhitespaceButKeepsInnerSpacing() {
        assertEquals("computer  algorithm", Query.parse("  computer  algorithm \t").text());
    }

    @Test
    void queryRejectsBlankInput() {
        assertThrows(IllegalArgumentException.class, () -> Query.parse("   "));
        assertThrows(IllegalArgumentException.class, () -> Query.parse(null));
        assertThrows(IllegalArgumentException.class, () -> new Query(""));
    }

    @Test
    void matchRejectsInvalidRange() {
        assertThrows(IllegalArgumentException.class, () -> new Match(-1, 3));
        assertThrows(IllegalArgumentException.class, () -> new Match(0, 0));
        assertEquals(7, new Match(4, 3).end());
    }

    @Test
    void foldingPreservesLength() {
        IndexedText text = IndexedText.of("ABC defİ");
        assertEquals(text.original().length(), text.folded().length());
        assertEquals("abc def", text.folded().substring(0, 7));
    }

    @Test
    void indexedTextRejectsMismatchedLengths() {
        assertThrows(IllegalArgumentException.class, () -> new IndexedText("abc", "ab"));
    }

    @Test
    void snippetSplitsAroundMatch() {
        Snippet snippet = new Snippet("the needle here", 4, 6);
        assertEquals("the ", snippet.before());
        assertEquals("needle", snippet.matched());
        assertEquals(" here", snippet.after());
        assertThrows(IllegalArgumentException.class, () -> new Snippet("abc", 2, 5));
    }

    @Test
    void documentOrderIgnoresCase() {
        Document b = doc("beta");
        Document a = doc("Alpha");
        Document c = doc("charlie");
        List<Document> docs = new ArrayList<>(List.of(c, b, a));
        docs.sort(Document.TITLE_ORDER);
        assertEquals(List.of(a, b, c), docs);
    }

    @Test
    void documentRejectsNegativeSize() {
        assertThrows(IllegalArgumentException.class,
                () -> new Document("id", "t", -1, Instant.EPOCH, ""));
    }

    @Test
    void displayTextKeepsOffsetsWhileRemovingCarriageReturns() {
        String content = "a\r\nb\rc";
        String display = OpenedDocument.displayText(content);
        assertEquals(content.length(), display.length());
        assertEquals("a \nb\nc", display);
    }

    @Test
    void openedDocumentCopiesMatches() {
        List<Match> matches = new ArrayList<>(List.of(new Match(0, 1)));
        OpenedDocument opened = new OpenedDocument(doc("t"), IndexedText.of("x"), matches);
        matches.clear();
        assertEquals(1, opened.matches().size());
        assertTrue(opened.matches() != matches);
    }

    @Test
    void searchResultRequiresAllParts() {
        assertThrows(NullPointerException.class, () -> new SearchResult(doc("t"), null, null));
    }

    static Document doc(String title) {
        return new Document(title + "-id", title, 1, Instant.EPOCH, "");
    }
}
