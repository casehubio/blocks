package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.speech.PromptContext;
import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.index.AttentionItem;
import io.casehub.neocortex.cognitive.index.TemporalEntry;
import io.casehub.neocortex.cognitive.index.TemporalSource;
import io.casehub.neocortex.mindmap.MindMapNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TemporalFocusPromptSectionTest {

    @Test
    void rendersItemsWithSalience() {
        var items = List.of(
                item("approaching deadline", 0.85),
                item("recent mood shift", 0.62));
        var section = new TemporalFocusPromptSection(items);

        var result = section.contribute(new PromptContext("a", "t", null));

        assertThat(result).startsWith("Temporal awareness:");
        assertThat(result).contains("approaching deadline (salience: 0.85)");
        assertThat(result).contains("recent mood shift (salience: 0.62)");
    }

    @Test
    void returnsNullForEmptyList() {
        var section = new TemporalFocusPromptSection(List.of());
        assertThat(section.contribute(new PromptContext("a", "t", null))).isNull();
    }

    @Test
    void limitsToFiveItems() {
        var items = IntStream.rangeClosed(1, 8)
                .mapToObj(i -> item("item-" + i, 1.0 - i * 0.1))
                .toList();
        var section = new TemporalFocusPromptSection(items);

        var result = section.contribute(new PromptContext("a", "t", null));

        assertThat(result).contains("item-1").contains("item-5");
        assertThat(result).doesNotContain("item-6");
    }

    private static AttentionItem item(String reason, double salience) {
        var node = mock(MindMapNode.class);
        when(node.id()).thenReturn("n");
        return new AttentionItem(
                new TemporalEntry(Instant.now(), new TemporalSource.FromMindMap(node),
                        "t", Confidence.stated(0.9, Instant.now())),
                salience, reason);
    }
}
