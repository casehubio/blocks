package io.casehub.blocks.negotiation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

public final class NegotiationFold {

    private NegotiationFold() {}

    public static NegotiationState propose(NegotiationState state,
                                           String proposalId, String proposer,
                                           String content, Instant createdAt) {
        var proposals = new ArrayList<>(state.proposals());

        for (int i = 0; i < proposals.size(); i++) {
            if (proposals.get(i).status() == ProposalStatus.ACTIVE) {
                Proposal old = proposals.get(i);
                proposals.set(i, new Proposal(old.proposalId(), old.proposer(),
                        old.content(), old.round(), old.createdAt(), ProposalStatus.SUPERSEDED));
            }
        }

        int round = proposals.size() + 1;
        proposals.add(new Proposal(proposalId, proposer, content, round, createdAt,
                ProposalStatus.ACTIVE));

        var parties = new LinkedHashSet<>(state.parties());
        parties.add(proposer);

        return new NegotiationState(proposals, parties, Map.of(), NegotiationOutcome.PENDING);
    }

    public static NegotiationState accept(NegotiationState state,
                                          String party, Instant respondedAt) {
        if (state.activeProposal() == null) return state;

        var responses = new LinkedHashMap<>(state.responses());
        responses.put(party, new Response(party, PartyDecision.ACCEPTED, null, respondedAt));

        var parties = new LinkedHashSet<>(state.parties());
        parties.add(party);

        return new NegotiationState(state.proposals(), parties, responses, state.outcome());
    }

    public static NegotiationState reject(NegotiationState state,
                                          String party, String reason,
                                          Instant respondedAt) {
        if (state.activeProposal() == null) return state;

        var responses = new LinkedHashMap<>(state.responses());
        responses.put(party, new Response(party, PartyDecision.REJECTED, reason, respondedAt));

        var parties = new LinkedHashSet<>(state.parties());
        parties.add(party);

        return new NegotiationState(state.proposals(), parties, responses, state.outcome());
    }

    public static NegotiationState agree(NegotiationState state) {
        if (state.activeProposal() == null) return state;

        var proposals = new ArrayList<>(state.proposals());
        for (int i = 0; i < proposals.size(); i++) {
            if (proposals.get(i).status() == ProposalStatus.ACTIVE) {
                Proposal p = proposals.get(i);
                proposals.set(i, new Proposal(p.proposalId(), p.proposer(), p.content(),
                        p.round(), p.createdAt(), ProposalStatus.ACCEPTED));
            }
        }

        return new NegotiationState(proposals, state.parties(), state.responses(),
                NegotiationOutcome.AGREED);
    }

    public static NegotiationState deadlock(NegotiationState state) {
        if (state.activeProposal() == null) return state;

        var proposals = new ArrayList<>(state.proposals());
        for (int i = 0; i < proposals.size(); i++) {
            if (proposals.get(i).status() == ProposalStatus.ACTIVE) {
                Proposal p = proposals.get(i);
                proposals.set(i, new Proposal(p.proposalId(), p.proposer(), p.content(),
                        p.round(), p.createdAt(), ProposalStatus.REJECTED));
            }
        }

        return new NegotiationState(proposals, state.parties(), state.responses(),
                NegotiationOutcome.DEADLOCKED);
    }

    public static NegotiationState withdraw(NegotiationState state,
                                            String party, String reason,
                                            Instant withdrawnAt) {
        var responses = new LinkedHashMap<>(state.responses());
        responses.put(party, new Response(party, PartyDecision.REJECTED, reason, withdrawnAt));

        var proposals = new ArrayList<>(state.proposals());
        for (int i = 0; i < proposals.size(); i++) {
            if (proposals.get(i).status() == ProposalStatus.ACTIVE) {
                Proposal p = proposals.get(i);
                proposals.set(i, new Proposal(p.proposalId(), p.proposer(), p.content(),
                        p.round(), p.createdAt(), ProposalStatus.REJECTED));
            }
        }

        return new NegotiationState(proposals, state.parties(), responses,
                NegotiationOutcome.WITHDRAWN);
    }
}
