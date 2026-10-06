package io.casehub.blocks.summarisation.yaml.examples.decision;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.jackson.YamlMappers;
import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.yaml.CompiledPipeline;
import io.casehub.blocks.summarisation.yaml.PipelineCompiler;
import io.casehub.blocks.summarisation.yaml.PipelineValidator;
import io.casehub.blocks.summarisation.yaml.PipelineWrapper;
import io.casehub.blocks.summarisation.yaml.SummariserFactory;
import io.casehub.blocks.summarisation.yaml.SummariserRegistry;
import io.casehub.blocks.summarisation.yaml.builtin.CountSummariser;
import io.casehub.blocks.summarisation.yaml.builtin.ThresholdClassifySummariser;
import io.casehub.platform.expression.MvelExpressionEngine;
import io.cloudevents.CloudEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class YamlDecisionNarrativePipelineTest {

    static final EventLevel INPUT = new EventLevel("input", 0);
    static final MvelExpressionEngine EXPR = new MvelExpressionEngine();
    static final ObjectMapper YAML = YamlMappers.create();

    @SuppressWarnings("unchecked")
    private record PipelineFixture(CompiledPipeline<?> pipeline,
                                    List<CloudEvent> emittedCloudEvents) {

        static PipelineFixture fromYaml(String resourcePath) throws Exception {
            var yaml = YamlDecisionNarrativePipelineTest.class.getResourceAsStream(resourcePath);
            var definition = YAML.readValue(yaml, PipelineWrapper.class).pipeline();

            var errors = new PipelineValidator().validate(definition, createRegistry(definition));
            var realErrors = errors.stream()
                    .filter(e -> e.level() == PipelineValidator.ValidationError.Level.ERROR)
                    .toList();
            assertThat(realErrors).as("YAML validation").isEmpty();

            var emitted = new ArrayList<CloudEvent>();
            var pipeline = new PipelineCompiler().compile(definition,
                    createRegistry(definition), emitted::add, EXPR);
            return new PipelineFixture(pipeline, emitted);
        }

        private static SummariserRegistry createRegistry(
                io.casehub.blocks.summarisation.yaml.PipelineDefinition def) {
            var registry = new SummariserRegistry();
            registry.register("threshold-classify", (SummariserFactory)
                    config -> ThresholdClassifySummariser.create(config, EXPR));
            registry.register("count", (SummariserFactory)
                    config -> CountSummariser.create(config));
            return registry;
        }
    }

    @Test
    void yamlPipeline_classifiesRoutingAndStepOutcome() throws Exception {
        var fixture = PipelineFixture.fromYaml("/META-INF/summarisation/decision-narrative.yaml");

        var stepSummaries = new ArrayList<LevelEvent<?>>();
        fixture.pipeline().<Object>outputBus("step-signals").subscribe(e -> true, stepSummaries::add);

        publishSignal(fixture.pipeline(), "case-1", "route-analyst",
                "routing", Map.of("selectedAgent", "analyst-3", "score", 0.87, "passed", false, "status", ""), null);
        publishSignal(fixture.pipeline(), "case-1", "route-analyst",
                "step_outcome", Map.of("status", "COMPLETED", "passed", false), null);

        fixture.pipeline().tick(1000L).toCompletableFuture().join();

        assertThat(stepSummaries).hasSize(2);
        assertThat(stepSummaries.get(0).payload()).isInstanceOfSatisfying(Map.class, m ->
                assertThat(m).containsEntry("category", "ROUTING"));
        assertThat(stepSummaries.get(1).payload()).isInstanceOfSatisfying(Map.class, m ->
                assertThat(m).containsEntry("category", "STEP_COMPLETED"));
    }

    @Test
    void yamlPipeline_trustFailure_classifiedViaStaleTimeout() throws Exception {
        var fixture = PipelineFixture.fromYaml("/META-INF/summarisation/decision-narrative.yaml");

        var stepSummaries = new ArrayList<LevelEvent<?>>();
        fixture.pipeline().<Object>outputBus("step-signals").subscribe(e -> true, stepSummaries::add);

        publishSignal(fixture.pipeline(), "case-1", "step1",
                "trust", Map.of("passed", false, "status", ""), null);

        fixture.pipeline().tick(System.currentTimeMillis() + 31_000L).toCompletableFuture().join();

        assertThat(stepSummaries).hasSize(1);
        assertThat(stepSummaries.get(0).payload()).isInstanceOfSatisfying(Map.class, m -> {
            assertThat(m).containsEntry("category", "TRUST_FAILED");
            assertThat(m).containsEntry("severity", "HIGH");
        });
    }

    @Test
    void yamlPipeline_caseLevel_countsCategoriesAcrossSteps() throws Exception {
        var fixture = PipelineFixture.fromYaml("/META-INF/summarisation/decision-narrative.yaml");

        var narratives = new ArrayList<LevelEvent<?>>();
        fixture.pipeline().<Object>outputBus("case-narrative").subscribe(e -> true, narratives::add);

        publishSignal(fixture.pipeline(), "case-1", "step1",
                "routing", Map.of("passed", false, "status", ""), null);
        publishSignal(fixture.pipeline(), "case-1", "step1",
                "step_outcome", Map.of("status", "COMPLETED", "passed", false), null);

        fixture.pipeline().tick(1000L).toCompletableFuture().join();
        fixture.pipeline().tick(1001L).toCompletableFuture().join();

        assertThat(narratives).hasSizeGreaterThanOrEqualTo(1);
        assertThat(narratives.get(0).payload()).isInstanceOfSatisfying(Map.class, m ->
                assertThat(m).containsEntry("ROUTING", 1).containsEntry("STEP_COMPLETED", 1));
    }

    @Test
    void yamlPipeline_staleTimeout_emitsIncompleteGroup() throws Exception {
        var fixture = PipelineFixture.fromYaml("/META-INF/summarisation/decision-narrative.yaml");

        var stepSummaries = new ArrayList<LevelEvent<?>>();
        fixture.pipeline().<Object>outputBus("step-signals").subscribe(e -> true, stepSummaries::add);

        publishSignal(fixture.pipeline(), "case-1", "step1",
                "routing", Map.of("passed", false, "status", ""), "tenant-A");

        fixture.pipeline().tick(System.currentTimeMillis() + 31_000L).toCompletableFuture().join();

        assertThat(stepSummaries).hasSize(1);
        assertThat(stepSummaries.get(0).tenancyId()).isEqualTo("tenant-A");
    }

    @Test
    void yamlPipeline_emitsCloudEvents() throws Exception {
        var fixture = PipelineFixture.fromYaml("/META-INF/summarisation/decision-narrative.yaml");

        publishSignal(fixture.pipeline(), "case-1", "step1",
                "step_outcome", Map.of("status", "COMPLETED", "passed", false), "tenant-1");

        fixture.pipeline().tick(1000L).toCompletableFuture().join();

        assertThat(fixture.emittedCloudEvents()).hasSizeGreaterThanOrEqualTo(1);
        assertThat(fixture.emittedCloudEvents().get(0).getType())
                .isEqualTo("io.casehub.decision.step-summary.v1");
    }

    @SuppressWarnings("unchecked")
    private void publishSignal(CompiledPipeline<?> pipeline, String caseId,
                                String stepName, String signalType,
                                Map<String, Object> extraFields, String tenancyId) {
        var bus = (EventStreamBus<Object>) (Object) pipeline.inputBus();
        var fields = new java.util.HashMap<String, Object>();
        fields.put("caseId", caseId);
        fields.put("stepName", stepName);
        fields.put("signalType", signalType);
        fields.putAll(extraFields);
        bus.publish(new LevelEvent<>(fields, System.currentTimeMillis(), INPUT, tenancyId));
    }
}
