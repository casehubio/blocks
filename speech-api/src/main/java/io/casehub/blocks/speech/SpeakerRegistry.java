package io.casehub.blocks.speech;

import java.util.List;
import java.util.Optional;

public interface SpeakerRegistry {
    void register(String name, SpeakerEmbedding embedding);
    Optional<SpeakerMatch> identify(SpeakerEmbedding embedding, double confidenceThreshold);
    List<String> registeredSpeakers();
    void remove(String name);
}
