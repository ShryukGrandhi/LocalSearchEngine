package controllers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import models.StorageException;

/** Use case #5: write a document's content to a path the user picked. */
public final class ExportController {

    private static final String EXTENSION = ".txt";
    private static final String FALLBACK_NAME = "document";

    /** @throws StorageException with a user-facing reason when the file cannot be written */
    public void export(String content, Path target) {
        Path absolute = target.toAbsolutePath();
        Path folder = absolute.getParent();
        if (folder == null || !Files.isDirectory(folder)) {
            throw new StorageException("The folder " + folder + " does not exist.");
        }
        if (Files.isDirectory(absolute)) {
            throw new StorageException(absolute + " is a folder, not a file.");
        }
        Path temp = null;
        try {
            // write beside the target, then move: a failed write never destroys a file being replaced
            temp = Files.createTempFile(folder, ".download-", ".tmp");
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
        } catch (AccessDeniedException e) {
            deleteQuietly(temp);
            throw new StorageException("Permission denied: cannot write " + absolute + ".", e);
        } catch (IOException e) {
            deleteQuietly(temp);
            throw new StorageException("Could not write " + absolute + ": " + e.getMessage(), e);
        }
    }

    private static void deleteQuietly(Path temp) {
        if (temp == null) {
            return;
        }
        try {
            Files.deleteIfExists(temp);
        } catch (IOException ignored) {
            // a stray hidden temp file is harmless; the user already sees the real error
        }
    }

    /** A safe default file name for the save dialog, derived from the title. */
    public static String suggestFileName(String title) {
        String base = title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").strip();
        if (base.isEmpty() || base.chars().allMatch(c -> c == '.' || c == '_')) {
            base = FALLBACK_NAME;
        }
        return base.toLowerCase().endsWith(EXTENSION) ? base : base + EXTENSION;
    }
}
