package models;

import java.nio.charset.StandardCharsets;

/** Decodes raw bytes as plain ASCII text, the only file format the spec accepts. */
public final class AsciiText {

    private AsciiText() {
    }

    /**
     * @throws InvalidDocumentException on any byte outside 0x01..0x7F. A NUL byte
     *                                  is rejected too because it signals a binary file.
     */
    public static String decode(byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            if (value == 0 || value > 0x7F) {
                throw new InvalidDocumentException(String.format(
                        "This file is not plain ASCII text (byte 0x%02X at offset %d).", value, i));
            }
        }
        return new String(bytes, StandardCharsets.US_ASCII);
    }
}
