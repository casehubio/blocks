package io.casehub.blocks.conversation;

@FunctionalInterface
public interface EpistemicRule {
    EpistemicStatus classify(ConversationPoint point, ParticipantContext context);

    default EpistemicRule and(EpistemicRule other) {
        return (point, context) -> {
            EpistemicStatus a = this.classify(point, context);
            EpistemicStatus b = other.classify(point, context);
            if (a == EpistemicStatus.DISPUTED || b == EpistemicStatus.DISPUTED) {
                return EpistemicStatus.DISPUTED;
            }
            if (a == EpistemicStatus.PENDING || b == EpistemicStatus.PENDING) {
                return EpistemicStatus.PENDING;
            }
            return EpistemicStatus.ESTABLISHED;
        };
    }

    default EpistemicRule or(EpistemicRule other) {
        return (point, context) -> {
            EpistemicStatus a = this.classify(point, context);
            EpistemicStatus b = other.classify(point, context);
            if (a == EpistemicStatus.ESTABLISHED || b == EpistemicStatus.ESTABLISHED) {
                return EpistemicStatus.ESTABLISHED;
            }
            if (a == EpistemicStatus.PENDING || b == EpistemicStatus.PENDING) {
                return EpistemicStatus.PENDING;
            }
            return EpistemicStatus.DISPUTED;
        };
    }
}
