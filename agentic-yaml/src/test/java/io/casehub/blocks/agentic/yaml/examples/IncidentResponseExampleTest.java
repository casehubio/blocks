package io.casehub.blocks.agentic.yaml.examples;

import io.casehub.blocks.agentic.aggregation.AuctionAggregation;
import io.casehub.blocks.agentic.aggregation.Bid;
import io.casehub.blocks.agentic.judgment.JudgmentPolicy;
import io.casehub.blocks.agentic.model.PatternType;
import io.casehub.blocks.agentic.routing.FirstMatchRouting;
import io.casehub.neocortex.cognition.drive.DriveAxis;
import io.casehub.blocks.agentic.yaml.compiler.ObservationFilterRegistry;
import io.casehub.blocks.agentic.yaml.compiler.WorldCompiler;
import io.casehub.blocks.agentic.yaml.registry.AggregationStrategyRegistry;
import io.casehub.blocks.agentic.yaml.spec.AggregationSpec;
import io.casehub.blocks.summarisation.Summariser;
import io.casehub.blocks.summarisation.observation.affordance.AnnotatedSection;
import io.casehub.blocks.summarisation.observation.affordance.ObservationFilter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentResponseExampleTest extends ExampleTestBase {

    private static final String SCENARIO = "incident-response";

    @Test
    void yamlCompiles() throws IOException {
        var pattern = loadPattern(SCENARIO);
        var model = patternCompiler.compile(pattern);
        assertThat(model.patternType()).isEqualTo(PatternType.SUPERVISOR);
        assertThat(model.routing()).isInstanceOf(FirstMatchRouting.class);
        assertThat(model.judgment()).isNotNull();
        assertThat(model.judgment()).isInstanceOf(JudgmentPolicy.class);

        var candidates = model.candidateSupplier().get();
        assertThat(candidates).hasSize(3);
        assertThat(candidates.get(0).descriptor().name()).isEqualTo("triage-responder");
        assertThat(candidates.get(1).descriptor().name()).isEqualTo("investigation-lead");
        assertThat(candidates.get(2).descriptor().name()).isEqualTo("mitigation-engineer");

        var cognition = loadCognition(SCENARIO);
        var compiled = cognitionCompiler.compile(cognition);
        assertThat(compiled.drive().axisWeights().get(DriveAxis.AUTONOMY)).isEqualTo(1.7);
        assertThat(compiled.mood().baseline().arousal()).isEqualTo(0.7);
    }

    @Test
    void escapeHatchWiresCustomSummariser() throws IOException {
        summariserRegistry.register("severity-classify", config ->
                Summariser.ofSync(batch -> batch.stream()
                        .map(e -> Map.of("severity", "HIGH", "source", "custom-java"))
                        .toList()));

        var def = loadPipeline(SCENARIO);
        var pipeline = pipelineCompiler.<Object>compile(def, summariserRegistry, null, pipelineExpressionEngine);

        assertThat(pipeline.name()).isEqualTo("incident-monitoring");
        assertThat(def.levels()).hasSize(3);
        assertThat(def.levels().get(1).name()).isEqualTo("severity-timeline");
        assertThat(def.levels().get(1).summariser().type()).isEqualTo("severity-classify");
    }

    @Test
    void escapeHatchWiresCustomAggregation() {
        var registry = new AggregationStrategyRegistry();
        registry.registerFallback(spec -> {
            if (spec instanceof AggregationSpec.Auction) {
                return new AuctionAggregation(
                        (result, round) -> new Bid("on-call", 1.0, round, Instant.now()));
            }
            return null;
        });

        var strategy = registry.resolve(new AggregationSpec.Auction("english"));
        assertThat(strategy).isInstanceOf(AuctionAggregation.class);
    }

    @Test
    void escapeHatchWiresCustomFilter() throws IOException {
        var filterRegistry = new ObservationFilterRegistry();
        filterRegistry.register("perception", () ->
                (sections, observerTags) -> sections.stream()
                        .filter(s -> !(s instanceof AnnotatedSection ann)
                                || observerTags.containsAll(ann.requiredTags()))
                        .toList());

        var customWorldCompiler = new WorldCompiler(filterRegistry);
        var def = loadWorld(SCENARIO);
        var compiled = customWorldCompiler.compile(def);

        assertThat(compiled.actions()).hasSize(5);
        assertThat(compiled.entities()).hasSize(3);
        assertThat(compiled.sections()).hasSize(4);
        assertThat(compiled.sections().get(1)).isInstanceOf(AnnotatedSection.class);
        var restricted = (AnnotatedSection) compiled.sections().get(1);
        assertThat(restricted.requiredTags()).containsExactly("clearance");

        assertThat(compiled.pipeline()).isNotNull();
    }
}
