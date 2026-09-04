package io.casehub.blocks.speech;

import java.util.Map;

public interface VoiceprintStore {
    void save(String name, SpeakerEmbedding embedding);
    Map<String, SpeakerEmbedding> loadAll();
    void delete(String name);
}
