package models;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SnippetsTest {

    @Test
    void previewKeepsAtMost500Characters() {
        String content = "x".repeat(800);
        assertEquals(500, Snippets.preview(content).length());
    }

    @Test
    void previewOfShortDocumentIsWholeDocumentOnOneLine() {
        assertEquals("line one line two", Snippets.preview("line one\nline two"));
    }

    @Test
    void aroundTakes40CharactersEachSide() {
        String content = "a".repeat(100) + "NEEDLE" + "b".repeat(100);
        Snippet snippet = Snippets.around(content, new Match(100, 6));
        assertEquals("a".repeat(40), snippet.before());
        assertEquals("NEEDLE", snippet.matched());
        assertEquals("b".repeat(40), snippet.after());
    }

    @Test
    void aroundIsClippedAtDocumentEdges() {
        Snippet snippet = Snippets.around("abNEEDLEcd", new Match(2, 6));
        assertEquals("ab", snippet.before());
        assertEquals("cd", snippet.after());
    }

    @Test
    void aroundFlattensLineBreaks() {
        Snippet snippet = Snippets.around("one\r\ntwo\tNEEDLE", new Match(9, 6));
        assertEquals("one  two ", snippet.before());
    }
}
