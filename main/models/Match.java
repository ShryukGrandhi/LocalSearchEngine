package models;

/** One occurrence of a query inside a document, as character offsets. */
public record Match(int start, int length) {

    public Match {
        if (start < 0 || length <= 0) {
            throw new IllegalArgumentException("Invalid match: start=" + start + ", length=" + length);
        }
    }

    /** Exclusive end offset. */
    public int end() {
        return start + length;
    }
}
