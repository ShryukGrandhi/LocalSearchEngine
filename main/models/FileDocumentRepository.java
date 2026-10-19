package models;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

/**
 * Stores each document as its own read-only file under {@code <root>/docs/<id>.txt}
 * and keeps titles in {@code <root>/catalog.properties}. Files are named by a
 * random id, not by title, so any title is safe and a re-upload never collides
 * with the copy it replaces.
 *
 * <p>Every change builds a new catalog, writes it atomically, and only then
 * swaps the in-memory map, so a failed write leaves memory and disk consistent.
 */
public final class FileDocumentRepository implements DocumentRepository {

    private static final String CATALOG_FILE = "catalog.properties";
    private static final String DOCS_DIR = "docs";
    private static final String TITLE_SUFFIX = ".title";
    private static final String IMPORTED_SUFFIX = ".importedAt";

    private final Path catalogFile;
    private final Path docsDir;
    private volatile Map<String, Document> documents;
    /** Catalog lines of documents unreadable at startup; written back untouched so they are never lost. */
    private final Map<String, String> unavailableEntries = new HashMap<>();

    public FileDocumentRepository(Path root) {
        this.catalogFile = root.resolve(CATALOG_FILE);
        this.docsDir = root.resolve(DOCS_DIR);
        try {
            Files.createDirectories(docsDir);
        } catch (IOException e) {
            throw new StorageException("Cannot create the library folder " + root, e);
        }
        this.documents = Map.copyOf(loadCatalog());
    }

    @Override
    public List<Document> findAll() {
        List<Document> all = new ArrayList<>(documents.values());
        all.sort(Document.TITLE_ORDER);
        return List.copyOf(all);
    }

    @Override
    public Optional<Document> findById(String id) {
        return Optional.ofNullable(documents.get(id));
    }

    @Override
    public Optional<Document> findByTitle(String title) {
        String key = TitlePolicy.key(title);
        return documents.values().stream()
                .filter(doc -> TitlePolicy.key(doc.title()).equals(key))
                .findFirst();
    }

    @Override
    public synchronized Document save(String title, String content) {
        String validTitle = TitlePolicy.validate(title);
        Optional<Document> replaced = findByTitle(validTitle);
        String id = UUID.randomUUID().toString();
        Path target = contentFile(id);
        long size = writeReadOnly(target, content, validTitle);

        Document document = new Document(id, validTitle, size, Instant.now(), Snippets.preview(content));
        Map<String, Document> next = new HashMap<>(documents);
        replaced.ifPresent(old -> next.remove(old.id()));
        next.put(id, document);
        try {
            writeCatalog(next);
        } catch (StorageException e) {
            deleteFile(target);
            throw e;
        }
        documents = Map.copyOf(next);
        replaced.ifPresent(old -> deleteFile(contentFile(old.id())));
        return document;
    }

    @Override
    public String loadContent(String id) {
        if (!documents.containsKey(id)) {
            throw new StorageException("This document is no longer in the library.");
        }
        try {
            return Files.readString(contentFile(id), StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            throw new StorageException("The stored copy of this document is missing.", e);
        } catch (IOException e) {
            throw new StorageException("Could not read the stored copy of this document.", e);
        }
    }

    @Override
    public synchronized void delete(Collection<String> ids) {
        Map<String, Document> next = new HashMap<>(documents);
        List<String> removed = new ArrayList<>();
        for (String id : ids) {
            if (next.remove(id) != null) {
                removed.add(id);
            }
        }
        if (removed.isEmpty()) {
            return;
        }
        writeCatalog(next);
        documents = Map.copyOf(next);
        removed.forEach(id -> deleteFile(contentFile(id)));
    }

    private Path contentFile(String id) {
        return docsDir.resolve(id + ".txt");
    }

    private long writeReadOnly(Path target, String content, String title) {
        Path temp = null;
        try {
            temp = Files.createTempFile(docsDir, "import-", ".tmp");
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            Files.move(temp, target);
            if (!target.toFile().setReadOnly()) {
                throw new IOException("could not mark " + target + " read-only");
            }
            return Files.size(target);
        } catch (IOException e) {
            deleteFile(temp);
            deleteFile(target);
            throw new StorageException("Could not store \"" + title + "\": " + e.getMessage(), e);
        }
    }

    private Map<String, Document> loadCatalog() {
        Map<String, Document> loaded = new HashMap<>();
        if (!Files.exists(catalogFile)) {
            return loaded;
        }
        Properties catalog = new Properties();
        try (Reader reader = Files.newBufferedReader(catalogFile, StandardCharsets.UTF_8)) {
            catalog.load(reader);
        } catch (IOException | IllegalArgumentException e) {
            throw new StorageException("Could not read the library catalog " + catalogFile, e);
        }
        for (String key : catalog.stringPropertyNames()) {
            if (!key.endsWith(TITLE_SUFFIX)) {
                continue;
            }
            String id = key.substring(0, key.length() - TITLE_SUFFIX.length());
            Path file = contentFile(id);
            String imported = catalog.getProperty(id + IMPORTED_SUFFIX);
            try {
                loaded.put(id, new Document(id, catalog.getProperty(key), sizeOf(file),
                        parseInstant(imported), readPreview(file)));
            } catch (StorageException e) {
                // missing or locked copy: hide it for this session but keep its catalog entry
                unavailableEntries.put(key, catalog.getProperty(key));
                if (imported != null) {
                    unavailableEntries.put(id + IMPORTED_SUFFIX, imported);
                }
            }
        }
        return loaded;
    }

    private void writeCatalog(Map<String, Document> docs) {
        Properties catalog = new Properties();
        unavailableEntries.forEach(catalog::setProperty);
        for (Document doc : docs.values()) {
            catalog.setProperty(doc.id() + TITLE_SUFFIX, doc.title());
            catalog.setProperty(doc.id() + IMPORTED_SUFFIX, doc.importedAt().toString());
        }
        Path temp = null;
        try {
            temp = Files.createTempFile(catalogFile.getParent(), "catalog-", ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                catalog.store(writer, "Local Search Engine library catalog");
            }
            moveReplacing(temp, catalogFile);
        } catch (IOException e) {
            deleteFile(temp);
            throw new StorageException("Could not update the library catalog: " + e.getMessage(), e);
        }
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String readPreview(Path file) {
        char[] buffer = new char[Snippets.PREVIEW_LENGTH];
        int filled = 0;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            int read;
            while (filled < buffer.length && (read = reader.read(buffer, filled, buffer.length - filled)) > 0) {
                filled += read;
            }
        } catch (IOException e) {
            throw new StorageException("Could not read " + file, e);
        }
        return Snippets.preview(new String(buffer, 0, filled));
    }

    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new StorageException("Could not read " + file, e);
        }
    }

    private static Instant parseInstant(String value) {
        if (value == null) {
            return Instant.EPOCH;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return Instant.EPOCH;
        }
    }

    /** Best effort: a leftover file is harmless because the catalog no longer points to it. */
    private static void deleteFile(Path file) {
        if (file == null) {
            return;
        }
        file.toFile().setWritable(true);
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // the catalog is the source of truth; an orphaned file is never shown
        }
    }
}
