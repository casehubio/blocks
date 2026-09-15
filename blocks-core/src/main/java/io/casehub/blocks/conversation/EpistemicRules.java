package io.casehub.blocks.conversation;

public final class EpistemicRules {

    private EpistemicRules() {}

    public static EpistemicRule explicitAcknowledgement(int minParticipants) {
        return (point, context) -> {
            if (!context.disputedBy().isEmpty()) {
                return EpistemicStatus.DISPUTED;
            }
            if (context.acknowledgedBy().size() >= minParticipants) {
                return EpistemicStatus.ESTABLISHED;
            }
            return EpistemicStatus.PENDING;
        };
    }

    public static EpistemicRule tacitAcceptance(int windowRounds) {
        return (point, context) -> {
            if (!context.disputedBy().isEmpty() || !context.failedBy().isEmpty()) {
                return EpistemicStatus.DISPUTED;
            }
            if (context.respondedBy().size() >= 1
                    && context.roundsSinceLastActivity() >= windowRounds) {
                return EpistemicStatus.ESTABLISHED;
            }
            return EpistemicStatus.PENDING;
        };
    }

    public static EpistemicRule commitmentResolution() {
        return (point, context) -> {
            if (!context.disputedBy().isEmpty() || !context.failedBy().isEmpty()) {
                return EpistemicStatus.DISPUTED;
            }
            if (!context.completedBy().isEmpty()) {
                return EpistemicStatus.ESTABLISHED;
            }
            return EpistemicStatus.PENDING;
        };
    }
}
