package io.casehub.blocks.speech.sherpa;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class VoiceRegistry {

    @FunctionalInterface
    interface VoiceEncoder {
        int[] encode(byte[] audioData);
    }

    private final VoiceEncoder encoder;
    private final Map<String, int[]> voices = new ConcurrentHashMap<>();

    VoiceRegistry(VoiceEncoder encoder) {
        this.encoder = encoder;
    }

    String register(Path referenceAudio) {
        byte[] audioData;
        try {
            audioData = Files.readAllBytes(referenceAudio);
        } catch (IOException e) {
            throw new SherpaException("Failed to read reference audio: " + referenceAudio, e);
        }
        int[] codes = encoder.encode(audioData);
        String id = UUID.randomUUID().toString();
        voices.put(id, codes);
        return id;
    }

    int[] getVoiceCodes(String voiceId) {
        int[] codes = voices.get(voiceId);
        if (codes == null) throw new IllegalArgumentException("Unknown voice: " + voiceId);
        return codes.clone();
    }

    void release(String voiceId) {
        voices.remove(voiceId);
    }

    Set<String> registeredVoices() {
        return Set.copyOf(voices.keySet());
    }

    void close() {
        voices.clear();
    }
}
