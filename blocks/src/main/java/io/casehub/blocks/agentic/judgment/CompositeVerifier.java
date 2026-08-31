package io.casehub.blocks.agentic.judgment;

import io.casehub.api.spi.judgment.JudgmentVerifier;
import io.casehub.api.spi.judgment.VerificationContext;
import io.casehub.api.spi.judgment.VerificationResult;

import java.util.Arrays;
import java.util.List;

public final class CompositeVerifier implements JudgmentVerifier {

    private final List<VerifierEntry> entries;

    private CompositeVerifier(List<VerifierEntry> entries) {
        this.entries = List.copyOf(entries);
    }

    public static CompositeVerifier of(JudgmentVerifier... verifiers) {
        return new CompositeVerifier(
                Arrays.stream(verifiers)
                      .map(v -> new VerifierEntry(v, VerifierFailurePolicy.RETRY_WITH_FEEDBACK))
                      .toList());
    }

    public static CompositeVerifier withPolicies(VerifierEntry... entries) {
        return new CompositeVerifier(List.of(entries));
    }

    public List<VerifierEntry> entries() { return entries; }

    @Override
    public String id() { return "composite"; }

    @Override
    public VerificationResult verify(VerificationContext context) {
        for (var entry : entries) {
            var result = entry.verifier().verify(context);
            if (!(result instanceof VerificationResult.Accepted)) {
                return result;
            }
        }
        return new VerificationResult.Accepted();
    }

    public VerifierFailurePolicy mostRestrictivePolicy() {
        var worst = VerifierFailurePolicy.RETRY_WITH_FEEDBACK;
        for (var entry : entries) {
            if (entry.failurePolicy().compareTo(worst) > 0) {
                worst = entry.failurePolicy();
            }
        }
        return worst;
    }

    public record VerifierEntry(JudgmentVerifier verifier, VerifierFailurePolicy failurePolicy) {}
}
