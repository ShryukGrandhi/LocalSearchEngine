package models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class MatchCursorTest {

    private final List<Match> three = List.of(new Match(0, 1), new Match(5, 1), new Match(9, 1));

    @Test
    void startsAtFirstMatch() {
        MatchCursor cursor = MatchCursor.of(three);
        assertEquals(1, cursor.position());
        assertEquals(three.get(0), cursor.current().orElseThrow());
    }

    @Test
    void nextWrapsFromLastToFirst() {
        MatchCursor cursor = MatchCursor.of(three).next().next();
        assertEquals(3, cursor.position());
        assertEquals(1, cursor.next().position());
    }

    @Test
    void previousWrapsFromFirstToLast() {
        assertEquals(3, MatchCursor.of(three).previous().position());
    }

    @Test
    void movingReturnsNewCursorAndLeavesOriginalUntouched() {
        MatchCursor original = MatchCursor.of(three);
        original.next();
        assertEquals(1, original.position());
    }

    @Test
    void emptyCursorHasNoCurrentAndDoesNotMove() {
        MatchCursor empty = MatchCursor.of(List.of());
        assertTrue(empty.isEmpty());
        assertTrue(empty.current().isEmpty());
        assertEquals(0, empty.position());
        assertSame(empty, empty.next());
        assertSame(empty, empty.previous());
    }

    @Test
    void rejectsOutOfRangeIndex() {
        assertThrows(IllegalArgumentException.class, () -> new MatchCursor(three, 3));
        assertThrows(IllegalArgumentException.class, () -> new MatchCursor(List.of(), 0));
    }
}
