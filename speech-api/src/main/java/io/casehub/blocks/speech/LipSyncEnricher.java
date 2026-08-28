package io.casehub.blocks.speech;

import java.util.List;
import java.util.Objects;

public final class LipSyncEnricher implements TextToSpeechService {

    private final TextToSpeechService delegate;
    private final PhonemeAligner aligner;
    private final int sampleRate;

    private LipSyncEnricher(TextToSpeechService delegate, PhonemeAligner aligner, int sampleRate) {
        this.delegate = Objects.requireNonNull(delegate);
        this.aligner = Objects.requireNonNull(aligner);
        this.sampleRate = sampleRate;
    }

    public static TextToSpeechService wrap(TextToSpeechService delegate, PhonemeAligner aligner) {
        return wrap(delegate, aligner, 22050);
    }

    public static TextToSpeechService wrap(TextToSpeechService delegate, PhonemeAligner aligner, int sampleRate) {
        return new LipSyncEnricher(delegate, aligner, sampleRate);
    }

    @Override
    public SynthesisResult synthesise(String text, SynthesisOptions options) {
        SynthesisResult result = delegate.synthesise(text, options);
        if (!result.phonemes().isEmpty()) {
            return result;
        }
        List<PhonemeTiming> timing = aligner.align(text, result.audioData(), sampleRate);
        return new SynthesisResult(result.audioData(), result.audioFormat(), timing);
    }
}
