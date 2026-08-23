package io.casehub.blocks.speech;

import java.util.Objects;

public record TranscriptionResult(String text, String language, double confidence) {
    public TranscriptionResult {
        Objects.requireNonNull(text, "text");
    }
}
