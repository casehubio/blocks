package io.casehub.blocks.agentic.personality;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TokenJaccardDistanceTest {

    @Test
    void identicalTextReturnsZero() {
        assertThat(TokenJaccardDistance.distance("hello world", "hello world")).isEqualTo(0.0);
    }

    @Test
    void completelyDisjointReturnsOne() {
        assertThat(TokenJaccardDistance.distance("hello world", "foo bar")).isEqualTo(1.0);
    }

    @Test
    void partialOverlap() {
        double d = TokenJaccardDistance.distance("hello world foo", "hello bar baz");
        assertThat(d).isCloseTo(0.8, within(0.001));
    }

    @Test
    void emptyStringsReturnZero() {
        assertThat(TokenJaccardDistance.distance("", "")).isEqualTo(0.0);
    }

    @Test
    void oneEmptyReturnsOne() {
        assertThat(TokenJaccardDistance.distance("hello", "")).isEqualTo(1.0);
        assertThat(TokenJaccardDistance.distance("", "hello")).isEqualTo(1.0);
    }

    @Test
    void caseInsensitive() {
        assertThat(TokenJaccardDistance.distance("Hello World", "hello world")).isEqualTo(0.0);
    }

    @Test
    void nullInputsTreatedAsEmpty() {
        assertThat(TokenJaccardDistance.distance(null, null)).isEqualTo(0.0);
        assertThat(TokenJaccardDistance.distance(null, "hello")).isEqualTo(1.0);
    }
}
