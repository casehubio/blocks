package io.casehub.blocks.agentic.yaml.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DescriptorLoaderTest {

    @Test
    void loadsHistoricalEncounterDescriptors() {
        var descriptors = DescriptorLoader.load("historical-encounter");

        assertThat(descriptors).hasSize(3);
        assertThat(descriptors.get(0).name()).isEqualTo("leonardo");
        assertThat(descriptors.get(1).name()).isEqualTo("nikola");
        assertThat(descriptors.get(2).name()).isEqualTo("historian-narrator");
    }

    @Test
    void descriptorsHaveBriefings() {
        var descriptors = DescriptorLoader.load("historical-encounter");

        for (var d : descriptors) {
            assertThat(d.briefing()).as("briefing for %s", d.name()).isNotBlank();
        }
    }

    @Test
    void descriptorsHaveCapabilities() {
        var descriptors = DescriptorLoader.load("historical-encounter");
        var leonardo = descriptors.get(0);

        assertThat(leonardo.capabilities()).isNotEmpty();
        assertThat(leonardo.capabilities().stream()
                .anyMatch(c -> c.name().equals("art-and-design"))).isTrue();
    }
}
