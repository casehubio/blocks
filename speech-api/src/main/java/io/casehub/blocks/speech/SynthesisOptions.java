package io.casehub.blocks.speech;

public record SynthesisOptions(String voice, String language, String audioFormat, boolean includePhonemes) {
    public static SynthesisOptions defaults() {
        return new SynthesisOptions(null, null, "wav", false);
    }
}
