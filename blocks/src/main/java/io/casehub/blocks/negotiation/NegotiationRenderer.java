package io.casehub.blocks.negotiation;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class NegotiationRenderer {

    public String render(NegotiationState state) {
        var sb = new StringBuilder();
        sb.append("# Negotiation Summary\n\n");

        sb.append("**Status:** ").append(state.outcome()).append("\n");
        sb.append("**Rounds:** ").append(state.round()).append("\n");
        sb.append("**Parties:** ").append(String.join(", ", state.parties())).append("\n\n");

        Proposal active = state.activeProposal();
        if (active != null) {
            sb.append("## Current Proposal (Round ").append(active.round()).append(")\n\n");
            sb.append("**Proposed by:** ").append(active.proposer()).append("\n");
            sb.append("**Terms:** ").append(active.content()).append("\n\n");

            if (!state.responses().isEmpty()) {
                sb.append("**Responses:**\n");
                for (Response r : state.responses().values()) {
                    String emoji = r.decision() == PartyDecision.ACCEPTED ? "✓" : "✗";
                    sb.append("- ").append(emoji).append(" **").append(r.party())
                            .append(":** ").append(r.decision());
                    if (r.reason() != null && !r.reason().isBlank()) {
                        sb.append(" — ").append(r.reason());
                    }
                    sb.append("\n");
                }
            }

            Set<String> pending = new LinkedHashSet<>(state.parties());
            pending.remove(active.proposer());
            pending.removeAll(state.responses().keySet());
            if (!pending.isEmpty()) {
                sb.append("\n**Awaiting response from:** ")
                        .append(String.join(", ", pending)).append("\n");
            }
        }

        List<Proposal> history = state.proposals().stream()
                .filter(p -> p.status() != ProposalStatus.ACTIVE)
                .toList();
        if (!history.isEmpty()) {
            sb.append("\n---\n\n## Proposal History\n\n");
            for (Proposal p : history) {
                String statusEmoji = switch (p.status()) {
                    case SUPERSEDED -> "↩";
                    case ACCEPTED -> "✓";
                    case REJECTED -> "✗";
                    default -> "·";
                };
                sb.append(statusEmoji).append(" **Round ").append(p.round())
                        .append("** (").append(p.proposer()).append("): ")
                        .append(p.content()).append(" — ").append(p.status()).append("\n");
            }
        }

        return sb.toString();
    }
}
