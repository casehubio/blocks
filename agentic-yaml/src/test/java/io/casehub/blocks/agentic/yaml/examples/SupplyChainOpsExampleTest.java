package io.casehub.blocks.agentic.yaml.examples;

import io.casehub.blocks.agentic.FailurePolicy;
import io.casehub.blocks.agentic.aggregation.CollectAll;
import io.casehub.blocks.agentic.judgment.JudgmentPolicy;
import io.casehub.blocks.agentic.model.PatternType;
import io.casehub.blocks.agentic.routing.FirstMatchRouting;
import io.casehub.neocortex.cognition.drive.DriveAxis;
import io.casehub.blocks.agentic.yaml.compiler.NegotiationCompiler;
import io.casehub.blocks.agentic.yaml.registry.ConflictResolutionRegistry;
import io.casehub.blocks.agentic.yaml.registry.TerminationConditionRegistry;
import io.casehub.blocks.agentic.yaml.spec.ConflictResolutionSpec;
import io.casehub.blocks.agentic.yaml.spec.NegotiationSpec;
import io.casehub.blocks.negotiation.NegotiationOutcome;
import io.casehub.blocks.normative.MostRestrictiveResolution;
import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.Summariser;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.message.MessageView;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SupplyChainOpsExampleTest extends ExampleTestBase {

    private static final String SCENARIO = "supply-chain-ops";

    @Test
    void pipelineCompiles() throws IOException {
        summariserRegistry.register("field-extract", config ->
                Summariser.ofSync(batch -> batch.stream()
                        .map(e -> Map.of("extracted", e.payload()))
                        .toList()));

        var def = loadPipeline(SCENARIO);
        var pipeline = pipelineCompiler.<Object>compile(def, summariserRegistry, null, pipelineExpressionEngine);

        assertThat(pipeline.name()).isEqualTo("supply-chain-monitoring");
        assertThat(def.levels()).hasSize(4);
        assertThat(def.levels().get(0).name()).isEqualTo("detection");
        assertThat(def.levels().get(0).summariser().type()).isEqualTo("threshold-classify");
        assertThat(def.levels().get(1).name()).isEqualTo("triage");
        assertThat(def.levels().get(1).summariser().type()).isEqualTo("phase-detect");
        assertThat(def.levels().get(2).name()).isEqualTo("response");
        assertThat(def.levels().get(2).summariser().type()).isEqualTo("count");
        assertThat(def.levels().get(3).name()).isEqualTo("resolution");
        assertThat(def.levels().get(3).summariser().type()).isEqualTo("field-extract");
    }

    @Test
    void patternCompiles() throws IOException {
        var spec = loadPattern(SCENARIO);
        var model = patternCompiler.compile(spec);

        assertThat(model.patternType()).isEqualTo(PatternType.SUPERVISOR);
        assertThat(model.routing()).isInstanceOf(FirstMatchRouting.class);
        assertThat(model.aggregation()).isInstanceOf(CollectAll.class);
        assertThat(model.failurePolicy().onDeadlock())
                .isEqualTo(FailurePolicy.AggregationFailureAction.ESCALATE);

        var candidates = model.candidateSupplier().get();
        assertThat(candidates).hasSize(4);
        assertThat(candidates.get(0).descriptor().name()).isEqualTo("anomaly-detector");
        assertThat(candidates.get(1).descriptor().name()).isEqualTo("triage-coordinator");
        assertThat(candidates.get(2).descriptor().name()).isEqualTo("response-planner");
        assertThat(candidates.get(3).descriptor().name()).isEqualTo("resolution-tracker");

        assertThat(model.judgment()).isNotNull();
        assertThat(model.judgment()).isInstanceOf(JudgmentPolicy.class);
    }

    @Test
    void cognitionCompiles() throws IOException {
        var def = loadCognition(SCENARIO);
        var compiled = cognitionCompiler.compile(def);

        assertThat(compiled.drive().axisWeights().get(DriveAxis.COMPETENCE)).isEqualTo(1.8);
        assertThat(compiled.userModel().minSignalsForSynthesis()).isEqualTo(5);
        assertThat(compiled.goalProposal().maxDriveGoals()).isEqualTo(3);
        assertThat(compiled.goalEscalation().escalationCycles()).isEqualTo(3);
        assertThat(compiled.normDetection().establishedThreshold()).isEqualTo(0.8);
        assertThat(compiled.collectiveGoal().alignmentThreshold()).isEqualTo(0.7);
        assertThat(compiled.narrative().maxEpisodes()).isEqualTo(80);
    }

    @Test
    void negotiationCompiles() throws IOException {
        var spec = load(SCENARIO, "negotiation.yaml", NegotiationSpec.class);
        var compiler = new NegotiationCompiler(new TerminationConditionRegistry(), null);
        var compiled = compiler.compile(spec);

        assertThat(compiled.parties()).containsExactlyInAnyOrder(
                "response-planner", "triage-coordinator", "resolution-tracker");
        assertThat(compiled.projection()).isNotNull();
        assertThat(compiled.termination()).isNotNull();
    }

    @Test
    void conflictResolutionResolves() throws IOException {
        var spec = load(SCENARIO, "conflict-resolution.yaml", ConflictResolutionSpec.class);
        var registry = new ConflictResolutionRegistry();
        var strategy = registry.resolve(spec);

        assertThat(strategy).isInstanceOf(MostRestrictiveResolution.class);
    }

    @Test
    void pipelineExecutesDetectionThroughResponse() throws IOException {
        var execRegistry = executionRegistry();
        execRegistry.register("field-extract", config ->
                Summariser.ofSync(batch -> batch.stream()
                        .map(e -> Map.of("extracted", e.payload()))
                        .toList()));

        var def = loadPipeline(SCENARIO);
        var runtimeEngine = runtimeMvelEngine();
        var pipeline = pipelineCompiler.<Map<String, Object>>compile(def, execRegistry, null, runtimeEngine);

        var detectionOut = new ArrayList<LevelEvent<?>>();
        var triageOut = new ArrayList<LevelEvent<?>>();
        var responseOut = new ArrayList<LevelEvent<?>>();
        var resolutionOut = new ArrayList<LevelEvent<?>>();
        pipeline.outputBus("detection").subscribe(e -> true, detectionOut::add);
        pipeline.outputBus("triage").subscribe(e -> true, triageOut::add);
        pipeline.outputBus("response").subscribe(e -> true, responseOut::add);
        pipeline.outputBus("resolution").subscribe(e -> true, resolutionOut::add);

        var input = new EventLevel("input", 0);

        pipeline.inputBus().publish(new LevelEvent<>(Map.of("warehouseId", (Object) "wh-1", "weightDelta", 2.0, "temperature", 22.0, "delayHours", 1, "missingScan", false), 1000L, input, "tenant-1"));
        pipeline.inputBus().publish(new LevelEvent<>(Map.of("warehouseId", (Object) "wh-1", "weightDelta", 8.5, "temperature", 35.0, "delayHours", 6, "missingScan", false), 2000L, input, "tenant-1"));
        pipeline.inputBus().publish(new LevelEvent<>(Map.of("warehouseId", (Object) "wh-1", "weightDelta", 1.0, "temperature", 28.0, "delayHours", 0, "missingScan", true), 3000L, input, "tenant-1"));

        for (int i = 0; i < 10; i++) {
            pipeline.inputBus().publish(new LevelEvent<>(Map.of("warehouseId", (Object) "wh-1", "weightDelta", 8.0 + i, "temperature", 32.0, "delayHours", 5, "missingScan", false), (4000L + i * 100), input, "tenant-1"));
        }

        pipeline.tick(6000L).toCompletableFuture().join();
        pipeline.tick(7000L).toCompletableFuture().join();
        pipeline.flush().toCompletableFuture().join();

        System.out.println("=== Supply Chain Pipeline Execution ===");
        System.out.println("L1 detection events:   " + detectionOut.size());
        detectionOut.forEach(e -> System.out.println("  → " + e.payload()));
        System.out.println("L2 triage events:      " + triageOut.size());
        triageOut.forEach(e -> System.out.println("  → " + e.payload()));
        System.out.println("L3 response events:    " + responseOut.size());
        responseOut.forEach(e -> System.out.println("  → " + e.payload()));
        System.out.println("L4 resolution events:  " + resolutionOut.size());
        resolutionOut.forEach(e -> System.out.println("  → " + e.payload()));

        assertThat(detectionOut).as("L1 detection should classify anomalies").isNotEmpty();
    }

    @Test
    void negotiationExecutesProposalToAgreement() throws IOException {
        var spec = load(SCENARIO, "negotiation.yaml", NegotiationSpec.class);
        var compiler = new NegotiationCompiler(new TerminationConditionRegistry(), null);
        var compiled = compiler.compile(spec);

        var projection = compiled.projection();
        var state = projection.identity();

        var channelId = UUID.randomUUID();
        var t = Instant.parse("2026-01-15T10:00:00Z");

        state = projection.apply(state, new MessageView(1L, channelId, "response-planner", MessageType.PROPOSE, "Reroute via warehouse-3, ETA 4h", null, "p1", null, null, null, List.of(), ActorType.AGENT, t, null, 0));

        System.out.println("=== Supply Chain Negotiation ===");
        System.out.println("After proposal: " + state.outcome() + " — round " + state.round());
        System.out.println("  Proposal: " + state.activeProposal().content());

        state = projection.apply(state, new MessageView(2L, channelId, "triage-coordinator", MessageType.DONE, null, null, "p1", null, null, null, List.of(), ActorType.AGENT, t.plusSeconds(30), null, 0));

        System.out.println("After triage-coordinator accepts: " + state.outcome());
        System.out.println("  Responses so far: " + state.responses().size());

        state = projection.apply(state, new MessageView(3L, channelId, "resolution-tracker", MessageType.DONE, null, null, "p1", null, null, null, List.of(), ActorType.AGENT, t.plusSeconds(60), null, 0));

        System.out.println("After resolution-tracker accepts: " + state.outcome());

        assertThat(state.outcome()).as("All non-proposer parties accepted")
                .isEqualTo(NegotiationOutcome.AGREED);
        System.out.println("  → Negotiation resolved: " + state.outcome());
    }
}
