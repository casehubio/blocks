package io.casehub.blocks.negotiation;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NegotiationRendererTest {

    private static final Instant T1 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-01T00:01:00Z");

    private final NegotiationRenderer renderer = new NegotiationRenderer();

    @Test
    void emptyStateRendersStatusAndParties() {
        var state = new NegotiationState(List.of(), Set.of("alice", "bob"),
                Map.of(), NegotiationOutcome.PENDING);
        var output = renderer.render(state);
        assertThat(output).contains("**Status:** PENDING");
        assertThat(output).contains("**Rounds:** 0");
        assertThat(output).contains("alice").contains("bob");
    }

    @Test
    void activeProposalRendered() {
        var state = new NegotiationState(List.of(), Set.of("alice", "bob"),
                Map.of(), NegotiationOutcome.PENDING);
        state = NegotiationFold.propose(state, "p1", "alice", "Price: $100", T1);
        var output = renderer.render(state);
        assertThat(output).contains("## Current Proposal (Round 1)");
        assertThat(output).contains("**Proposed by:** alice");
        assertThat(output).contains("**Terms:** Price: $100");
        assertThat(output).contains("**Awaiting response from:**").contains("bob");
    }

    @Test
    void responsesRendered() {
        var state = new NegotiationState(List.of(), Set.of("alice", "bob"),
                Map.of(), NegotiationOutcome.PENDING);
        state = NegotiationFold.propose(state, "p1", "alice", "$100", T1);
        state = NegotiationFold.reject(state, "bob", "too expensive", T2);
        var output = renderer.render(state);
        assertThat(output).contains("✗ **bob:** REJECTED — too expensive");
    }

    @Test
    void proposalHistoryRendered() {
        var state = new NegotiationState(List.of(), Set.of("alice", "bob"),
                Map.of(), NegotiationOutcome.PENDING);
        state = NegotiationFold.propose(state, "p1", "alice", "$100", T1);
        state = NegotiationFold.propose(state, "p2", "bob", "$80", T2);
        var output = renderer.render(state);
        assertThat(output).contains("## Proposal History");
        assertThat(output).contains("↩ **Round 1** (alice): $100 — SUPERSEDED");
    }

    @Test
    void agreedStateRendered() {
        var state = new NegotiationState(List.of(), Set.of("alice", "bob"),
                Map.of(), NegotiationOutcome.PENDING);
        state = NegotiationFold.propose(state, "p1", "alice", "$100", T1);
        state = NegotiationFold.accept(state, "bob", T2);
        state = NegotiationFold.agree(state);
        var output = renderer.render(state);
        assertThat(output).contains("**Status:** AGREED");
    }
}
