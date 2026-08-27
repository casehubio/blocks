package io.casehub.blocks.speech.ws.protocol;

public record VisemeFrame(String viseme, long startMs, long endMs) {
    public VisemeFrame {
        if (viseme == null || viseme.isBlank()) throw new IllegalArgumentException("viseme required");
        if (endMs < startMs) throw new IllegalArgumentException("endMs < startMs");
    }
}
