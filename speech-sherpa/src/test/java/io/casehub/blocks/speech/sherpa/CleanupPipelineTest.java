package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.CleanupConfig;
import io.casehub.blocks.speech.TextFilter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CleanupPipelineTest {

    @Test
    void casingFilterLowercases() {
        var filter = new CasingFilter();
        assertThat(filter.apply("THE YELLOW LAMPS")).isEqualTo("the yellow lamps");
        assertThat(filter.destructiveness()).isEqualTo(0);
    }

    @Test
    void casingFilterPreservesEmpty() {
        assertThat(new CasingFilter().apply("")).isEmpty();
    }

    @Test
    void fillerRemovalStripsCommonFillers() {
        var filter = new FillerRemovalFilter();
        assertThat(filter.apply("um the uh yellow lamps would um light up"))
                .isEqualTo("the yellow lamps would light up");
        assertThat(filter.destructiveness()).isEqualTo(1);
    }

    @Test
    void fillerRemovalHandlesMultipleSpaces() {
        var filter = new FillerRemovalFilter();
        assertThat(filter.apply("uh  um  er")).isEmpty();
    }

    @Test
    void fillerRemovalPreservesRealWords() {
        var filter = new FillerRemovalFilter();
        assertThat(filter.apply("the umbrella is here")).isEqualTo("the umbrella is here");
    }

    @Test
    void fillerRemovalHandlesVariants() {
        var filter = new FillerRemovalFilter();
        assertThat(filter.apply("umm the uhh lamps")).isEqualTo("the lamps");
        assertThat(filter.apply("hmm interesting")).isEqualTo("interesting");
    }

    @Test
    void cleanupConfigSortsByDestructiveness() {
        TextFilter high = new StubFilter("high", 5);
        TextFilter low = new StubFilter("low", 1);
        TextFilter mid = new StubFilter("mid", 3);

        CleanupConfig config = CleanupConfig.of(high, low, mid);

        assertThat(config.filters().stream().map(TextFilter::name).toList())
                .containsExactly("low", "mid", "high");
    }

    @Test
    void cleanupConfigRespectsMaxDestructiveness() {
        TextFilter a = new StubFilter("a", 1);
        TextFilter b = new StubFilter("b", 3);
        TextFilter c = new StubFilter("c", 5);

        CleanupConfig config = CleanupConfig.upTo(3, a, b, c);

        assertThat(config.filters()).hasSize(2);
        assertThat(config.filters().stream().map(TextFilter::name).toList())
                .containsExactly("a", "b");
    }

    @Test
    void cleanupConfigAppliesFiltersInOrder() {
        TextFilter upper = new TextFilter() {
            @Override public String apply(String t) { return t.toUpperCase(); }
            @Override public String name() { return "upper"; }
            @Override public int destructiveness() { return 0; }
        };
        TextFilter exclaim = new TextFilter() {
            @Override public String apply(String t) { return t + "!"; }
            @Override public String name() { return "exclaim"; }
            @Override public int destructiveness() { return 1; }
        };

        CleanupConfig config = CleanupConfig.of(exclaim, upper);

        assertThat(config.apply("hello")).isEqualTo("HELLO!");
    }

    @Test
    void cleanupConfigWithMaxDestructivenessNarrows() {
        TextFilter a = new StubFilter("a", 1);
        TextFilter b = new StubFilter("b", 5);
        CleanupConfig full = CleanupConfig.of(a, b);
        CleanupConfig narrow = full.withMaxDestructiveness(3);

        assertThat(narrow.filters()).hasSize(1);
        assertThat(narrow.filters().getFirst().name()).isEqualTo("a");
    }

    private record StubFilter(String name, int destructiveness) implements TextFilter {
        @Override
        public String apply(String text) {
            return text;
        }
    }
}
