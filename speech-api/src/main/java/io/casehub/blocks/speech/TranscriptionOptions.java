package io.casehub.blocks.speech;

public record TranscriptionOptions(String audioFormat, String languageHint, String modelSize,
                                   String vocabularyHint) {

    public TranscriptionOptions(String audioFormat, String languageHint, String modelSize) {
        this(audioFormat, languageHint, modelSize, null);
    }

    public static TranscriptionOptions defaults() {
        return new TranscriptionOptions("wav", null, "base.en", null);
    }

    public TranscriptionOptions withVocabularyHint(String hint) {
        return new TranscriptionOptions(audioFormat, languageHint, modelSize, hint);
    }
}
