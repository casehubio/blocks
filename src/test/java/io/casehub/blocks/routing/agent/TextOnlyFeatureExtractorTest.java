package io.casehub.blocks.routing.agent;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.casehub.api.spi.routing.AgentRoutingContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TextOnlyFeatureExtractorTest {

    private final TextOnlyFeatureExtractor extractor = new TextOnlyFeatureExtractor();

    @Test
    void extractFeatures_returnsEmptyMap() {
        var ctx = new AgentRoutingContext(UUID.randomUUID(), "cap", NullNode.instance, "t", List.of());
        assertThat(extractor.extractFeatures(ctx)).isEmpty();
    }

    @Test
    void extractProblem_withObjectNode_returnsStringRepresentation() {
        var node = JsonNodeFactory.instance.objectNode().put("key", "value");
        var ctx = new AgentRoutingContext(UUID.randomUUID(), "cap", node, "t", List.of());
        assertThat(extractor.extractProblem(ctx)).isNotNull().contains("key");
    }

    @Test
    void extractProblem_withNullNode_returnsNull() {
        var ctx = new AgentRoutingContext(UUID.randomUUID(), "cap", NullNode.instance, "t", List.of());
        assertThat(extractor.extractProblem(ctx)).isNull();
    }

    @Test
    void extractProblem_withNullContext_returnsNull() {
        var ctx = new AgentRoutingContext(UUID.randomUUID(), "cap", null, "t", List.of());
        assertThat(extractor.extractProblem(ctx)).isNull();
    }

    @Test
    void extractProblem_withBlankTextNode_returnsNull() {
        var ctx = new AgentRoutingContext(UUID.randomUUID(), "cap", new TextNode("   "), "t", List.of());
        assertThat(extractor.extractProblem(ctx)).isNull();
    }
}
