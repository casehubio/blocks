package io.casehub.blocks.agentic.social.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.eidos.api.AgentGoal;
import io.casehub.eidos.api.GoalPriority;
import io.casehub.eidos.api.Visibility;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.smallrye.mutiny.Multi;
import java.util.List;
import org.junit.jupiter.api.Test;

class LlmDriveGoalFormationStrategyTest {

    private final AgentProvider agentProvider = mock(AgentProvider.class);
    private final LlmDriveGoalFormationStrategy strategy =
            new LlmDriveGoalFormationStrategy(agentProvider);

    @Test
    void propose_returnsProposal_fromLlmResponse() {
        String json = """
                {"goalName": "explore-quantum-mechanics", \
                "goalDescription": "Investigate quantum mechanics concepts that appear in recent low-retention memories", \
                "formationReason": "curiosity: fragmented knowledge in physics domain"}""";
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().item(new AgentEvent.TextDelta(json)));

        var context = new DriveGoalFormationContext(
                "agent-1", "tenant-1", DriveAxis.CURIOSITY, 0.7,
                "5 low-retention memories across 3 groups",
                List.of(), 3);

        var proposal = strategy.propose(context);

        assertThat(proposal).isNotNull();
        assertThat(proposal.axis()).isEqualTo(DriveAxis.CURIOSITY);
        assertThat(proposal.goalName()).isEqualTo("explore-quantum-mechanics");
        assertThat(proposal.goalDescription()).contains("quantum mechanics");
        assertThat(proposal.driveIntensity()).isEqualTo(0.7);
    }

    @Test
    void propose_returnsNull_whenLlmReturnsNoGoal() {
        String json = """
                {"goalName": null, "goalDescription": null, "formationReason": null}""";
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().item(new AgentEvent.TextDelta(json)));

        var context = new DriveGoalFormationContext(
                "agent-1", "tenant-1", DriveAxis.CURIOSITY, 0.3,
                "1 low-retention memory", List.of(), 3);

        assertThat(strategy.propose(context)).isNull();
    }

    @Test
    void propose_returnsNull_whenLlmFails() {
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().failure(new RuntimeException("LLM unavailable")));

        var context = new DriveGoalFormationContext(
                "agent-1", "tenant-1", DriveAxis.AFFILIATION, 0.6,
                "2 of 5 relationships neglected", List.of(), 3);

        assertThat(strategy.propose(context)).isNull();
    }

    @Test
    void propose_includesExistingGoalsInPrompt() {
        String json = """
                {"goalName": "deepen-relationship-alice", \
                "goalDescription": "Reconnect with Alice through shared interests", \
                "formationReason": "affiliation: relationship with alice neglected"}""";
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().item(new AgentEvent.TextDelta(json)));

        var existing = new AgentGoal("explore-gaps", "Explore knowledge gaps",
                GoalPriority.SECONDARY, Visibility.PUBLIC, List.of(),
                java.util.Map.of("source", "drive"));
        var context = new DriveGoalFormationContext(
                "agent-1", "tenant-1", DriveAxis.AFFILIATION, 0.6,
                "2 of 5 relationships neglected", List.of(existing), 2);

        var proposal = strategy.propose(context);
        assertThat(proposal).isNotNull();
        assertThat(proposal.goalName()).isEqualTo("deepen-relationship-alice");
    }

    @Test
    void propose_returnsNull_whenResponseUnparseable() {
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().item(new AgentEvent.TextDelta("not json at all")));

        var context = new DriveGoalFormationContext(
                "agent-1", "tenant-1", DriveAxis.COMPETENCE, 0.5,
                "2 declining dimensions", List.of(), 3);

        assertThat(strategy.propose(context)).isNull();
    }

    @Test
    void propose_handlesMultiChunkResponse() {
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().items(
                        new AgentEvent.TextDelta("{\"goalName\": \"improve-clarity\", "),
                        new AgentEvent.TextDelta("\"goalDescription\": \"Focus on clarity dimension\", "),
                        new AgentEvent.TextDelta("\"formationReason\": \"competence: clarity declining\"}")));

        var context = new DriveGoalFormationContext(
                "agent-1", "tenant-1", DriveAxis.COMPETENCE, 0.6,
                "1 declining dimension", List.of(), 3);

        var proposal = strategy.propose(context);
        assertThat(proposal).isNotNull();
        assertThat(proposal.goalName()).isEqualTo("improve-clarity");
    }
}
