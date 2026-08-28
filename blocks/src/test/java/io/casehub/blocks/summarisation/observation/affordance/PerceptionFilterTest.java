package io.casehub.blocks.summarisation.observation.affordance;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PerceptionFilterTest {

    private final PerceptionFilter filter = new PerceptionFilter();

    @Test
    void bare_sections_pass_through() {
        var section = ObservationSection.text("Location", "Kitchen");
        var result = filter.filter(List.of(section), Set.of());
        assertThat(result).containsExactly(section);
    }

    @Test
    void annotated_section_passes_when_tags_met() {
        var section = ObservationSection.text("Keen Observations", "Detail");
        var annotated = AnnotatedSection.requiring(section, Set.of("perception"));
        var result = filter.filter(List.of(annotated), Set.of("perception"));
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(annotated);
    }

    @Test
    void annotated_section_removed_when_tags_not_met_and_no_fallback() {
        var section = ObservationSection.text("Keen Observations", "Detail");
        var annotated = AnnotatedSection.requiring(section, Set.of("perception"));
        var result = filter.filter(List.of(annotated), Set.of());
        assertThat(result).isEmpty();
    }

    @Test
    void annotated_section_downgrades_when_tags_not_met_and_fallback_exists() {
        var full = ObservationSection.text("Keen Observations", "X positioned Y carefully");
        var reduced = ObservationSection.text("Directed to You", "X said something");
        var annotated = AnnotatedSection.withResolution(full, Set.of("perception"), ResolutionTier.REDUCED, reduced);
        var result = filter.filter(List.of(annotated), Set.of());
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(reduced);
    }

    @Test
    void empty_required_tags_always_passes() {
        var section = ObservationSection.text("Location", "Kitchen");
        var annotated = AnnotatedSection.requiring(section, Set.of());
        var result = filter.filter(List.of(annotated), Set.of());
        assertThat(result).hasSize(1);
    }

    @Test
    void mixed_list_filters_correctly() {
        var bare = ObservationSection.text("Location", "Kitchen");
        var keen = ObservationSection.text("Keen Observations", "Detail");
        var directed = ObservationSection.text("Directed to You", "Simple");
        var annotated = AnnotatedSection.withResolution(keen, Set.of("perception"), ResolutionTier.REDUCED, directed);
        var result = filter.filter(List.of(bare, annotated), Set.of());
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo(bare);
        assertThat(result.get(1)).isEqualTo(directed);
    }

    @Test
    void multiple_tags_all_required() {
        var section = ObservationSection.text("Secret", "Hidden info");
        var annotated = AnnotatedSection.requiring(section, Set.of("perception", "analysis"));
        assertThat(filter.filter(List.of(annotated), Set.of("perception"))).isEmpty();
        assertThat(filter.filter(List.of(annotated), Set.of("perception", "analysis"))).hasSize(1);
    }
}
