package io.casehub.blocks.negotiation;

import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.message.MessageView;
import io.casehub.qhorus.api.spi.ChannelProjection;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class NegotiationProjection implements ChannelProjection<NegotiationState> {

    private static final Logger LOG = System.getLogger(NegotiationProjection.class.getName());

    private final Set<String> parties;
    private final AcceptancePolicy acceptancePolicy;

    public NegotiationProjection(Set<String> parties, AcceptancePolicy acceptancePolicy) {
        this.parties = Set.copyOf(Objects.requireNonNull(parties));
        this.acceptancePolicy = Objects.requireNonNull(acceptancePolicy);
    }

    @Override
    public NegotiationState identity() {
        return new NegotiationState(List.of(), parties, Map.of(), NegotiationOutcome.PENDING);
    }

    @Override
    public NegotiationState apply(NegotiationState state, MessageView message) {
        if (state.outcome().isTerminal()) return state;

        try {
            return doApply(state, message);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "apply() caught unexpected exception — state unchanged", e);
            return state;
        }
    }

    private NegotiationState doApply(NegotiationState state, MessageView message) {
        if (!parties.contains(message.sender())) {
            LOG.log(Level.WARNING, "Message from unknown party {0} — ignored", message.sender());
            return state;
        }

        return switch (message.type()) {
            case PROPOSE -> handlePropose(state, message);
            case DONE    -> handleAccept(state, message);
            case DECLINE -> handleDecline(state, message);
            default      -> state;
        };
    }

    private NegotiationState handlePropose(NegotiationState state, MessageView message) {
        String proposalId = message.correlationId();
        if (proposalId == null) {
            LOG.log(Level.WARNING, "PROPOSE without correlationId — discarded");
            return state;
        }

        String content = message.content() != null ? message.content() : "";
        return NegotiationFold.propose(state, proposalId, message.sender(),
                content, message.createdAt());
    }

    private NegotiationState handleAccept(NegotiationState state, MessageView message) {
        Proposal active = state.activeProposal();
        if (active == null) return state;

        if (!active.proposalId().equals(message.correlationId())) return state;

        NegotiationState updated = NegotiationFold.accept(state, message.sender(),
                message.createdAt());

        if (acceptancePolicy.isAccepted(updated)) {
            return NegotiationFold.agree(updated);
        }
        return updated;
    }

    private NegotiationState handleDecline(NegotiationState state, MessageView message) {
        Proposal active = state.activeProposal();
        if (active == null) return state;

        if (active.proposer().equals(message.sender())) {
            String reason = message.content() != null ? message.content() : "";
            return NegotiationFold.withdraw(state, message.sender(), reason,
                    message.createdAt());
        }

        if (!active.proposalId().equals(message.correlationId())) return state;

        String reason = message.content() != null ? message.content() : "";
        return NegotiationFold.reject(state, message.sender(), reason,
                message.createdAt());
    }
}
