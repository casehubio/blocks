package io.casehub.blocks.summarisation.observation.affordance;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AnnotatedSectionTest {

    @Test
    void requiring_creates_visibility_only_annotation() {
        var section = ObservationSection.text("Keen Observations", "X positioned Y carefully");
        var annotated = AnnotatedSection.requiring(section, Set.of("perception"));
        assertThat(annotated.section()).isEqualTo(section);
        assertThat(annotated.requiredTags()).containsExactly("perception");
        assertThat(annotated.resolutions()).isEmpty();
        assertThat(annotated.interpretiveFrame()).isNull();
    }

    @Test
    void withResolution_creates_visibility_plus_fallback() {
        var full = ObservationSection.text("Keen Observations", "X positioned Y carefully");
        var reduced = ObservationSection.text("Directed to You", "X said something to you");
        var annotated = AnnotatedSection.withResolution(full, Set.of("perception"), ResolutionTier.REDUCED, reduced);
        assertThat(annotated.section()).isEqualTo(full);
        assertThat(annotated.requiredTags()).containsExactly("perception");
        assertThat(annotated.resolutions()).containsEntry(ResolutionTier.REDUCED, reduced);
    }

    @Test
    void header_delegates_to_wrapped_section() {
        var section = ObservationSection.text("Location", "Kitchen");
        var annotated = AnnotatedSection.requiring(section, Set.of());
        assertThat(annotated.header()).isEqualTo("Location");
    }

    @Test
    void annotated_section_is_observation_section() {
        var section = ObservationSection.text("Location", "Kitchen");
        var annotated = AnnotatedSection.requiring(section, Set.of());
        assertThat(annotated).isInstanceOf(ObservationSection.class);
    }

    @Test
    void empty_required_tags_creates_passthrough_annotation() {
        var section = ObservationSection.text("Location", "Kitchen");
        var annotated = AnnotatedSection.requiring(section, Set.of());
        assertThat(annotated.requiredTags()).isEmpty();
    }

    @Test
    void renderer_handles_annotated_section() {
        var section = ObservationSection.text("Location", "Kitchen");
        var annotated = AnnotatedSection.requiring(section, Set.of());
        var renderer = new AffordanceRenderer();
        var rendered = renderer.renderObservation(java.util.List.of(annotated));
        assertThat(rendered).contains("== Location ==");
        assertThat(rendered).contains("Kitchen");
    }
}
