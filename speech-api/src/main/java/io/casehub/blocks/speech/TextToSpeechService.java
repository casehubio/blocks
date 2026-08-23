package io.casehub.blocks.speech;

public interface TextToSpeechService {
    SynthesisResult synthesise(String text, SynthesisOptions options);
}
