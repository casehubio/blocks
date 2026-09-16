package io.casehub.blocks.agentic.yaml.spec.cognition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrustEvolutionConfigSpecTest {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    @Test
    void deserializesFromYaml() throws Exception {
        String yaml = """
            scoring:
              decayHalfLifeDays: 30
              negativeDecayMultiplier: 1.5
            consolidation:
              overlayProperty: trust-score
              significantChangeThreshold: 0.15
            levels:
              high: 0.7
              moderate: 0.4
              low: 0.2
            events:
              - actionType: STEAL
                verdict: FLAGGED
                confidence: 0.9
                witnessConfidence: 0.5
              - actionType: GIVE
                verdict: SOUND
                confidence: 0.7
                witnessConfidence: 0.3
            """;
        var spec = YAML.readValue(yaml, TrustEvolutionConfigSpec.class);

        assertThat(spec.events()).hasSize(2);
        assertThat(spec.events().get(0).actionType()).isEqualTo("STEAL");
        assertThat(spec.events().get(0).verdict()).isEqualTo("FLAGGED");
        assertThat(spec.events().get(0).confidence()).isEqualTo(0.9);
        assertThat(spec.events().get(0).witnessConfidence()).isEqualTo(0.5);

        assertThat(spec.scoring().decayHalfLifeDays()).isEqualTo(30);
        assertThat(spec.scoring().negativeDecayMultiplier()).isEqualTo(1.5);

        assertThat(spec.consolidation().overlayProperty()).isEqualTo("trust-score");
        assertThat(spec.consolidation().significantChangeThreshold()).isEqualTo(0.15);

        assertThat(spec.levels().high()).isEqualTo(0.7);
        assertThat(spec.levels().moderate()).isEqualTo(0.4);
        assertThat(spec.levels().low()).isEqualTo(0.2);
    }

    @Test
    void handlesNullOptionalFields() throws Exception {
        String yaml = """
            events:
              - actionType: STEAL
                verdict: FLAGGED
                confidence: 0.9
                witnessConfidence: 0.5
            """;
        var spec = YAML.readValue(yaml, TrustEvolutionConfigSpec.class);

        assertThat(spec.events()).hasSize(1);
        assertThat(spec.scoring()).isNull();
        assertThat(spec.consolidation()).isNull();
        assertThat(spec.levels()).isNull();
    }
}
