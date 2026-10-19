package models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** AsciiText and TitlePolicy: the input rules of use case #1. */
class PoliciesTest {

    @Test
    void asciiDecodesPlainText() {
        assertEquals("Hello\r\n\tWorld~", AsciiText.decode("Hello\r\n\tWorld~".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void asciiRejectsNonAsciiByteAndReportsOffset() {
        byte[] bytes = "café".getBytes(StandardCharsets.UTF_8);
        InvalidDocumentException e = assertThrows(InvalidDocumentException.class, () -> AsciiText.decode(bytes));
        assertTrue(e.getMessage().contains("offset 3"), e.getMessage());
    }

    @Test
    void asciiRejectsNulBytesOfBinaryFiles() {
        assertThrows(InvalidDocumentException.class, () -> AsciiText.decode(new byte[] {'a', 0, 'b'}));
    }

    @Test
    void titleIsStripped() {
        assertEquals("My Notes", TitlePolicy.validate("  My Notes  "));
    }

    @Test
    void titleRejectsBlankTooLongAndControlCharacters() {
        assertThrows(InvalidDocumentException.class, () -> TitlePolicy.validate(" "));
        assertThrows(InvalidDocumentException.class, () -> TitlePolicy.validate(null));
        assertThrows(InvalidDocumentException.class,
                () -> TitlePolicy.validate("x".repeat(TitlePolicy.MAX_LENGTH + 1)));
        assertThrows(InvalidDocumentException.class, () -> TitlePolicy.validate("bad\ttitle"));
    }

    @Test
    void titleKeyIgnoresCaseAndOuterWhitespace() {
        assertEquals(TitlePolicy.key(" Notes "), TitlePolicy.key("NOTES"));
    }
}
