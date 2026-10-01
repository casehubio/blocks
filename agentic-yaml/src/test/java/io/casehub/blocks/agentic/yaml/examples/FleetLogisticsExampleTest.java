package io.casehub.blocks.agentic.yaml.examples;

import io.casehub.blocks.agentic.FailurePolicy;
import io.casehub.blocks.agentic.aggregation.CollectAll;
import io.casehub.blocks.agentic.judgment.JudgmentPolicy;
import io.casehub.blocks.agentic.model.PatternType;
import io.casehub.blocks.agentic.routing.FirstMatchRouting;
import io.casehub.neocortex.cognition.drive.DriveAxis;
import io.casehub.neocortex.cognition.drive.DriveConfig;
import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.LevelEvent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FleetLogisticsExampleTest extends ExampleTestBase {

    private static final String SCENARIO = "fleet-logistics";

    @Test
    void pipelineCompiles() throws IOException {
        var def = loadPipeline(SCENARIO);
        var pipeline = pipelineCompiler.<Object>compile(def, summariserRegistry, null, pipelineExpressionEngine);

        assertThat(pipeline.name()).isEqualTo("fleet-monitoring");
        assertThat(def.levels()).hasSize(4);
        assertThat(def.levels().get(0).name()).isEqualTo("per-vehicle");
        assertThat(def.levels().get(1).name()).isEqualTo("per-route");
        assertThat(def.levels().get(2).name()).isEqualTo("route-health");
        assertThat(def.levels().get(3).name()).isEqualTo("fleet-status");
    }

    @Test
    void patternCompiles() throws IOException {
        var spec = loadPattern(SCENARIO);
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
        assertThat(candidates.get(0).descriptor().name()).isEqualTo("route-analyst");
        assertThat(candidates.get(1).descriptor().name()).isEqualTo("dispatcher");
        assertThat(candidates.get(2).descriptor().name()).isEqualTo("reporter");
        assertThat(candidates.get(0).descriptor().capabilities()).hasSize(2);

        assertThat(model.judgment()).isNotNull();
        assertThat(model.judgment()).isInstanceOf(JudgmentPolicy.class);
    }

    @Test
    void pipelineExecutesWithEvents() throws IOException {
        var def = loadPipeline(SCENARIO);
        var runtimeEngine = runtimeMvelEngine();
        var pipeline = pipelineCompiler.<Map<String, Object>>compile(def, executionRegistry(), null, runtimeEngine);

        var level0Out = new ArrayList<LevelEvent<?>>();
        var level1Out = new ArrayList<LevelEvent<?>>();
        var level2Out = new ArrayList<LevelEvent<?>>();
        var level3Out = new ArrayList<LevelEvent<?>>();
        pipeline.outputBus("per-vehicle").subscribe(e -> true, level0Out::add);
        pipeline.outputBus("per-route").subscribe(e -> true, level1Out::add);
        pipeline.outputBus("route-health").subscribe(e -> true, level2Out::add);
        pipeline.outputBus("fleet-status").subscribe(e -> true, level3Out::add);

        var input = new EventLevel("input", 0);

        pipeline.inputBus().publish(new LevelEvent<>(Map.of("vehicleId", (Object) "truck-1", "routeId", "route-A", "delayMinutes", 5), 1000L, input, "tenant-1"));
        pipeline.inputBus().publish(new LevelEvent<>(Map.of("vehicleId", (Object) "truck-1", "routeId", "route-A", "delayMinutes", 25), 2000L, input, "tenant-1"));
        pipeline.inputBus().publish(new LevelEvent<>(Map.of("vehicleId", (Object) "truck-2", "routeId", "route-A", "delayMinutes", 70, "missedDelivery", true), 3000L, input, "tenant-1"));

        pipeline.tick(4000L).toCompletableFuture().join();
        pipeline.tick(5000L).toCompletableFuture().join();
        pipeline.flush().toCompletableFuture().join();

        System.out.println("=== Fleet Logistics Pipeline Execution ===");
        System.out.println("L1 per-vehicle events: " + level0Out.size());
        level0Out.forEach(e -> System.out.println("  → " + e.payload()));
        System.out.println("L2 per-route events:   " + level1Out.size());
        level1Out.forEach(e -> System.out.println("  → " + e.payload()));
        System.out.println("L3 route-health events: " + level2Out.size());
        level2Out.forEach(e -> System.out.println("  → " + e.payload()));
        System.out.println("L4 fleet-status events: " + level3Out.size());
        level3Out.forEach(e -> System.out.println("  → " + e.payload()));

        assertThat(level0Out).as("L1 per-vehicle should produce output").isNotEmpty();
    }

    @Test
    void cognitionCompiles() throws IOException {
        var def = loadCognition(SCENARIO);
        var compiled = cognitionCompiler.compile(def);

        assertThat(compiled.drive().axisWeights().get(DriveAxis.COMPETENCE)).isEqualTo(1.8);
        assertThat(compiled.drive().changeThreshold()).isEqualTo(0.12);
        assertThat(compiled.mood().maxDisplacement()).isEqualTo(0.5);
        assertThat(compiled.mood().baseline().pleasure()).isEqualTo(0.4);
        assertThat(compiled.narrative().maxEpisodes()).isEqualTo(75);
        assertThat(compiled.retention().recencyWeight()).isEqualTo(0.7);
    }
}
