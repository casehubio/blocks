package io.casehub.blocks.speech.ws;

import io.casehub.blocks.speech.TextToSpeechService;

import java.util.Map;

public record TtsModelRegistry(Map<String, TextToSpeechService> models) {
    public TtsModelRegistry { models = Map.copyOf(models); }
    public TtsModelRegistry() { this(Map.of()); }
}
