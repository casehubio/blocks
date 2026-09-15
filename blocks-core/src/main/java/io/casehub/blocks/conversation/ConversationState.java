package io.casehub.blocks.conversation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ConversationState(
        Map<String, ConversationPoint> points,
        List<FlagEntry> humanFlags,
        List<RoundMemo> memos,
        Map<String, SubTaskFinding> subTaskFindings
) {
    public ConversationState {
        points = Collections.unmodifiableMap(new LinkedHashMap<>(points));
        humanFlags = List.copyOf(humanFlags);
        memos = List.copyOf(memos);
        subTaskFindings = Collections.unmodifiableMap(new LinkedHashMap<>(subTaskFindings));
    }
}
