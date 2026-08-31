package io.casehub.blocks.agentic.judgment;

import io.casehub.api.spi.judgment.JudgmentVerifier;
import io.casehub.api.spi.judgment.VerificationContext;
import io.casehub.api.spi.judgment.VerificationResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CompositeVerifierTest {

    private static JudgmentVerifier accepting() {
        return new JudgmentVerifier() {
            @Override public String id() { return "accept"; }
            @Override public VerificationResult verify(VerificationContext ctx) {
                return new VerificationResult.Accepted();
            }
        };
    }

    private static JudgmentVerifier rejecting(String reason) {
        return new JudgmentVerifier() {
            @Override public String id() { return "reject"; }
            @Override public VerificationResult verify(VerificationContext ctx) {
                return new VerificationResult.Rejected(reason);
            }
        };
    }

    private static VerificationContext dummyCtx() {
        return new VerificationContext(null, "", "", null, Map.of(), null, null, List.of(), null, null);
    }

    @Test
    void allAccepted_returnsAccepted() {
        var composite = CompositeVerifier.of(accepting(), accepting());
        assertThat(composite.verify(dummyCtx())).isInstanceOf(VerificationResult.Accepted.class);
    }

    @Test
    void anyRejected_returnsRejected() {
        var composite = CompositeVerifier.of(accepting(), rejecting("bad"));
        var result = composite.verify(dummyCtx());
        assertThat(result).isInstanceOf(VerificationResult.Rejected.class);
        assertThat(((VerificationResult.Rejected) result).reason()).isEqualTo("bad");
    }

    @Test
    void emptyChain_returnsAccepted() {
        var composite = CompositeVerifier.of();
        assertThat(composite.verify(dummyCtx())).isInstanceOf(VerificationResult.Accepted.class);
    }

    @Test
    void mostRestrictivePolicy_returnsHighestSeverity() {
        var composite = CompositeVerifier.withPolicies(
                new CompositeVerifier.VerifierEntry(accepting(), VerifierFailurePolicy.RETRY_WITH_FEEDBACK),
                new CompositeVerifier.VerifierEntry(accepting(), VerifierFailurePolicy.FAIL));
        assertThat(composite.mostRestrictivePolicy()).isEqualTo(VerifierFailurePolicy.FAIL);
    }

    @Test
    void mostRestrictivePolicy_emptyChain_returnsRetry() {
        var composite = CompositeVerifier.of();
        assertThat(composite.mostRestrictivePolicy()).isEqualTo(VerifierFailurePolicy.RETRY_WITH_FEEDBACK);
    }

    @Test
    void noOpVerifier_alwaysAccepts() {
        var noop = new NoOpVerifier();
        assertThat(noop.id()).isEqualTo("noop");
        assertThat(noop.verify(dummyCtx())).isInstanceOf(VerificationResult.Accepted.class);
    }
}
