package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.mindmap.ConsolidationArtifact;
import io.casehub.neocortex.mindmap.intelligence.consolidation.ConsolidationCompleted;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ConsolidationMediatorTest {

    @Test
    void drainReturnsEmptyWhenNoConsolidation() {
        var mediator = new ConsolidationMediator();
        assertThat(mediator.drainForAgent("agent-1", "t1")).isEmpty();
    }

    @Test
    void drainReturnsArtifactsAfterConsolidation() {
        var mediator = new ConsolidationMediator();
        var artifact = new ConsolidationArtifact.MergeFlagged(
            "t1", "n1", "n2", 0.8, "test", Instant.now());
        mediator.onConsolidation(new ConsolidationCompleted(
            "t1", List.of(), List.of(artifact)));
        var result = mediator.drainForAgent("agent-1", "t1");
        assertThat(result).containsExactly(artifact);
    }

    @Test
    void secondDrainReturnsEmptyForSameAgent() {
        var mediator = new ConsolidationMediator();
        var artifact = new ConsolidationArtifact.MergeFlagged(
            "t1", "n1", "n2", 0.8, "test", Instant.now());
        mediator.onConsolidation(new ConsolidationCompleted(
            "t1", List.of(), List.of(artifact)));
        mediator.drainForAgent("agent-1", "t1");
        assertThat(mediator.drainForAgent("agent-1", "t1")).isEmpty();
    }

    @Test
    void differentAgentSeesSameConsolidation() {
        var mediator = new ConsolidationMediator();
        var artifact = new ConsolidationArtifact.MergeFlagged(
            "t1", "n1", "n2", 0.8, "test", Instant.now());
        mediator.onConsolidation(new ConsolidationCompleted(
            "t1", List.of(), List.of(artifact)));
        mediator.drainForAgent("agent-1", "t1");
        assertThat(mediator.drainForAgent("agent-2", "t1")).containsExactly(artifact);
    }

    @Test
    void graduatedExperienceFilteredByAgentId() {
        var mediator = new ConsolidationMediator();
        var agentA = new ConsolidationArtifact.GraduatedExperience(
            "t1", "n1", "learned X", "episodic", 0.8, "agent-a", "event", Instant.now());
        var agentB = new ConsolidationArtifact.GraduatedExperience(
            "t1", "n2", "learned Y", "episodic", 0.7, "agent-b", "event", Instant.now());
        var merge = new ConsolidationArtifact.MergeFlagged(
            "t1", "n3", "n4", 0.8, "test", Instant.now());
        mediator.onConsolidation(new ConsolidationCompleted(
            "t1", List.of(), List.of(agentA, agentB, merge)));
        var resultA = mediator.drainForAgent("agent-a", "t1");
        assertThat(resultA).containsExactly(agentA, merge);
    }

    @Test
    void crossTenantIsolation() {
        var mediator = new ConsolidationMediator();
        var art1 = new ConsolidationArtifact.MergeFlagged(
            "t1", "n1", "n2", 0.8, "test", Instant.now());
        var art2 = new ConsolidationArtifact.MergeFlagged(
            "t2", "n3", "n4", 0.9, "test", Instant.now());
        mediator.onConsolidation(new ConsolidationCompleted("t1", List.of(), List.of(art1)));
        mediator.onConsolidation(new ConsolidationCompleted("t2", List.of(), List.of(art2)));
        mediator.drainForAgent("agent-1", "t1");
        assertThat(mediator.drainForAgent("agent-1", "t2")).containsExactly(art2);
    }
}
