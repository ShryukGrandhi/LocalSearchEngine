package models;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * In-memory search index: keeps every document's text and a trigram posting
 * list. A query's trigrams are intersected to find the few documents that could
 * contain it; only those are scanned with {@link String#indexOf}. Trigrams can
 * only rule documents out, never in, so the result is always exact.
 *
 * <p>Thread-safe: imports and deletes take the write lock, searches the read lock.
 */
public final class SearchIndex {

    private static final int GRAM = 3;
    private static final int BITS_PER_CHAR = 7;
    private static final int CHAR_MASK = (1 << BITS_PER_CHAR) - 1;
    private static final int GRAM_SPACE = 1 << (BITS_PER_CHAR * GRAM);

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<String, IndexedDocument> documents = new HashMap<>();
    private final Map<String, int[]> gramsByDocument = new HashMap<>();
    private final Map<Integer, Set<String>> postings = new HashMap<>();

    /** Adds or replaces the document. The expensive work happens before the lock is taken. */
    public void put(Document document, String content) {
        IndexedText text = IndexedText.of(content);
        int[] grams = gramsOf(text.folded());
        lock.writeLock().lock();
        try {
            removeLocked(document.id());
            documents.put(document.id(), new IndexedDocument(document, text));
            gramsByDocument.put(document.id(), grams);
            for (int gram : grams) {
                postings.computeIfAbsent(gram, key -> new HashSet<>()).add(document.id());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void remove(String id) {
        lock.writeLock().lock();
        try {
            removeLocked(id);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Optional<IndexedDocument> get(String id) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(documents.get(id));
        } finally {
            lock.readLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return documents.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Documents that may contain {@code foldedKey}: a superset of the real matches. */
    public List<IndexedDocument> candidates(String foldedKey) {
        lock.readLock().lock();
        try {
            if (foldedKey.length() < GRAM) {
                return List.copyOf(documents.values());
            }
            List<Set<String>> lists = new ArrayList<>();
            for (int gram : gramsOf(foldedKey)) {
                Set<String> ids = postings.get(gram);
                if (ids == null) {
                    return List.of();
                }
                lists.add(ids);
            }
            lists.sort(Comparator.comparingInt(Set::size));
            Set<String> ids = new HashSet<>(lists.get(0));
            for (int i = 1; i < lists.size() && !ids.isEmpty(); i++) {
                ids.retainAll(lists.get(i));
            }
            List<IndexedDocument> result = new ArrayList<>(ids.size());
            for (String id : ids) {
                result.add(documents.get(id));
            }
            return List.copyOf(result);
        } finally {
            lock.readLock().unlock();
        }
    }

    private void removeLocked(String id) {
        int[] grams = gramsByDocument.remove(id);
        documents.remove(id);
        if (grams == null) {
            return;
        }
        for (int gram : grams) {
            Set<String> ids = postings.get(gram);
            if (ids != null) {
                ids.remove(id);
                if (ids.isEmpty()) {
                    postings.remove(gram);
                }
            }
        }
    }

    /**
     * Distinct trigrams of the text, each packed into 21 bits. Characters above
     * 127 share one bucket; that only adds false candidates, which the exact
     * scan removes.
     */
    static int[] gramsOf(String folded) {
        BitSet seen = new BitSet(GRAM_SPACE);
        for (int i = 0; i + GRAM <= folded.length(); i++) {
            int gram = 0;
            for (int j = 0; j < GRAM; j++) {
                gram = (gram << BITS_PER_CHAR) | Math.min(folded.charAt(i + j), CHAR_MASK);
            }
            seen.set(gram);
        }
        return seen.stream().toArray();
    }
}
