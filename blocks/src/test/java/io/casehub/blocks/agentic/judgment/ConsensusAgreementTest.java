package io.casehub.blocks.agentic.judgment;

import io.casehub.api.spi.judgment.CallerIdentity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConsensusAgreementTest {

    private static JudgmentResponse resp(String decision) {
        return new JudgmentResponse(decision, List.of(), CallerIdentity.of("test", "agent"));
    }

    @Test
    void unanimous_allAgree_returnsAgreed() {
        var policy = ConsensusAgreement.unanimous();
        var result = policy.evaluate(List.of(resp("yes"), resp("yes"), resp("yes")));
        assertThat(result).isInstanceOf(AgreementResult.Agreed.class);
    }

    @Test
    void unanimous_oneDisagrees_returnsDisagreed() {
        var policy = ConsensusAgreement.unanimous();
        var result = policy.evaluate(List.of(resp("yes"), resp("no"), resp("yes")));
        assertThat(result).isInstanceOf(AgreementResult.Disagreed.class);
    }

    @Test
    void majority_moreThanHalf_returnsAgreed() {
        var policy = ConsensusAgreement.majority();
        var result = policy.evaluate(List.of(resp("yes"), resp("yes"), resp("no")));
        assertThat(result).isInstanceOf(AgreementResult.Agreed.class);
    }

    @Test
    void majority_noMajority_returnsDisagreed() {
        var policy = ConsensusAgreement.majority();
        var result = policy.evaluate(List.of(resp("a"), resp("b"), resp("c")));
        assertThat(result).isInstanceOf(AgreementResult.Disagreed.class);
    }

    @Test
    void threshold_meetsThreshold_returnsAgreed() {
        var policy = ConsensusAgreement.threshold(2);
        var result = policy.evaluate(List.of(resp("yes"), resp("yes"), resp("no")));
        assertThat(result).isInstanceOf(AgreementResult.Agreed.class);
    }

    @Test
    void threshold_belowThreshold_returnsDisagreed() {
        var policy = ConsensusAgreement.threshold(3);
        var result = policy.evaluate(List.of(resp("yes"), resp("yes"), resp("no")));
        assertThat(result).isInstanceOf(AgreementResult.Disagreed.class);
    }

    @Test
    void emptyResponses_returnsDisagreed() {
        var policy = ConsensusAgreement.unanimous();
        var result = policy.evaluate(List.of());
        assertThat(result).isInstanceOf(AgreementResult.Disagreed.class);
    }
}
