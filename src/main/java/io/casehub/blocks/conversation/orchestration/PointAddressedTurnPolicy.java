package io.casehub.blocks.conversation.orchestration;

import io.casehub.blocks.conversation.ConversationState;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class PointAddressedTurnPolicy implements TurnPolicy {

    private static final Set<String> OPEN_STATUSES = Set.of("OPEN", "ACTIVE");

    @Override
    public List<AgentParticipant> nextResponders(
            ConversationState state,
            TurnContext context,
            List<AgentParticipant> participants) {
        var needed = new LinkedHashSet<AgentParticipant>();
        for (var point : state.points().values()) {
            if (!OPEN_STATUSES.contains(point.status())) continue;

            Set<String> respondedRoles = new HashSet<>();
            for (var entry : point.thread()) {
                respondedRoles.add(entry.role());
            }

            for (var participant : participants) {
                if (participant.agentId().equals(context.senderId())) continue;
                if (respondedRoles.contains(participant.role())) continue;
                needed.add(participant);
            }
        }
        return List.copyOf(needed);
    }
}
