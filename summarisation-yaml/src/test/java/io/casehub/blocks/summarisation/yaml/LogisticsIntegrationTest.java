package io.casehub.blocks.summarisation.yaml;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.jackson.YamlMappers;
import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.yaml.builtin.CountSummariser;
import io.casehub.blocks.summarisation.yaml.builtin.PhaseDetectSummariser;
import io.casehub.blocks.summarisation.yaml.builtin.ThresholdClassifySummariser;
import io.casehub.platform.api.expression.ExpressionEngine;
import io.casehub.platform.expression.MvelExpressionEngine;
import io.cloudevents.CloudEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class LogisticsIntegrationTest {

    static final ObjectMapper MAPPER = YamlMappers.create();
    static final EventLevel INPUT = new EventLevel("input", 0);
    static final ExpressionEngine EXPR = new MvelExpressionEngine();

    PipelineDefinition definition;
    SummariserRegistry registry;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws IOException {
        var yaml = getClass().getResourceAsStream("/META-INF/summarisation/logistics-hub.yaml");
        definition = MAPPER.readValue(yaml, PipelineWrapper.class).pipeline();

        registry = new SummariserRegistry();
        registry.register("threshold-classify", (SummariserFactory)
                config -> ThresholdClassifySummariser.create(config, EXPR));
        registry.register("phase-detect", (SummariserFactory) config -> {
            var aggregateFields = definition.levels().stream()
                    .filter(l -> l.summariser().type().equals("phase-detect"))
                    .findFirst()
                    .map(LevelDefinition::aggregateFields)
                    .orElse(List.of());
            return PhaseDetectSummariser.create(config, aggregateFields);
        });
        registry.register("count", (SummariserFactory) config -> CountSummariser.create(config));
    }

    @Test
    void yamlParsesCorrectly() {
        assertThat(definition.name()).isEqualTo("logistics-hub");
        assertThat(definition.levels()).hasSize(2);
        assertThat(definition.levels().get(0).name()).isEqualTo("anomalies");
        assertThat(definition.levels().get(1).name()).isEqualTo("phases");
    }

    @Test
    void validationPasses() {
        var errors = new PipelineValidator().validate(definition, registry);
        var realErrors = errors.stream()
                .filter(e -> e.level() == PipelineValidator.ValidationError.Level.ERROR)
                .toList();
        assertThat(realErrors).isEmpty();
    }

    @Test
    void thresholdClassify_producesAnomalies() {
        var emitted = new ArrayList<CloudEvent>();
        var pipeline = new PipelineCompiler().compile(definition, registry, emitted::add);

        var output = new ArrayList<LevelEvent<?>>();
        pipeline.<Object>outputBus("anomalies").subscribe(e -> true, output::add);

        publishScans(pipeline, 10, 55.0, false, "tenant-1");
        pipeline.tick(1000L).toCompletableFuture().join();

        assertThat(output).hasSize(10);
        assertThat(output.get(0).payload()).isInstanceOfSatisfying(Map.class, m ->
                assertThat(m).containsEntry("category", "WEIGHT_MISMATCH"));

        assertThat(emitted).hasSize(10);
        assertThat(emitted.get(0).getType()).isEqualTo("io.casehub.logistics.anomaly.v1");
        assertThat(emitted.get(0).getExtension("tenancyid")).isEqualTo("tenant-1");
    }

    @Test
    void phaseDetect_transitionsOnHighAnomalies() {
        var pipeline = new PipelineCompiler().compile(definition, registry, null);

        var phaseOutput = new ArrayList<LevelEvent<?>>();
        pipeline.<Object>outputBus("phases").subscribe(e -> true, phaseOutput::add);

        publishScans(pipeline, 10, 55.0, false, null);
        pipeline.tick(1000L).toCompletableFuture().join();

        pipeline.tick(400_000L).toCompletableFuture().join();

        assertThat(phaseOutput).hasSizeGreaterThanOrEqualTo(1);
        assertThat(phaseOutput.get(0).payload()).isInstanceOfSatisfying(Map.class, m -> {
            assertThat(m).containsEntry("from", "NORMAL_FLOW");
            assertThat(m).containsEntry("to", "CONGESTION");
        });
    }

    @Test
    void multiTenant_independentState() {
        var pipeline = new PipelineCompiler().compile(definition, registry, null);

        var anomalies = new ArrayList<LevelEvent<?>>();
        pipeline.<Object>outputBus("anomalies").subscribe(e -> true, anomalies::add);

        publishScans(pipeline, 10, 55.0, false, "tenant-1");
        publishScans(pipeline, 10, 10.0, false, "tenant-2");
        pipeline.tick(1000L).toCompletableFuture().join();

        var t1Anomalies = anomalies.stream()
                .filter(e -> "tenant-1".equals(e.tenancyId()))
                .toList();
        var t2Anomalies = anomalies.stream()
                .filter(e -> "tenant-2".equals(e.tenancyId()))
                .toList();

        assertThat(t1Anomalies).hasSize(10);
        assertThat(t2Anomalies).isEmpty();
    }

    @Test
    void flush_drainsAllLevels() {
        var pipeline = new PipelineCompiler().compile(definition, registry, null);

        var anomalies = new ArrayList<LevelEvent<?>>();
        pipeline.<Object>outputBus("anomalies").subscribe(e -> true, anomalies::add);

        publishScans(pipeline, 3, 55.0, false, null);
        pipeline.tick(1000L).toCompletableFuture().join();
        assertThat(anomalies).isEmpty();

        pipeline.flush().toCompletableFuture().join();
        assertThat(anomalies).hasSize(3);
    }

    @SuppressWarnings("unchecked")
    private void publishScans(CompiledPipeline<?> pipeline,
                               int count, double weight, boolean fragile,
                               String tenancyId) {
        var bus = (io.casehub.blocks.summarisation.EventStreamBus<Object>) (Object) pipeline.inputBus();
        for (int i = 0; i < count; i++) {
            var payload = Map.<String, Object>of(
                    "packageId", "PKG-" + i,
                    "weight", weight,
                    "fragile", fragile);
            bus.publish(new LevelEvent<>(payload, System.currentTimeMillis(), INPUT, tenancyId));
        }
    }
}
