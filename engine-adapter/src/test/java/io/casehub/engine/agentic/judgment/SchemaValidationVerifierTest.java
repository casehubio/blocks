package io.casehub.engine.agentic.judgment;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.model.JudgmentTarget;
import io.casehub.api.spi.judgment.VerificationContext;
import io.casehub.api.spi.judgment.VerificationResult;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SchemaValidationVerifierTest {

  private final SchemaValidationVerifier verifier = new SchemaValidationVerifier();

  record ReviewDecision(String outcome, String comment) {}

  @Test
  void id_is_schema_validation() {
    assertThat(verifier.id()).isEqualTo("schema-validation");
  }

  @Test
  void accepts_when_no_resolutionType() {
    var target = JudgmentTarget.builder().prompt("Review").build();
    var ctx = new VerificationContext(
        UUID.randomUUID(), "t1", "b1", target, Map.of(), null, "approve", List.of(), null, null);

    assertThat(verifier.verify(ctx)).isInstanceOf(VerificationResult.Accepted.class);
  }

  @Test
  void accepts_when_decision_matches_type() {
    var target = JudgmentTarget.builder().prompt("Review").resolutionType(ReviewDecision.class).build();
    var ctx = new VerificationContext(
        UUID.randomUUID(), "t1", "b1", target, Map.of(), null,
        "{\"outcome\":\"approve\",\"comment\":\"looks good\"}", List.of(), null, null);

    assertThat(verifier.verify(ctx)).isInstanceOf(VerificationResult.Accepted.class);
  }

  @Test
  void rejects_null_decision() {
    var target = JudgmentTarget.builder().prompt("Review").build();
    var ctx = new VerificationContext(
        UUID.randomUUID(), "t1", "b1", target, Map.of(), null, null, List.of(), null, null);

    assertThat(verifier.verify(ctx)).isInstanceOf(VerificationResult.Rejected.class);
  }

  @Test
  void rejects_when_decision_violates_type() {
    var target = JudgmentTarget.builder().prompt("Review").resolutionType(ReviewDecision.class).build();
    var ctx = new VerificationContext(
        UUID.randomUUID(), "t1", "b1", target, Map.of(), null,
        "not-valid-json-for-record", List.of(), null, null);

    var result = verifier.verify(ctx);
    assertThat(result).isInstanceOf(VerificationResult.InsufficientEvidence.class);
  }
}
