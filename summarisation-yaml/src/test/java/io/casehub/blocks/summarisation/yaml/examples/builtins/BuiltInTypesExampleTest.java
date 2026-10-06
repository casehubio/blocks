package io.casehub.blocks.summarisation.yaml.examples.builtins;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.jackson.YamlMappers;
import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.Summariser;
import io.casehub.blocks.summarisation.SummarisationRunner;
import io.casehub.blocks.summarisation.WindowPolicy;
import io.casehub.blocks.summarisation.yaml.CompiledPipeline;
import io.casehub.blocks.summarisation.yaml.PipelineCompiler;
import io.casehub.blocks.summarisation.yaml.PipelineWrapper;
import io.casehub.blocks.summarisation.yaml.SummariserFactory;
import io.casehub.blocks.summarisation.yaml.SummariserRegistry;
import io.casehub.blocks.summarisation.yaml.builtin.CountSummariser;
import io.casehub.blocks.summarisation.yaml.builtin.FieldExtractSummariser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demonstrates the remaining built-in summariser types:
 * - pass-through: identity rebatching (level exists for grouping, not transformation)
 * - count: per-category frequency counts within a batch
 * - field-extract: JQ-style document transformation (Tier 1 only)
 */
class BuiltInTypesExampleTest {

    static final EventLevel INPUT = new EventLevel("input", 0);
    static final EventLevel OUTPUT = new EventLevel("output", 1);
    static final ObjectMapper YAML = YamlMappers.create();
    static final ObjectMapper JSON = new ObjectMapper();

    /**
     * pass-through: events flow through unchanged. Useful when a level
     * exists purely for windowed grouping or CloudEvent emission.
     */
    @Test
    void passThrough_rebatchesWithoutTransformation() throws Exception {
        var def = YAML.readValue(
                getClass().getResourceAsStream("/META-INF/summarisation/sensor-monitoring.yaml"),
                PipelineWrapper.class).pipeline();

        var registry = createRegistry();
        var pipeline = new PipelineCompiler().compile(def, registry, null);

        var rawOutput = new ArrayList<LevelEvent<?>>();
        pipeline.<Object>outputBus("raw-readings").subscribe(e -> true, rawOutput::add);

        publishReading(pipeline, "TEMPERATURE", 22.5);
        publishReading(pipeline, "HUMIDITY", 65.0);
        publishReading(pipeline, "TEMPERATURE", 23.1);
        publishReading(pipeline, "PRESSURE", 1013.0);
        publishReading(pipeline, "TEMPERATURE", 22.8);

        pipeline.tick(1000L).toCompletableFuture().join();

        assertThat(rawOutput).hasSize(5);
        assertThat(rawOutput.get(0).payload()).isInstanceOfSatisfying(Map.class, m ->
                assertThat(m).containsEntry("category", "TEMPERATURE"));
    }

    /**
     * count: produces per-category frequency counts from a batch.
     * Output: single Map where keys are category values and values are counts.
     */
    @Test
    void count_producesCategoryFrequencies() throws Exception {
        var def = YAML.readValue(
                getClass().getResourceAsStream("/META-INF/summarisation/sensor-monitoring.yaml"),
                PipelineWrapper.class).pipeline();

        var registry = createRegistry();
        var pipeline = new PipelineCompiler().compile(def, registry, null);

        var countOutput = new ArrayList<LevelEvent<?>>();
        pipeline.<Object>outputBus("category-counts").subscribe(e -> true, countOutput::add);

        publishReading(pipeline, "TEMPERATURE", 22.5);
        publishReading(pipeline, "HUMIDITY", 65.0);
        publishReading(pipeline, "TEMPERATURE", 23.1);
        publishReading(pipeline, "PRESSURE", 1013.0);
        publishReading(pipeline, "TEMPERATURE", 22.8);

        pipeline.tick(1000L).toCompletableFuture().join();
        pipeline.tick(2000L).toCompletableFuture().join();

        assertThat(countOutput).hasSizeGreaterThanOrEqualTo(1);
        assertThat(countOutput.get(0).payload()).isInstanceOfSatisfying(Map.class, m -> {
            assertThat(m).containsEntry("TEMPERATURE", 3);
            assertThat(m).containsEntry("HUMIDITY", 1);
            assertThat(m).containsEntry("PRESSURE", 1);
        });
    }

    /**
     * field-extract: extracts nested fields from Map payloads via a pluggable
     * JQ-style evaluator. Tier 1 only (Map payloads).
     */
    @Test
    void fieldExtract_extractsNestedDocumentFields() {
        Function<String, Function<JsonNode, List<JsonNode>>> jqCompiler = expression -> node -> {
            var fieldName = expression.startsWith(".") ? expression.substring(1) : expression;
            var field = node.get(fieldName);
            return field != null ? List.of(field) : List.of();
        };

        var summariser = FieldExtractSummariser.create(
                Map.of("expression", ".metrics"), jqCompiler);

        var outputBus = new EventStreamBus<Map<String, Object>>();
        var results = new ArrayList<Map<String, Object>>();
        outputBus.subscribe(e -> true, e -> results.add(e.payload()));

        var runner = new SummarisationRunner<>(
                WindowPolicy.ofCount(2), summariser, outputBus, OUTPUT);

        runner.collect(new LevelEvent<>(
                Map.<String, Object>of("sensorId", "S1",
                        "metrics", Map.of("temperature", 22.5, "humidity", 65)),
                100L, INPUT, null));
        runner.collect(new LevelEvent<>(
                Map.<String, Object>of("sensorId", "S2",
                        "metrics", Map.of("temperature", 23.0, "pressure", 1013)),
                200L, INPUT, null));

        runner.tick(300L).toCompletableFuture().join();

        assertThat(results).hasSize(2);
        assertThat(results.get(0)).containsEntry("temperature", 22.5);
        assertThat(results.get(1)).containsEntry("pressure", 1013);
    }

    @SuppressWarnings("unchecked")
    private SummariserRegistry createRegistry() {
        var registry = new SummariserRegistry();
        registry.register("count", (SummariserFactory) config -> CountSummariser.create(config));
        return registry;
    }

    @SuppressWarnings("unchecked")
    private void publishReading(CompiledPipeline<?> pipeline,
                                 String category, double value) {
        var bus = (EventStreamBus<Object>) (Object) pipeline.inputBus();
        bus.publish(new LevelEvent<>(
                Map.<String, Object>of("category", category, "value", value),
                System.currentTimeMillis(), INPUT, null));
    }
}
