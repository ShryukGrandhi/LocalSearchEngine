package models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class CaseInsensitivePhraseStrategyTest {

    private final MatchStrategy strategy = new CaseInsensitivePhraseStrategy();
    private final Query query = Query.parse("computer algorithm");

    @ParameterizedTest(name = "matches \"{0}\"")
    @ValueSource(strings = {
        "computer algorithm",
        "CoMpUtEr aLgOriThm",
        "The computer algorithm",
        "Computer Algorithms",
        "Randomized Computer Algorithms"
    })
    @DisplayName("spec examples that must match")
    void matchesSpecPositiveExamples(String text) {
        assertTrue(strategy.findFirst(IndexedText.of(text), query).isPresent());
    }

    @ParameterizedTest(name = "rejects \"{0}\"")
    @ValueSource(strings = {
        "Computer_Algorithm",
        "Computer  Algorithm",
        "Computing algorithms",
        "computer randomized algorithms",
        "computer\nalgorithm"
    })
    @DisplayName("spec examples that must not match")
    void rejectsSpecNegativeExamples(String text) {
        assertFalse(strategy.findFirst(IndexedText.of(text), query).isPresent());
    }

    @Test
    void findFirstReportsOffsetAndLength() {
        Match match = strategy.findFirst(IndexedText.of("The Computer Algorithm"), query).orElseThrow();
        assertEquals(new Match(4, 18), match);
    }

    @Test
    void findAllReturnsEveryNonOverlappingMatchInOrder() {
        IndexedText text = IndexedText.of("aaaa");
        List<Match> matches = strategy.findAll(text, Query.parse("aa"));
        assertEquals(List.of(new Match(0, 2), new Match(2, 2)), matches);
    }

    @Test
    void findAllReturnsEmptyListWhenNothingMatches() {
        assertTrue(strategy.findAll(IndexedText.of("nothing here"), query).isEmpty());
    }

    @Test
    void indexKeyIsFoldedQuery() {
        assertEquals("computer algorithm", strategy.indexKey(Query.parse("  COMPUTER Algorithm ")));
    }
}
