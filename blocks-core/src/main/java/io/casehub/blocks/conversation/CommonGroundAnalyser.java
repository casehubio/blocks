package io.casehub.blocks.conversation;

import io.casehub.qhorus.api.message.MessageType;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;

public final class CommonGroundAnalyser {

    private CommonGroundAnalyser() {}

    public static CommonGroundState analyse(ConversationState state, EpistemicRule rule) {
        int maxRound = maxRound(state);

        var established = new LinkedHashMap<String, GroundedFact>();
        var pending = new LinkedHashMap<String, GroundedFact>();
        var disputed = new LinkedHashMap<String, GroundedFact>();

        for (var entry : state.points().entrySet()) {
            ConversationPoint point = entry.getValue();
            ParticipantContext context = buildContext(point, maxRound);
            EpistemicStatus status = rule.classify(point, context);

            String content = point.thread().isEmpty() ? "" : point.thread().get(0).content();
            int round = point.thread().isEmpty() ? 0 : point.thread().get(0).round();

            Set<String> ackBy = context.acknowledgedBy();
            var dispBy = new HashSet<>(context.disputedBy());
            dispBy.addAll(context.failedBy());

            var fact = new GroundedFact(point.id(), point.topic(), status,
                                        content, ackBy, dispBy, round);

            switch (status) {
                case ESTABLISHED -> established.put(point.id(), fact);
                case PENDING -> pending.put(point.id(), fact);
                case DISPUTED -> disputed.put(point.id(), fact);
            }
        }

        return new CommonGroundState(established, pending, disputed);
    }

    static ParticipantContext buildContext(ConversationPoint point, int maxRound) {
        var allParticipants = new HashSet<String>();
        var respondedBy = new HashSet<String>();
        var acknowledgedBy = new HashSet<String>();
        var completedBy = new HashSet<String>();
        var disputedBy = new HashSet<String>();
        var failedBy = new HashSet<String>();
        int lastRound = 0;

        for (ThreadEntry te : point.thread()) {
            if (te.sender() != null) {
                allParticipants.add(te.sender());
            }
            if (te.messageType() != null && te.sender() != null) {
                if (te.messageType() != MessageType.EVENT
                        && te.messageType() != MessageType.QUERY
                        && te.messageType() != MessageType.COMMAND) {
                    respondedBy.add(te.sender());
                }
                if (te.messageType() == MessageType.RESPONSE
                        || te.messageType() == MessageType.DONE) {
                    acknowledgedBy.add(te.sender());
                }
                if (te.messageType() == MessageType.DONE) {
                    completedBy.add(te.sender());
                }
                if (te.messageType() == MessageType.DECLINE) {
                    disputedBy.add(te.sender());
                }
                if (te.messageType() == MessageType.FAILURE) {
                    failedBy.add(te.sender());
                }
            }
            if (te.round() > lastRound) {
                lastRound = te.round();
            }
        }

        return new ParticipantContext(allParticipants, respondedBy,
                acknowledgedBy, completedBy, disputedBy, failedBy,
                maxRound - lastRound);
    }

    private static int maxRound(ConversationState state) {
        int max = 0;
        for (ConversationPoint point : state.points().values()) {
            for (ThreadEntry te : point.thread()) {
                if (te.round() > max) { max = te.round(); }
            }
        }
        return max;
    }
}
