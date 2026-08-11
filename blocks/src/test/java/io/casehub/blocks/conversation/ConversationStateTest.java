package io.casehub.blocks.conversation;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ConversationStateTest {

    @Test
    void defensiveCopy_pointsMapIsImmutable() {
        var points = new HashMap<String, ConversationPoint>();
        var state = new ConversationState(points, List.of(), List.of(), Map.of());
        points.put("after", null);
        assertThat(state.points()).isEmpty();
        assertThatThrownBy(() -> state.points().put("x", null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void defensiveCopy_humanFlagsIsImmutable() {
        var flags = new ArrayList<FlagEntry>();
        var state = new ConversationState(Map.of(), flags, List.of(), Map.of());
        flags.add(new FlagEntry("e1", 1, "REV", "content"));
        assertThat(state.humanFlags()).isEmpty();
        assertThatThrownBy(() -> state.humanFlags().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void defensiveCopy_memosIsImmutable() {
        var memos = new ArrayList<RoundMemo>();
        var state = new ConversationState(Map.of(), List.of(), memos, Map.of());
        memos.add(new RoundMemo("REV", 1, "memo"));
        assertThat(state.memos()).isEmpty();
    }

    @Test
    void defensiveCopy_subTaskFindingsIsImmutable() {
        var findings = new HashMap<String, SubTaskFinding>();
        var state = new ConversationState(Map.of(), List.of(), List.of(), findings);
        findings.put("after", null);
        assertThat(state.subTaskFindings()).isEmpty();
    }
}
