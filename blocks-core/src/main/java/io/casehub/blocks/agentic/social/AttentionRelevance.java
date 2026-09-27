package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.mindmap.AttentionBriefing;
import io.casehub.neocortex.mindmap.SignalCategory;

import java.util.EnumSet;
import java.util.Set;

import static io.casehub.neocortex.mindmap.SignalCategory.*;

final class AttentionRelevance {

    static final Set<SignalCategory> GOALS = EnumSet.of(
        URGENCY_SPIKE, GOAL_RECOGNIZED, DECAY_DETECTED,
        BLOCKER_RESOLVED, PRIORITY_SHIFT);

    static final Set<SignalCategory> DRIVES = EnumSet.of(DRIVE_SHIFT);

    static final Set<SignalCategory> MOOD = EnumSet.of(AFFECT_CHANGE);

    static final Set<SignalCategory> MENTAL_MODEL = EnumSet.of(
        RELATIONSHIP_STAGE, BELIEF_REVISED);

    static final Set<SignalCategory> USER_MODEL = EnumSet.of(RELATIONSHIP_STAGE);

    static boolean overrides(AttentionBriefing briefing, Set<SignalCategory> relevance) {
        return briefing.signals().stream()
            .anyMatch(s -> relevance.contains(s.category()));
    }

    private AttentionRelevance() {}
}
