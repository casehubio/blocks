package io.casehub.blocks.agentic.judgment;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ConsensusAgreement implements AgreementPolicy {

    private final int requiredAgreement;
    private final ConsensusMode mode;

    public enum ConsensusMode { UNANIMOUS, MAJORITY, THRESHOLD }

    private ConsensusAgreement(int requiredAgreement, ConsensusMode mode) {
        this.requiredAgreement = requiredAgreement;
        this.mode = mode;
    }

    public static ConsensusAgreement unanimous() {
        return new ConsensusAgreement(0, ConsensusMode.UNANIMOUS);
    }

    public static ConsensusAgreement majority() {
        return new ConsensusAgreement(0, ConsensusMode.MAJORITY);
    }

    public static ConsensusAgreement threshold(int required) {
        return new ConsensusAgreement(required, ConsensusMode.THRESHOLD);
    }

    @Override
    public AgreementResult evaluate(List<JudgmentResponse> responses) {
        if (responses.isEmpty()) {
            return new AgreementResult.Disagreed("No responses");
        }

        Map<String, List<JudgmentResponse>> grouped = responses.stream()
                .collect(Collectors.groupingBy(r -> String.valueOf(r.decision())));

        var largest = grouped.entrySet().stream()
                .max(Map.Entry.comparingByValue((a, b) -> a.size() - b.size()))
                .orElse(null);

        if (largest == null) {
            return new AgreementResult.Disagreed("No responses");
        }

        int count = largest.getValue().size();
        boolean agreed = switch (mode) {
            case UNANIMOUS -> count == responses.size();
            case MAJORITY -> count > responses.size() / 2;
            case THRESHOLD -> count >= requiredAgreement;
        };

        if (agreed) {
            return new AgreementResult.Agreed(largest.getValue().get(0));
        }
        return new AgreementResult.Disagreed(
                count + " of " + responses.size() + " agreed (mode: " + mode + ")");
    }
}
