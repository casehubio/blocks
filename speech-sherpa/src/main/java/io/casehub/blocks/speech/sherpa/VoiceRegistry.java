package io.casehub.blocks.speech.sherpa;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class VoiceRegistry implements AutoCloseable {

    private final           TtsVoiceEncoder        encoder;
    private final           Map<String, VoiceData> voices = new ConcurrentHashMap<>();
    private final @Nullable VoiceData              defaultVoice;

    VoiceRegistry(TtsVoiceEncoder encoder) {
        this(encoder, null);
    }

    VoiceRegistry(TtsVoiceEncoder encoder, @Nullable VoiceData defaultVoice) {
        this.encoder      = encoder;
        this.defaultVoice = defaultVoice;
    }

    String register(Path referenceAudio) {
        return register(referenceAudio, null);
    }

    String register(Path referenceAudio, @Nullable String transcript) {
        byte[] audioData;
        try {
            audioData = Files.readAllBytes(referenceAudio);
        } catch (IOException e) {
            throw new SherpaException("Failed to read reference audio: " + referenceAudio, e);
        }
        VoiceData vd = encoder.encode(audioData, transcript);
        String    id = UUID.randomUUID().toString();
        voices.put(id, vd);
        return id;
    }

    VoiceData get(String voiceId) {
        VoiceData vd = voices.get(voiceId);
        if (vd == null) {throw new IllegalArgumentException("Unknown voice: " + voiceId);}
        return vd;
    }

    @Nullable
    VoiceData defaultVoice() {
        return defaultVoice;
    }

    void release(String voiceId) {
        voices.remove(voiceId);
    }

    Set<String> registeredVoices() {
        return Set.copyOf(voices.keySet());
    }

    public void close() {
        voices.clear();
    }
}
