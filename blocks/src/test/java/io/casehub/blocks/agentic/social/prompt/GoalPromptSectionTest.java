package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.social.goal.DriveGoalProposal;
import io.casehub.blocks.agentic.social.goal.GoalProposalOrchestrator;
import io.casehub.blocks.speech.PromptContext;
import io.casehub.eidos.api.GoalPriority;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoalPromptSectionTest {

    private static final PromptContext CTX = new PromptContext("agent1", "tenant1", null);

    @Test
    void rendersGoalProposals() {
        var goals = mock(GoalProposalOrchestrator.class);
        when(goals.currentProposals("agent1", "tenant1")).thenReturn(Optional.of(List.of(
                new DriveGoalProposal(DriveAxis.CURIOSITY, "explore-topic",
                        "Learn about the user's interests", "knowledge gap", 0.7,
                        GoalPriority.PRIMARY, null),
                new DriveGoalProposal(DriveAxis.AFFILIATION, "reconnect",
                        "Re-engage with user", "neglected relationship", 0.5))));
        var section = new GoalPromptSection(goals);
        var result = section.contribute(CTX);
        assertThat(result).isNotNull();
        assertThat(result).contains("Learn about the user's interests");
        assertThat(result).contains("Re-engage with user");
        assertThat(result).containsIgnoringCase("curiosity");
    }

    @Test
    void returnsNullWhenNoProposals() {
        var goals = mock(GoalProposalOrchestrator.class);
        when(goals.currentProposals("agent1", "tenant1")).thenReturn(Optional.empty());
        var section = new GoalPromptSection(goals);
        assertThat(section.contribute(CTX)).isNull();
    }

    @Test
    void returnsNullForEmptyProposalList() {
        var goals = mock(GoalProposalOrchestrator.class);
        when(goals.currentProposals("agent1", "tenant1")).thenReturn(Optional.of(List.of()));
        var section = new GoalPromptSection(goals);
        assertThat(section.contribute(CTX)).isNull();
    }
}
