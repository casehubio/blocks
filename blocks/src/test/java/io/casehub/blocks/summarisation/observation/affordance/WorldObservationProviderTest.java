package io.casehub.blocks.summarisation.observation.affordance;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorldObservationProviderTest {

    @Test
    void lambdaImplementationReturnsSections() {
        WorldObservationProvider provider = () -> List.of(
                ObservationSection.text("Location", "A dusty hallway."),
                ObservationSection.entities("Objects", "Nothing here.", List.of())
        );

        List<ObservationSection> sections = provider.worldSections();

        assertThat(sections).hasSize(2);
        assertThat(sections.get(0)).isInstanceOf(ObservationSection.TextBlock.class);
        assertThat(sections.get(1)).isInstanceOf(ObservationSection.EntityGroup.class);
    }

    @Test
    void emptyProviderReturnsEmptyList() {
        WorldObservationProvider provider = List::of;

        assertThat(provider.worldSections()).isEmpty();
    }
}
