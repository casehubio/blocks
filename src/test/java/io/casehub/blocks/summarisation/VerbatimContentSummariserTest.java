package io.casehub.blocks.summarisation;

import io.casehub.qhorus.api.spi.SummaryResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VerbatimContentSummariserTest {

    private final VerbatimContentSummariser<String> summariser =
            new VerbatimContentSummariser<>(s -> s);

    @Test
    void rendersItemsAsBulletList() {
        var result = summariser.summarise(List.of("alpha", "beta"), null)
                .toCompletableFuture().join();

        assertThat(result.text()).isEqualTo("- alpha\n- beta");
        assertThat(result.annotations()).containsEntry("tier", "verbatim");
        assertThat(result.annotations()).containsEntry("itemCount", "2");
    }

    @Test
    void preservesPreviousSummaryText() {
        var previous = new SummaryResult("existing content", Map.of("key", "value"));
        var result = summariser.summarise(List.of("new item"), previous)
                .toCompletableFuture().join();

        assertThat(result.text()).startsWith("existing content\n\n");
        assertThat(result.text()).contains("- new item");
    }

    @Test
    void propagatesPreviousAnnotations() {
        var previous = new SummaryResult("prior", Map.of("domain", "medical", "urgency", "high"));
        var result = summariser.summarise(List.of("item"), previous)
                .toCompletableFuture().join();

        assertThat(result.annotations())
                .containsEntry("domain", "medical")
                .containsEntry("urgency", "high")
                .containsEntry("tier", "verbatim");
    }

    @Test
    void nullPrevious_noPreamble() {
        var result = summariser.summarise(List.of("only"), null)
                .toCompletableFuture().join();

        assertThat(result.text()).isEqualTo("- only");
        assertThat(result.annotations()).doesNotContainKey("domain");
    }

    @Test
    void emptyItems_producesEmptyResult() {
        var result = summariser.summarise(List.of(), null)
                .toCompletableFuture().join();

        assertThat(result.text()).isEmpty();
    }
}
