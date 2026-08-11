package io.casehub.blocks.trust;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntakeClassifierTest {

    record TestSubject(String id, double trustScore) {}

    @Test
    void classifierReturnsResultForSubject() {
        IntakeClassifier<TestSubject> classifier = (subject, ctx) ->
                new IntakeResult(
                        subject.trustScore() >= 0.75 ? "FAST_TRACK" : "STANDARD",
                        subject.trustScore(),
                        "trust-based classification",
                        Map.of("trustScore", subject.trustScore()));

        var result = classifier.classify(
                new TestSubject("sub-1", 0.85),
                new IntakeContext("tenant-1"));

        assertThat(result.lane()).isEqualTo("FAST_TRACK");
        assertThat(result.confidence()).isEqualTo(0.85);
        assertThat(result.reason()).isEqualTo("trust-based classification");
        assertThat(result.metadata()).containsEntry("trustScore", 0.85);
    }

    @Test
    void intakeContextCompactConstructorDefaultsAttributes() {
        var ctx = new IntakeContext("tenant-1");
        assertThat(ctx.tenancyId()).isEqualTo("tenant-1");
        assertThat(ctx.capabilityTag()).isNull();
        assertThat(ctx.attributes()).isEmpty();
    }

    @Test
    void intakeResultCompactConstructorDefaultsMetadata() {
        var result = new IntakeResult("TRIAGE", 0.3, "low trust");
        assertThat(result.metadata()).isEmpty();
    }

    @Test
    void intakeResultRejectsConfidenceOutOfRange() {
        assertThatThrownBy(() -> new IntakeResult("FAST_TRACK", 1.5, "bad"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IntakeResult("FAST_TRACK", -0.1, "bad"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void intakeContextPassesAttributesToClassifier() {
        IntakeClassifier<String> classifier = (subject, ctx) -> {
            double score = (double) ctx.attributes().get("trustScore");
            return new IntakeResult(score > 0.5 ? "STANDARD" : "TRIAGE", score, "from attributes");
        };

        var ctx = new IntakeContext("tenant-1", "routing", Map.of("trustScore", 0.6));
        var result = classifier.classify("subject-1", ctx);

        assertThat(result.lane()).isEqualTo("STANDARD");
    }
}
