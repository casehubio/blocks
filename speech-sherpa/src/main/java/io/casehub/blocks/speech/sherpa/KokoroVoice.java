package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.SynthesisOptions;
import io.casehub.blocks.speech.SynthesisResult;
import io.casehub.blocks.speech.TextToSpeechService;

final class KokoroVoice implements TextToSpeechService {

    private final KokoroTextToSpeech engine;
    private final int voiceId;

    KokoroVoice(KokoroTextToSpeech engine, int voiceId) {
        this.engine = engine;
        this.voiceId = voiceId;
    }

    @Override
    public SynthesisResult synthesise(String text, SynthesisOptions options) {
        return engine.synthesise(text, voiceId, options);
    }

    @Override
    public void warmUp() {
        engine.warmUp();
    }
}
