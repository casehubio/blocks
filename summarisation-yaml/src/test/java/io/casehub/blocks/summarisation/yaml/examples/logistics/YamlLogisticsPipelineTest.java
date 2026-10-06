package io.casehub.blocks.summarisation.yaml.examples.logistics;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.jackson.YamlMappers;
import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.yaml.CompiledPipeline;
import io.casehub.platform.api.expression.ExpressionEngine;
import io.casehub.blocks.summarisation.yaml.LevelDefinition;
import io.casehub.blocks.summarisation.yaml.PipelineCompiler;
import io.casehub.blocks.summarisation.yaml.PipelineValidator;
import io.casehub.blocks.summarisation.yaml.PipelineWrapper;
import io.casehub.blocks.summarisation.yaml.SummariserFactory;
import io.casehub.blocks.summarisation.yaml.SummariserRegistry;
import io.casehub.platform.api.expression.ExpressionEngine;
import io.casehub.platform.expression.MvelExpressionEngine;
import io.casehub.blocks.summarisation.yaml.builtin.CountSummariser;
import io.casehub.blocks.summarisation.yaml.builtin.PhaseDetectSummariser;
import io.casehub.blocks.summarisation.yaml.builtin.ThresholdClassifySummariser;
import io.cloudevents.CloudEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demonstrates the YAML pipeline surface (Tier 1) — a logistics hub monitoring
 * pipeline defined entirely in YAML.
 *
 * Pipeline: package scans → anomaly classification (threshold-classify)
 *                         → phase detection (phase-detect with state machine)
 *
 * Compare with the Tier 2 Java API example in
 * blocks/src/test/java/.../examples/logistics/LogisticsPipelineTest.java
 * — same domain, declarative vs programmatic.
 */
class YamlLogisticsPipelineTest {

    static final EventLevel INPUT = new EventLevel("input", 0);
    static final ExpressionEngine EXPR = new MvelExpressionEngine();
    static final ObjectMapper YAML = YamlMappers.create();

    @SuppressWarnings("unchecked")
    private record PipelineFixture(CompiledPipeline<?> pipeline,
                                    List<CloudEvent> emittedCloudEvents) {

        static PipelineFixture fromYaml(String resourcePath) throws Exception {
            var yaml = YamlLogisticsPipelineTest.class.getResourceAsStream(resourcePath);
            var definition = YAML.readValue(yaml, PipelineWrapper.class).pipeline();

            var errors = new PipelineValidator().validate(definition, createRegistry(definition));
            var realErrors = errors.stream()
                    .filter(e -> e.level() == PipelineValidator.ValidationError.Level.ERROR)
                    .toList();
            assertThat(realErrors).as("YAML validation").isEmpty();

            var emitted = new ArrayList<CloudEvent>();
            var pipeline = new PipelineCompiler().compile(definition,
                    createRegistry(definition), emitted::add);
            return new PipelineFixture(pipeline, emitted);
        }

        private static SummariserRegistry createRegistry(
                io.casehub.blocks.summarisation.yaml.PipelineDefinition def) {
            var registry = new SummariserRegistry();
            registry.register("threshold-classify", (SummariserFactory)
                    config -> ThresholdClassifySummariser.create(config, EXPR));
            registry.register("phase-detect", (SummariserFactory) config -> {
                var aggFields = def.levels().stream()
                        .filter(l -> l.summariser().type().equals("phase-detect"))
                        .findFirst()
                        .map(LevelDefinition::aggregateFields)
                        .orElse(List.of());
                return PhaseDetectSummariser.create(config, aggFields);
            });
            registry.register("count", (SummariserFactory) config -> CountSummariser.create(config));
            return registry;
        }
    }

    @Test
    void yamlPipeline_classifiesAnomalies_emitsCloudEvents() throws Exception {
        var fixture = PipelineFixture.fromYaml("/META-INF/summarisation/logistics-hub.yaml");

        var anomalies = new ArrayList<LevelEvent<?>>();
        fixture.pipeline().<Object>outputBus("anomalies").subscribe(e -> true, anomalies::add);

        publishScans(fixture.pipeline(), 10, 55.0, false, "tenant-1");
        fixture.pipeline().tick(1000L).toCompletableFuture().join();

        assertThat(anomalies).hasSize(10);
        assertThat(anomalies.get(0).payload()).isInstanceOfSatisfying(Map.class, m ->
                assertThat(m).containsEntry("category", "WEIGHT_MISMATCH")
                        .containsEntry("severity", "HIGH"));

        assertThat(fixture.emittedCloudEvents()).hasSize(10);
        assertThat(fixture.emittedCloudEvents().get(0).getType())
                .isEqualTo("io.casehub.logistics.anomaly.v1");
        assertThat(fixture.emittedCloudEvents().get(0).getExtension("tenancyid"))
                .isEqualTo("tenant-1");
    }

    @Test
    void yamlPipeline_detectsPhaseTransition() throws Exception {
        var fixture = PipelineFixture.fromYaml("/META-INF/summarisation/logistics-hub.yaml");

        var phases = new ArrayList<LevelEvent<?>>();
        fixture.pipeline().<Object>outputBus("phases").subscribe(e -> true, phases::add);

        publishScans(fixture.pipeline(), 10, 55.0, false, null);
        fixture.pipeline().tick(1000L).toCompletableFuture().join();
        fixture.pipeline().tick(400_000L).toCompletableFuture().join();

        assertThat(phases).hasSizeGreaterThanOrEqualTo(1);
        assertThat(phases.get(0).payload()).isInstanceOfSatisfying(Map.class, m -> {
            assertThat(m).containsEntry("from", "NORMAL_FLOW");
            assertThat(m).containsEntry("to", "CONGESTION");
            assertThat(m).containsEntry("phase", "CONGESTION");
        });
    }

    @Test
    void yamlPipeline_multiTenantIsolation() throws Exception {
        var fixture = PipelineFixture.fromYaml("/META-INF/summarisation/logistics-hub.yaml");

        var anomalies = new ArrayList<LevelEvent<?>>();
        fixture.pipeline().<Object>outputBus("anomalies").subscribe(e -> true, anomalies::add);

        publishScans(fixture.pipeline(), 10, 55.0, false, "tenant-1");
        publishScans(fixture.pipeline(), 10, 10.0, false, "tenant-2");
        fixture.pipeline().tick(1000L).toCompletableFuture().join();

        assertThat(anomalies.stream().filter(e -> "tenant-1".equals(e.tenancyId())).count())
                .isEqualTo(10);
        assertThat(anomalies.stream().filter(e -> "tenant-2".equals(e.tenancyId())).count())
                .isZero();
    }

    @SuppressWarnings("unchecked")
    private void publishScans(CompiledPipeline<?> pipeline, int count,
                               double weight, boolean fragile, String tenancyId) {
        var bus = (io.casehub.blocks.summarisation.EventStreamBus<Object>)
                (Object) pipeline.inputBus();
        for (int i = 0; i < count; i++) {
            bus.publish(new LevelEvent<>(
                    Map.<String, Object>of("packageId", "PKG-" + i,
                            "weight", weight, "fragile", fragile),
                    System.currentTimeMillis(), INPUT, tenancyId));
        }
    }
}
