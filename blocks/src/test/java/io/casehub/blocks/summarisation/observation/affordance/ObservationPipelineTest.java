package io.casehub.blocks.summarisation.observation.affordance;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ObservationPipelineTest {

    @Test
    void pipeline_applies_perception_filter() {
        var keen = ObservationSection.text("Keen Observations", "Detail");
        var directed = ObservationSection.text("Directed to You", "Simple");
        var annotated = AnnotatedSection.withResolution(keen, Set.of("perception"), ResolutionTier.REDUCED, directed);

        var pipeline = new ObservationPipeline(new PerceptionFilter());
        var result = pipeline.apply(List.of(annotated), Set.of());
        assertThat(result).hasSize(1);
        assertThat(result.get(0).header()).isEqualTo("Directed to You");
    }

    @Test
    void pipeline_unwraps_annotated_sections_after_filtering() {
        var section = ObservationSection.text("Location", "Kitchen");
        var annotated = AnnotatedSection.requiring(section, Set.of());
        var pipeline = new ObservationPipeline(new PerceptionFilter());
        var result = pipeline.apply(List.of(annotated), Set.of());
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isNotInstanceOf(AnnotatedSection.class);
        assertThat(result.get(0).header()).isEqualTo("Location");
    }

    @Test
    void empty_pipeline_passes_through_and_unwraps() {
        var section = ObservationSection.text("Location", "Kitchen");
        var annotated = AnnotatedSection.requiring(section, Set.of());
        var pipeline = new ObservationPipeline();
        var result = pipeline.apply(List.of(annotated), Set.of());
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isNotInstanceOf(AnnotatedSection.class);
    }

    @Test
    void bare_sections_pass_through_unchanged() {
        var section = ObservationSection.text("Location", "Kitchen");
        var pipeline = new ObservationPipeline(new PerceptionFilter());
        var result = pipeline.apply(List.of(section), Set.of());
        assertThat(result).containsExactly(section);
    }

    @Test
    void multiple_stages_compose() {
        var hidden = ObservationSection.text("Secret", "Top secret");
        var annotatedHidden = AnnotatedSection.requiring(hidden, Set.of("clearance"));
        var visible = ObservationSection.text("Location", "Kitchen");

        // Custom filter that uppercases all TextBlock headers (for testing composition)
        ObservationFilter upperCaseFilter = (sections, tags) -> sections.stream()
                .map(s -> {
                    if (s instanceof ObservationSection.TextBlock tb) {
                        return ObservationSection.text(tb.header().toUpperCase(), tb.content());
                    }
                    return s;
                })
                .toList();

        var pipeline = new ObservationPipeline(new PerceptionFilter(), upperCaseFilter);
        var result = pipeline.apply(List.of(annotatedHidden, visible), Set.of());
        assertThat(result).hasSize(1);
        assertThat(result.get(0).header()).isEqualTo("LOCATION");
    }
}
