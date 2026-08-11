package io.casehub.blocks.conversation;

import java.util.List;

public record ConversationPoint(
        String id,
        String topic,
        PointClassification classification,
        List<ThreadEntry> thread,
        String status) {
    public ConversationPoint {
        thread = List.copyOf(thread);
    }
}
