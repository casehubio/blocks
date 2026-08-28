package io.casehub.blocks.speech.sherpa.correction;

import io.casehub.blocks.speech.CorrectionStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneticIndexTest {

    @Test
    void findsSoundAlikeCandidates() {
        var index = PhoneticIndex.fromWords(List.of("limerick", "hello", "world", "cat"));
        var candidates = index.lookup("limmerick");
        assertThat(candidates).contains("limerick");
    }

    @Test
    void returnsEmptyForNoPhoneticMatch() {
        var index = PhoneticIndex.fromWords(List.of("hello", "world"));
        var candidates = index.lookup("xyzabc");
        assertThat(candidates).isEmpty();
    }

    @Test
    void dynamicAddMakesWordFindable() {
        var index = PhoneticIndex.fromWords(List.of("hello"));
        index.addWord("kubernetes");
        var candidates = index.lookup("kuberneetees");
        assertThat(candidates).contains("kubernetes");
    }

    @Test
    void exactMatchReturnsSelf() {
        var index = PhoneticIndex.fromWords(List.of("hello", "world", "cat"));
        var candidates = index.lookup("hello");
        assertThat(candidates).contains("hello");
    }

    @Test
    void strategyReturnsCandidatesWithPhoneticSource() {
        var index = PhoneticIndex.fromWords(List.of("limerick", "hello"));
        var strategy = new PhoneticStrategy(index);
        var ctx = new CorrectionStrategy.CorrectionContext("read", "", Set.of());
        var candidates = strategy.candidates("limmerick", ctx);
        assertThat(candidates).extracting(CorrectionStrategy.Candidate::word).contains("limerick");
        assertThat(candidates).extracting(CorrectionStrategy.Candidate::source)
                .allMatch(s -> s.equals("phonetic"));
    }

    @Test
    void strategyReturnsEmptyForNoMatch() {
        var index = PhoneticIndex.fromWords(List.of("hello"));
        var strategy = new PhoneticStrategy(index);
        var ctx = new CorrectionStrategy.CorrectionContext("", "", Set.of());
        assertThat(strategy.candidates("xyzabc", ctx)).isEmpty();
    }

    @Test
    void phoneticConfidenceLowerThanSymspell() {
        var index = PhoneticIndex.fromWords(List.of("hello"));
        var strategy = new PhoneticStrategy(index);
        var ctx = new CorrectionStrategy.CorrectionContext("", "", Set.of());
        var candidates = strategy.candidates("hello", ctx);
        assertThat(candidates).isNotEmpty();
        assertThat(candidates.getFirst().confidence()).isLessThanOrEqualTo(0.7);
    }
}
