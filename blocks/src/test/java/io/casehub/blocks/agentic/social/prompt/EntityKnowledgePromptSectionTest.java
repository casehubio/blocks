package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.speech.PromptContext;
import io.casehub.neocortex.cognitive.index.AffectTrajectory;
import io.casehub.neocortex.cognitive.index.EntityKnowledge;
import io.casehub.neocortex.cognitive.index.TrendDirection;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.mindmap.MindMapEdge;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.NodeRef;
import io.casehub.platform.api.identity.PrincipalId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EntityKnowledgePromptSectionTest {

    private static final PromptContext CTX = new PromptContext("agent", "tenant", null);

    @Test
    void returnsNullWhenNoEntities() {
        var section = new EntityKnowledgePromptSection(List.of(), Map.of());
        assertThat(section.contribute(CTX)).isNull();
    }

    @Test
    void rendersEntityWithAllFields() {
        var node = stubNode("Penelope Pitstop", "CHARACTER");
        var edge = stubEdge("node-peter", "ALLY");
        var memory = stubMemory("experience", "Helped escape trap");
        var trajectory = new AffectTrajectory(0.3, 0.1, 0.2, 0.1,
                TrendDirection.IMPROVING, 0.15, 5);

        var ek = new EntityKnowledge(node, List.of(edge),
                Map.of(new MemoryDomain("experience"), List.of(memory)),
                trajectory, Set.of(), "tenant", PrincipalId.agent("agent"));

        var section = new EntityKnowledgePromptSection(List.of(ek), Map.of());
        var text = section.contribute(CTX);

        assertThat(text).contains("Penelope Pitstop");
        assertThat(text).contains("ally");
        assertThat(text).contains("Helped escape trap");
        assertThat(text).contains("improving");
    }

    @Test
    void rendersMultipleEntities() {
        var node1 = stubNode("Penelope", "CHARACTER");
        var node2 = stubNode("Muttley", "CHARACTER");
        var ek1 = new EntityKnowledge(node1, List.of(), Map.of(), null, Set.of(),
                "tenant", PrincipalId.agent("agent"));
        var ek2 = new EntityKnowledge(node2, List.of(), Map.of(), null, Set.of(),
                "tenant", PrincipalId.agent("agent"));

        var section = new EntityKnowledgePromptSection(List.of(ek1, ek2), Map.of());
        var text = section.contribute(CTX);

        assertThat(text).contains("Penelope");
        assertThat(text).contains("Muttley");
    }

    @Test
    void handlesEmptyTrajectoryGracefully() {
        var node = stubNode("Penelope", "CHARACTER");
        var ek = new EntityKnowledge(node, List.of(), Map.of(), null, Set.of(),
                "tenant", PrincipalId.agent("agent"));

        var section = new EntityKnowledgePromptSection(List.of(ek), Map.of());
        var text = section.contribute(CTX);

        assertThat(text).doesNotContain("trajectory");
    }

    @Test
    void rendersUnresolvedRefs() {
        var node = stubNode("Penelope", "CHARACTER");
        var ref = new NodeRef("mindmap", "ref-123", "Mystery Person");
        var ek = new EntityKnowledge(node, List.of(), Map.of(), null, Set.of(ref),
                "tenant", PrincipalId.agent("agent"));

        var section = new EntityKnowledgePromptSection(List.of(ek), Map.of());
        var text = section.contribute(CTX);

        assertThat(text).contains("Mystery Person");
        assertThat(text).contains("not yet known");
    }

    @Test
    void rendersComparisonWhenPresent() {
        var node = stubNode("Penelope", "CHARACTER");
        var selfTrajectory = new AffectTrajectory(0.3, 0.1, 0.2, 0.1,
                TrendDirection.IMPROVING, 0.15, 5);
        var otherTrajectory = new AffectTrajectory(-0.2, 0.3, -0.1, 0.2,
                TrendDirection.WORSENING, 0.2, 5);
        var selfEk = new EntityKnowledge(node, List.of(), Map.of(), selfTrajectory, Set.of(),
                "tenant", PrincipalId.agent("self"));
        var otherEk = new EntityKnowledge(node, List.of(), Map.of(), otherTrajectory, Set.of(),
                "tenant", PrincipalId.agent("other-agent"));

        var comparisons = Map.of("node-Penelope",
                Map.of(PrincipalId.agent("other-agent"), otherEk));

        var section = new EntityKnowledgePromptSection(List.of(selfEk), comparisons);
        var text = section.contribute(CTX);

        assertThat(text).contains("How others see");
        assertThat(text).contains("worsening");
    }

    @Test
    void respectsMemoryLimit() {
        var node = stubNode("Penelope", "CHARACTER");
        var domain = new MemoryDomain("experience");
        var memories = List.of(
                stubMemory("experience", "Memory 1"),
                stubMemory("experience", "Memory 2"),
                stubMemory("experience", "Memory 3"),
                stubMemory("experience", "Memory 4"),
                stubMemory("experience", "Memory 5"));
        var ek = new EntityKnowledge(node, List.of(), Map.of(domain, memories), null, Set.of(),
                "tenant", PrincipalId.agent("agent"));

        var section = new EntityKnowledgePromptSection(List.of(ek), Map.of());
        var text = section.contribute(CTX);

        assertThat(text).contains("Memory 1");
        assertThat(text).contains("Memory 3");
        assertThat(text).doesNotContain("Memory 4");
    }

    private static MindMapNode stubNode(String name, String subgraphType) {
        var node = mock(MindMapNode.class);
        when(node.name()).thenReturn(name);
        when(node.id()).thenReturn("node-" + name);
        when(node.subgraphType()).thenReturn(subgraphType);
        when(node.properties()).thenReturn(Map.of());
        return node;
    }

    private static MindMapEdge stubEdge(String targetNodeId, String edgeType) {
        var edge = mock(MindMapEdge.class);
        when(edge.targetNodeId()).thenReturn(targetNodeId);
        when(edge.edgeType()).thenReturn(edgeType);
        return edge;
    }

    private static Memory stubMemory(String domainName, String text) {
        return new Memory("mem-1", new io.casehub.neocortex.memory.Subject("entity", "entity-1"),
                new MemoryDomain(domainName), "tenant", "case1",
                text, Map.of(), Instant.now(), null, null, null, null, null, Set.of());
    }
}
