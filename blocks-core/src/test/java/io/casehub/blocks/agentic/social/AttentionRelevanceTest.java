package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.mindmap.AttentionBriefing;
import io.casehub.neocortex.mindmap.AttentionSignal;
import io.casehub.neocortex.mindmap.SignalCategory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AttentionRelevanceTest {

    @Test
    void goalCategoriesOverrideGoals() {
        for (var category : List.of(
                SignalCategory.URGENCY_SPIKE, SignalCategory.GOAL_RECOGNIZED,
                SignalCategory.DECAY_DETECTED, SignalCategory.BLOCKER_RESOLVED,
                SignalCategory.PRIORITY_SHIFT)) {
            var briefing = briefingWith(category);
            assertThat(AttentionRelevance.overrides(briefing, AttentionRelevance.GOALS))
                .as("Expected %s to override GOALS", category)
                .isTrue();
        }
    }

    @Test
    void driveShiftOverridesDrives() {
        assertThat(AttentionRelevance.overrides(
            briefingWith(SignalCategory.DRIVE_SHIFT), AttentionRelevance.DRIVES)).isTrue();
    }

    @Test
    void affectChangeOverridesMood() {
        assertThat(AttentionRelevance.overrides(
            briefingWith(SignalCategory.AFFECT_CHANGE), AttentionRelevance.MOOD)).isTrue();
    }

    @Test
    void relationshipStageOverridesMentalModelAndUserModel() {
        var briefing = briefingWith(SignalCategory.RELATIONSHIP_STAGE);
        assertThat(AttentionRelevance.overrides(briefing, AttentionRelevance.MENTAL_MODEL)).isTrue();
        assertThat(AttentionRelevance.overrides(briefing, AttentionRelevance.USER_MODEL)).isTrue();
    }

    @Test
    void beliefRevisedOverridesMentalModelButNotUserModel() {
        var briefing = briefingWith(SignalCategory.BELIEF_REVISED);
        assertThat(AttentionRelevance.overrides(briefing, AttentionRelevance.MENTAL_MODEL)).isTrue();
        assertThat(AttentionRelevance.overrides(briefing, AttentionRelevance.USER_MODEL)).isFalse();
    }

    @Test
    void mergeCandidateOverridesNothing() {
        var briefing = briefingWith(SignalCategory.MERGE_CANDIDATE);
        assertThat(AttentionRelevance.overrides(briefing, AttentionRelevance.GOALS)).isFalse();
        assertThat(AttentionRelevance.overrides(briefing, AttentionRelevance.DRIVES)).isFalse();
        assertThat(AttentionRelevance.overrides(briefing, AttentionRelevance.MOOD)).isFalse();
        assertThat(AttentionRelevance.overrides(briefing, AttentionRelevance.MENTAL_MODEL)).isFalse();
        assertThat(AttentionRelevance.overrides(briefing, AttentionRelevance.USER_MODEL)).isFalse();
    }

    @Test
    void experienceGraduatedOverridesNothing() {
        var briefing = briefingWith(SignalCategory.EXPERIENCE_GRADUATED);
        assertThat(AttentionRelevance.overrides(briefing, AttentionRelevance.GOALS)).isFalse();
        assertThat(AttentionRelevance.overrides(briefing, AttentionRelevance.DRIVES)).isFalse();
        assertThat(AttentionRelevance.overrides(briefing, AttentionRelevance.MOOD)).isFalse();
    }

    private static AttentionBriefing briefingWith(SignalCategory category) {
        return new AttentionBriefing("agent", "tenant",
            List.of(new AttentionSignal("agent", "tenant", category,
                "node-1", "Source", 0.8, "reason")),
            0.8, Instant.now());
    }
}
