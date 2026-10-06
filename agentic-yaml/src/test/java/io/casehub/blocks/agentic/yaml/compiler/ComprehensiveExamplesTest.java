package io.casehub.blocks.agentic.yaml.compiler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.jackson.YamlMappers;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.FailurePolicy;
import io.casehub.blocks.agentic.aggregation.CollectAll;
import io.casehub.blocks.agentic.aggregation.MajorityVote;
import io.casehub.blocks.agentic.judgment.JudgmentPolicy;
import io.casehub.blocks.agentic.model.PatternType;
import io.casehub.blocks.agentic.routing.FirstMatchRouting;
import io.casehub.blocks.agentic.routing.SelectAllRouting;
import io.casehub.blocks.agentic.routing.SequentialRouting;
import io.casehub.blocks.agentic.yaml.spec.PatternSpec;
import io.casehub.blocks.agentic.yaml.spec.cognition.CognitionDefinition;
import io.casehub.blocks.agentic.yaml.spec.world.WorldDefinition;
import io.casehub.blocks.summarisation.observation.affordance.AnnotatedSection;
import io.casehub.blocks.summarisation.observation.affordance.ObservationSection;
import io.casehub.platform.expression.MvelExpressionEngine;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ComprehensiveExamplesTest {

    private ObjectMapper mapper;
    private PatternCompiler patternCompiler;
    private CognitionCompiler cognitionCompiler;
    private WorldCompiler worldCompiler;

    record InteractiveAgentDefinition(
            @Nullable CognitionDefinition cognition,
            @Nullable WorldDefinition world) {}

    @BeforeEach
    void setUp() {
        mapper = YamlMappers.create();
        mapper.registerModule(new JavaTimeModule());
        patternCompiler = new PatternCompiler(new MvelExpressionEngine());
        cognitionCompiler = new CognitionCompiler();
        worldCompiler = new WorldCompiler(new ObservationFilterRegistry());
    }

    @Test
    void researchTeamSupervisorWithComposedParallelSubTeam() throws IOException {
        var spec = loadPattern("research-team");
        var model = patternCompiler.compile(spec);

        assertThat(model.patternType()).isEqualTo(PatternType.SUPERVISOR);
        assertThat(model.routing()).isInstanceOf(FirstMatchRouting.class);
        assertThat(model.aggregation()).isInstanceOf(CollectAll.class);
        assertThat(model.failurePolicy().onRoutingFailure())
                .isEqualTo(FailurePolicy.RoutingFailureAction.RETRY_BROADER);
        assertThat(model.failurePolicy().onDeadlock())
                .isEqualTo(FailurePolicy.AggregationFailureAction.ESCALATE);

        var candidates = model.candidateSupplier().get();
        assertThat(candidates).hasSize(3);

        var researchTeam = candidates.get(0);
        assertThat(researchTeam.ref()).isInstanceOf(AgentRef.ComposedAgent.class);
        var composed = (AgentRef.ComposedAgent) researchTeam.ref();
        assertThat(composed.model().patternType()).isEqualTo(PatternType.PARALLEL);
        assertThat(composed.model().candidateSupplier().get()).hasSize(2);
        var webResearcher = composed.model().candidateSupplier().get().get(0);
        assertThat(webResearcher.descriptor()).isNotNull();
        assertThat(webResearcher.descriptor().capabilities()).hasSize(2);

        var synthesiser = candidates.get(1);
        assertThat(synthesiser.descriptor()).isNotNull();
        assertThat(synthesiser.descriptor().briefing())
                .isEqualTo("Combines research findings into a coherent report");

        assertThat(model.judgment()).isNotNull();
        assertThat(model.judgment()).isInstanceOf(JudgmentPolicy.class);
    }

    @Test
    void reviewPipelineDebateWithTerminationComposition() throws IOException {
        var spec = loadPattern("review-pipeline");
        var model = patternCompiler.compile(spec);

        assertThat(model.patternType()).isEqualTo(PatternType.DEBATE);
        assertThat(model.failurePolicy().onRoutingFailure())
                .isEqualTo(FailurePolicy.RoutingFailureAction.FAIL);
        assertThat(model.failurePolicy().onDeadlock())
                .isEqualTo(FailurePolicy.AggregationFailureAction.FAIL);

        var candidates = model.candidateSupplier().get();
        assertThat(candidates).hasSize(3);

        assertThat(candidates.get(0).descriptor().name()).isEqualTo("structural-reviewer");
        assertThat(candidates.get(1).descriptor().name()).isEqualTo("content-reviewer");
        assertThat(candidates.get(2).descriptor().name()).isEqualTo("readability-reviewer");
    }

    @Test
    void decisionProcessVotingWithEscalationChain() throws IOException {
        var spec = loadPattern("decision-process");
        var model = patternCompiler.compile(spec);

        assertThat(model.patternType()).isEqualTo(PatternType.VOTING);
        assertThat(model.routing()).isInstanceOf(SelectAllRouting.class);
        assertThat(model.aggregation()).isInstanceOf(MajorityVote.class);

        var candidates = model.candidateSupplier().get();
        assertThat(candidates).hasSize(4);
        assertThat(candidates.get(3).ref().name()).isEqualTo("domain-expert");
        assertThat(candidates.get(3).descriptor().name()).isEqualTo("domain-expert");

        assertThat(model.judgment()).isNotNull();
        assertThat(model.judgment()).isInstanceOf(JudgmentPolicy.class);
    }

    @Test
    void interactiveAgentCognitionAndWorld() throws IOException {
        try (var is = getClass().getResourceAsStream("/examples/interactive-agent.yaml")) {
            var definition = mapper.readValue(is, InteractiveAgentDefinition.class);

            var cognition = cognitionCompiler.compile(definition.cognition());
            assertThat(cognition.drive().changeThreshold()).isEqualTo(0.15);
            assertThat(cognition.mood().maxDisplacement()).isEqualTo(0.6);
            assertThat(cognition.mood().baseline().pleasure()).isEqualTo(0.5);
            assertThat(cognition.personality().decayFactor()).isEqualTo(0.03);
            assertThat(cognition.narrative().maxEpisodes()).isEqualTo(50);
            assertThat(cognition.goalProposal().proposalThreshold()).isEqualTo(0.6);
            assertThat(cognition.retention().recencyWeight()).isEqualTo(0.6);

            var world = worldCompiler.compile(definition.world());
            assertThat(world.actions()).hasSize(4);
            assertThat(world.entities()).hasSize(3);
            assertThat(world.sections()).hasSize(4);

            var door = world.entities().get("door");
            assertThat(door.affordances()).hasSize(2);
            assertThat(door.affordances().get(1).requiredItem()).isEqualTo("key");

            var characters = world.sections().get(2);
            assertThat(characters).isInstanceOf(AnnotatedSection.class);
            assertThat(((AnnotatedSection) characters).requiredTags())
                    .containsExactly("social-awareness");

            var exits = (ObservationSection.ItemList) world.sections().get(3);
            assertThat(exits.items()).hasSize(4);

            assertThat(world.rendererThresholds().verbatimThreshold()).isEqualTo(10);
            assertThat(world.rendererThresholds().groupedThreshold()).isEqualTo(25);
        }
    }

    @Test
    void eventProcessingSequenceWithComposedParallelTier() throws IOException {
        var spec = loadPattern("event-processing");
        var model = patternCompiler.compile(spec);

        assertThat(model.patternType()).isEqualTo(PatternType.SEQUENCE);
        assertThat(model.routing()).isInstanceOf(SequentialRouting.class);
        assertThat(model.failurePolicy().onRoutingFailure())
                .isEqualTo(FailurePolicy.RoutingFailureAction.FAIL);
        assertThat(model.failurePolicy().agentRetry().maxRetries()).isEqualTo(2);

        var candidates = model.candidateSupplier().get();
        assertThat(candidates).hasSize(3);

        assertThat(candidates.get(0).descriptor().name()).isEqualTo("event-classifier");
        assertThat(candidates.get(0).descriptor().capabilities()).hasSize(2);

        var analysisTier = candidates.get(1);
        assertThat(analysisTier.ref()).isInstanceOf(AgentRef.ComposedAgent.class);
        var composed = (AgentRef.ComposedAgent) analysisTier.ref();
        assertThat(composed.model().patternType()).isEqualTo(PatternType.PARALLEL);
        assertThat(composed.model().aggregation()).isInstanceOf(CollectAll.class);
        assertThat(composed.model().candidateSupplier().get()).hasSize(2);

        assertThat(candidates.get(2).descriptor().name()).isEqualTo("action-recommender");
    }

    private PatternSpec loadPattern(String name) throws IOException {
        try (var is = getClass().getResourceAsStream("/examples/" + name + ".yaml")) {
            return mapper.readValue(is, PatternSpec.class);
        }
    }
}
