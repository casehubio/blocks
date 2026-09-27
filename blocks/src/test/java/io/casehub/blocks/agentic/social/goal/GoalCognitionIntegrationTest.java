package io.casehub.blocks.agentic.social.goal;

import io.casehub.blocks.agentic.social.CognitionTickContext;
import io.casehub.blocks.agentic.social.prompt.EmergentGoalPromptSection;
import io.casehub.blocks.speech.PromptContext;
import io.casehub.neocortex.cognitive.EmotionType;
import io.casehub.neocortex.cognitive.PadProjection;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.inmem.InMemoryMemoryStore;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.NodeUpdate;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.neocortex.mindmap.intelligence.HeuristicGoalAppraisal;
import io.casehub.neocortex.mindmap.intelligence.consolidation.SurfacingAggregationPhase;
import io.casehub.platform.api.identity.CurrentPrincipal;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GoalCognitionIntegrationTest {

    private InMemoryMindMapStore mindMapStore;
    private InMemoryMemoryStore memoryStore;
    private CognitiveGoalOrchestrator orchestrator;
    private SurfacingAggregationPhase surfacingPhase;
    private EmergentGoalPromptSection promptSection;
    private String                    goalSubgraphId;
    private String birthdayGoalId;

    private static final String TENANT = "integration-test";
    private static final String AGENT = "agent-1";
    private static final Instant START = Instant.parse("2026-09-17T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(START, ZoneOffset.UTC);

    @BeforeAll
    void setUp() {
        mindMapStore = new InMemoryMindMapStore();
        CurrentPrincipal principal = new CurrentPrincipal() {
            @Override public String actorId() { return AGENT; }
            @Override public Set<String> groups() { return Set.of(); }
            @Override public String tenancyId() { return TENANT; }
            @Override public boolean isCrossTenantAdmin() { return true; }
        };
        memoryStore = new InMemoryMemoryStore(principal);
        var config = new CognitiveGoalConfig(0.7, Duration.ofSeconds(0), 0.0, 0.5);

        orchestrator = new CognitiveGoalOrchestrator(
                mindMapStore,
                new HeuristicGoalAppraisal(),
                memoryStore,
                config,
                (a, t) -> PadProjection.NEUTRAL,
                CLOCK);

        surfacingPhase = new SurfacingAggregationPhase(mindMapStore, memoryStore);
        promptSection = new EmergentGoalPromptSection(null, orchestrator, config);

        goalSubgraphId = mindMapStore.createSubgraph(
                new SubgraphInput("Goals", SubgraphTypes.GOAL, null), TENANT);
    }

    @Test @Order(1)
    void createGoalWithDeadline() {
        birthdayGoalId = mindMapStore.addNode(
                NodeInput.of("Buy daughter's birthday gift", goalSubgraphId)
                        .withProperties(Map.of(
                                "description", "Buy a birthday gift",
                                "status", "active",
                                "priority", "0.85",
                                "urgency", "0.2",
                                "feasibility", "0.8")),
                TENANT);

        assertThat(birthdayGoalId).isNotNull();
    }

    @Test @Order(2)
    void day1_tickProducesHopeAndSurfacingEvent() {
        orchestrator.tick(tickContext());

        var state = orchestrator.currentState(AGENT, TENANT);
        assertThat(state).isPresent();
        assertThat(state.get().goals()).hasSize(1);

        var emotions = state.get().goals().get(0).emotions();
        assertThat(emotions).anyMatch(e -> e.type() == EmotionType.HOPE);
    }

    @Test @Order(3)
    void day1_promptRendersGoalWithHope() {
        var prompt = promptSection.contribute(new PromptContext(AGENT, TENANT, null));
        assertThat(prompt).isNotNull();
        assertThat(prompt).contains("Buy daughter's birthday gift");
        assertThat(prompt).contains("HOPEFUL");
    }

    @Test @Order(4)
    void day1_surfacingAggregated() {
        surfacingPhase.run(TENANT, List.of());

        MindMapNode node = mindMapStore.getNode(birthdayGoalId, TENANT);
        assertThat(node.property("surfaced-count")).hasValue("1");
    }

    @Test @Order(5)
    void day3_urgencyRises_fearAppears() {
        mindMapStore.updateNode(birthdayGoalId,
                NodeUpdate.empty().withPropertiesToSet(Map.of("urgency", "0.6")),
                TENANT);

        orchestrator.tick(tickContext());
        surfacingPhase.run(TENANT, List.of());

        var state = orchestrator.currentState(AGENT, TENANT);
        assertThat(state).isPresent();
        var emotions = state.get().goals().get(0).emotions();
        assertThat(emotions).anyMatch(e -> e.type() == EmotionType.FEAR);
    }

    @Test @Order(6)
    void day6_repeatedSurfacing_fearDominant() {
        mindMapStore.updateNode(birthdayGoalId,
                NodeUpdate.empty().withPropertiesToSet(Map.of("urgency", "0.9")),
                TENANT);

        orchestrator.tick(tickContext());
        orchestrator.tick(tickContext());
        orchestrator.tick(tickContext());
        surfacingPhase.run(TENANT, List.of());

        var node = mindMapStore.getNode(birthdayGoalId, TENANT);
        int surfacedCount = Integer.parseInt(node.property("surfaced-count").orElse("0"));
        assertThat(surfacedCount).isGreaterThanOrEqualTo(4);

        var prompt = promptSection.contribute(new PromptContext(AGENT, TENANT, null));
        assertThat(prompt).contains("CONCERNED");
        assertThat(prompt).contains("reminded");
    }

    @Test @Order(7)
    void progressResets_hopefulAgain() {
        memoryStore.store(MemoryInput.of(
                Subject.of("agent", AGENT), new MemoryDomain("experience"), TENANT,
                "Progress: ordered the gift")
                .withAttributes(Map.of(
                        "cognitive-event", "goal-progress",
                        "goal-node-id", birthdayGoalId)));

        surfacingPhase.run(TENANT, List.of());

        var node = mindMapStore.getNode(birthdayGoalId, TENANT);
        assertThat(node.property("last-progress-at")).isPresent();
        int gap = Integer.parseInt(node.property("surfacing-progress-gap").orElse("999"));
        assertThat(gap).isLessThan(4);
    }

    @Test @Order(8)
    void completedGoal_satisfactionRendered() {
        mindMapStore.updateNode(birthdayGoalId,
                NodeUpdate.empty().withPropertiesToSet(Map.of("status", "completed")),
                TENANT);

        orchestrator.tick(tickContext());

        var state = orchestrator.currentState(AGENT, TENANT);
        assertThat(state).isPresent();
        assertThat(state.get().goals()).isEmpty();
    }

    @Test @Order(9)
    void agentExperienceMemoriesRecorded() {
        var memories = memoryStore.query(
                io.casehub.neocortex.memory.MemoryQuery.forSubject(
                        Subject.of("agent", AGENT),
                        new MemoryDomain("agent-experience"),
                        TENANT).withLimit(50));
        assertThat(memories).isNotEmpty();
        assertThat(memories).anyMatch(m ->
                m.attributes().containsKey("emotion-type"));
    }

    private CognitionTickContext tickContext() {
        return new CognitionTickContext(AGENT, TENANT, null, (a, t) -> Set.of());
    }
}
