package io.casehub.blocks.conversation;

public record RoundMemo(
        String role,      // role that wrote this memo, or "UNKNOWN" if absent
        int round,        // conversation round number this memo was written after (1-based)
        String content    // the agent's working notes for this round
) {}
