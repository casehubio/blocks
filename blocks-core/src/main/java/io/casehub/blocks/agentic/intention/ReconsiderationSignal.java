package io.casehub.blocks.agentic.intention;

import java.util.Objects;

public record ReconsiderationSignal(
        ReconsiderationReason reason,
        String detail,
        boolean shouldDrop
) {
    public ReconsiderationSignal {
        Objects.requireNonNull(reason);
        Objects.requireNonNull(detail);
    }

    public static ReconsiderationSignal reconsider(ReconsiderationReason reason, String detail) {
        return new ReconsiderationSignal(reason, detail, false);
    }

    public static ReconsiderationSignal drop(ReconsiderationReason reason, String detail) {
        return new ReconsiderationSignal(reason, detail, true);
    }
}
