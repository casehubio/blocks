package io.casehub.blocks.agentic.termination;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TerminationDecisionTest {

    @Test
    void sealedPermitsFourVariants() {
        assertThat(TerminationDecision.class.getPermittedSubclasses()).hasSize(4);
    }

    @Test
    void continueIsASingleton() {
        assertThat(TerminationDecision.Continue.INSTANCE)
                .isInstanceOf(TerminationDecision.class);
    }

    @Test
    void completeCarriesResult() {
        var d = new TerminationDecision.Complete("done");
        assertThat(d.result()).isEqualTo("done");
    }

    @Test
    void failedCarriesReason() {
        var d = new TerminationDecision.Failed("error");
        assertThat(d.reason()).isEqualTo("error");
    }

    @Test
    void escalateCarriesReason() {
        var d = new TerminationDecision.Escalate("needs human");
        assertThat(d.reason()).isEqualTo("needs human");
    }
}
