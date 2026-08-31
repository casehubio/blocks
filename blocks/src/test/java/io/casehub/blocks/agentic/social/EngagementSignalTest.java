package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.memory.engagement.EngagementEvent;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class EngagementSignalTest {

    @Test void turnOutcome_requiresNonNullEvent() {
        assertThatThrownBy(() -> new EngagementSignal.TurnOutcome(null, Map.of(), "excerpt"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test void turnOutcome_requiresNonNullSnapshot() {
        assertThatThrownBy(() -> new EngagementSignal.TurnOutcome(dummyEvent(), null, "excerpt"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test void turnOutcome_defensiveCopiesSnapshot() {
        var mutable = new HashMap<>(Map.of("verbosity", 0.7));
        var signal = new EngagementSignal.TurnOutcome(dummyEvent(), mutable, "excerpt");
        mutable.put("hacked", 1.0);
        assertThat(signal.dimensionalSnapshot()).doesNotContainKey("hacked");
    }

    @Test void turnOutcome_allowsNullExcerpt() {
        var signal = new EngagementSignal.TurnOutcome(dummyEvent(), Map.of(), null);
        assertThat(signal.responseExcerpt()).isNull();
    }

    @Test void conversationOutcome_requiresNonNullConversationId() {
        assertThatThrownBy(() -> new EngagementSignal.ConversationOutcome(null, "summary", 5))
                .isInstanceOf(NullPointerException.class);
    }

    @Test void conversationOutcome_requiresNonBlankConversationId() {
        assertThatThrownBy(() -> new EngagementSignal.ConversationOutcome("", "summary", 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void conversationOutcome_requiresNonBlankConversationId_whitespace() {
        assertThatThrownBy(() -> new EngagementSignal.ConversationOutcome("  ", "summary", 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void conversationOutcome_rejectsNegativeTurnCount() {
        assertThatThrownBy(() -> new EngagementSignal.ConversationOutcome("conv-1", "summary", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void conversationOutcome_acceptsZeroTurnCount() {
        var outcome = new EngagementSignal.ConversationOutcome("conv-1", "summary", 0);
        assertThat(outcome.turnCount()).isZero();
    }

    @Test void conversationOutcome_allowsNullSummary() {
        var outcome = new EngagementSignal.ConversationOutcome("conv-1", null, 3);
        assertThat(outcome.conversationSummary()).isNull();
    }

    private EngagementEvent dummyEvent() {
        return new EngagementEvent("agent-1", "user-1", "tenant-1", "case-1",
                                   "turn-1", null, "test description", null, Map.of(),
                                   true, null, null, null, null, true);
    }
}
