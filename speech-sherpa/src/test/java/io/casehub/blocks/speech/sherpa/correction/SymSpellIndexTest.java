package io.casehub.blocks.speech.sherpa.correction;

import io.casehub.blocks.speech.CorrectionStrategy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SymSpellIndexTest {

    private static SymSpellIndex index;

    @BeforeAll
    static void loadIndex() {
        index = SymSpellIndex.fromResource("test-dictionary.txt");
    }

    @Test
    void findsEditDistance1() {
        var results = index.lookup("helo");
        assertThat(results).extracting(SymSpellIndex.SuggestItem::word).contains("hello");
    }

    @Test
    void findsEditDistance2() {
        var results = index.lookup("hllo");
        assertThat(results).extracting(SymSpellIndex.SuggestItem::word).contains("hello");
    }

    @Test
    void returnsEmptyForHighEditDistance() {
        var results = index.lookup("xyzabc");
        assertThat(results).isEmpty();
    }

    @Test
    void exactMatchReturnsDistance0() {
        var results = index.lookup("hello");
        assertThat(results)
                .extracting(SymSpellIndex.SuggestItem::word).contains("hello");
        assertThat(results.stream().filter(s -> s.word().equals("hello")).findFirst())
                .hasValueSatisfying(s -> assertThat(s.distance()).isZero());
    }

    @Test
    void resultsSortedByDistanceThenFrequency() {
        var results = index.lookup("helo");
        assertThat(results).isNotEmpty();
        for (int i = 1; i < results.size(); i++) {
            var prev = results.get(i - 1);
            var curr = results.get(i);
            if (prev.distance() == curr.distance()) {
                assertThat(prev.frequency()).isGreaterThanOrEqualTo(curr.frequency());
            } else {
                assertThat(prev.distance()).isLessThan(curr.distance());
            }
        }
    }

    @Test
    void dynamicAddMakesWordKnown() {
        var localIndex = SymSpellIndex.fromResource("test-dictionary.txt");
        localIndex.add("avatar", 10000);
        var results = localIndex.lookup("avtar");
        assertThat(results).extracting(SymSpellIndex.SuggestItem::word).contains("avatar");
    }

    @Test
    void dynamicAddExactLookupReturnsDistance0() {
        var localIndex = SymSpellIndex.fromResource("test-dictionary.txt");
        localIndex.add("kubernetes", 5000);
        var results = localIndex.lookup("kubernetes");
        assertThat(results).extracting(SymSpellIndex.SuggestItem::word).contains("kubernetes");
    }

    @Test
    void containsReturnsTrueForKnownWord() {
        assertThat(index.contains("hello")).isTrue();
    }

    @Test
    void containsReturnsFalseForUnknownWord() {
        assertThat(index.contains("xyzabc")).isFalse();
    }

    @Test
    void dictionaryReturnsAllKnownWords() {
        assertThat(index.dictionary()).contains("hello", "world", "limerick", "cat", "the");
    }

    @Test
    void strategyReturnsCandidates() {
        var strategy = new SymSpellStrategy(index);
        var ctx = new CorrectionStrategy.CorrectionContext("", "", Set.of());
        var candidates = strategy.candidates("helo", ctx);
        assertThat(candidates).isNotEmpty();
        assertThat(candidates).extracting(CorrectionStrategy.Candidate::word).contains("hello");
        assertThat(candidates).extracting(CorrectionStrategy.Candidate::source)
                .allMatch(s -> s.equals("symspell"));
    }

    @Test
    void strategyConfidenceDecreasesWithDistance() {
        var strategy = new SymSpellStrategy(index);
        var ctx = new CorrectionStrategy.CorrectionContext("", "", Set.of());
        var dist1 = strategy.candidates("helo", ctx).stream()
                .filter(c -> c.word().equals("hello")).findFirst().orElseThrow();
        var dist0 = strategy.candidates("hello", ctx).stream()
                .filter(c -> c.word().equals("hello")).findFirst().orElseThrow();
        assertThat(dist0.confidence()).isGreaterThan(dist1.confidence());
    }

    @Test
    void strategyReturnsEmptyForNoMatch() {
        var strategy = new SymSpellStrategy(index);
        var ctx = new CorrectionStrategy.CorrectionContext("", "", Set.of());
        assertThat(strategy.candidates("xyzabc", ctx)).isEmpty();
    }
}
