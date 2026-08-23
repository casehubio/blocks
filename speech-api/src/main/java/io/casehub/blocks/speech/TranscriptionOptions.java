package io.casehub.blocks.speech;

public record TranscriptionOptions(String audioFormat, String languageHint, String modelSize) {
    public static TranscriptionOptions defaults() {
        return new TranscriptionOptions("wav", null, "tiny");
    }
}
