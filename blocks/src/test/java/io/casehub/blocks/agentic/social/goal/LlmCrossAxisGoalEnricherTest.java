package io.casehub.blocks.agentic.social.goal;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.social.narrative.DerivedTheme;
import io.casehub.blocks.agentic.social.narrative.NarrativeFragment;
import io.casehub.blocks.agentic.social.narrative.NarrativeScope;
import io.casehub.blocks.agentic.social.narrative.NarrativeState;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmCrossAxisGoalEnricherTest {

    @Test
    void enriches_compoundGoalDescription() {
        var agentProvider = mock(AgentProvider.class);
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().item(
                        new AgentEvent.TextDelta("Explore knowledge gaps in physics through collaborative study groups")));
        var enricher = new LlmCrossAxisGoalEnricher(agentProvider);

        var theme = new DerivedTheme("t1", Instant.now(), null, List.of(),
                "curious-connector", 0.8,
                Map.of(DriveAxis.CURIOSITY, 0.5, DriveAxis.AFFILIATION, 0.4), List.of());
        var heuristic = new DriveGoalProposal(DriveAxis.CURIOSITY,
                "compound-curiosity-affiliation-curious-connector",
                "Compound goal: curious-connector across CURIOSITY and AFFILIATION",
                "cross-axis", 0.7, null,
                Map.of("crossAxisWeights", "CURIOSITY:0.50,AFFILIATION:0.40"));
        var narrative = narrativeWith(theme);

        var result = enricher.enrich(heuristic, narrative, theme);

        assertThat(result).isNotNull();
        assertThat(result.goalDescription()).contains("physics");
        assertThat(result.axis()).isEqualTo(DriveAxis.CURIOSITY);
        assertThat(result.driveIntensity()).isEqualTo(0.7);
        assertThat(result.proposalAttributes()).containsKey("crossAxisWeights");
    }

    @Test
    void returnsNull_onLlmFailure() {
        var agentProvider = mock(AgentProvider.class);
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().failure(new RuntimeException("LLM timeout")));
        var enricher = new LlmCrossAxisGoalEnricher(agentProvider);

        var theme = new DerivedTheme("t1", Instant.now(), null, List.of(),
                "connector", 0.8,
                Map.of(DriveAxis.CURIOSITY, 0.5, DriveAxis.AFFILIATION, 0.4), List.of());
        var heuristic = new DriveGoalProposal(DriveAxis.CURIOSITY,
                "compound", "desc", "reason", 0.7);

        var result = enricher.enrich(heuristic, narrativeWith(theme), theme);

        assertThat(result).isNull();
    }

    @Test
    void returnsNull_onBlankResponse() {
        var agentProvider = mock(AgentProvider.class);
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().item(new AgentEvent.TextDelta("  ")));
        var enricher = new LlmCrossAxisGoalEnricher(agentProvider);

        var theme = new DerivedTheme("t1", Instant.now(), null, List.of(),
                "connector", 0.8,
                Map.of(DriveAxis.CURIOSITY, 0.5, DriveAxis.AFFILIATION, 0.4), List.of());
        var heuristic = new DriveGoalProposal(DriveAxis.CURIOSITY,
                "compound", "desc", "reason", 0.7);

        var result = enricher.enrich(heuristic, narrativeWith(theme), theme);

        assertThat(result).isNull();
    }

    @Test
    void preservesOriginalFieldsExceptDescription() {
        var agentProvider = mock(AgentProvider.class);
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().item(new AgentEvent.TextDelta("Enriched goal description")));
        var enricher = new LlmCrossAxisGoalEnricher(agentProvider);

        var theme = new DerivedTheme("t1", Instant.now(), null, List.of(),
                "connector", 0.8,
                Map.of(DriveAxis.CURIOSITY, 0.5, DriveAxis.AFFILIATION, 0.4), List.of());
        var attrs = Map.of("crossAxisWeights", "CURIOSITY:0.50,AFFILIATION:0.40");
        var heuristic = new DriveGoalProposal(DriveAxis.CURIOSITY,
                "compound-name", "original-desc", "original-reason", 0.65,
                null, attrs);

        var result = enricher.enrich(heuristic, narrativeWith(theme), theme);

        assertThat(result).isNotNull();
        assertThat(result.goalName()).isEqualTo("compound-name");
        assertThat(result.axis()).isEqualTo(DriveAxis.CURIOSITY);
        assertThat(result.driveIntensity()).isEqualTo(0.65);
        assertThat(result.suggestedPriority()).isNull();
        assertThat(result.proposalAttributes()).isEqualTo(attrs);
        assertThat(result.goalDescription()).isEqualTo("Enriched goal description");
    }

    private NarrativeState narrativeWith(DerivedTheme... themes) {
        List<NarrativeFragment> fragments = new ArrayList<>(List.of(themes));
        return new NarrativeState("a1", "t1", NarrativeScope.INDIVIDUAL,
                fragments, Instant.now(), 5);
    }
}
