package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.SpeakerEmbedding;
import io.casehub.blocks.speech.SpeakerMatch;
import io.casehub.blocks.speech.VoiceprintStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CosineDistanceSpeakerRegistryTest {

    private CosineDistanceSpeakerRegistry registry;

    @BeforeEach
    void setup() {
        registry = new CosineDistanceSpeakerRegistry(new InMemoryVoiceprintStore());
    }

    @Test
    void registerAndIdentify() {
        float[] vec = unitVector(192, 0);
        registry.register("Mark", new SpeakerEmbedding(vec, 192));
        Optional<SpeakerMatch> match = registry.identify(new SpeakerEmbedding(vec, 192), 0.7);
        assertTrue(match.isPresent());
        assertEquals("Mark", match.get().name());
        assertTrue(match.get().confidence() > 0.99);
    }

    @Test
    void identifyReturnsEmptyWhenNoMatch() {
        float[] vec1 = unitVector(192, 0);
        float[] vec2 = unitVector(192, 1);
        registry.register("Mark", new SpeakerEmbedding(vec1, 192));
        Optional<SpeakerMatch> match = registry.identify(new SpeakerEmbedding(vec2, 192), 0.7);
        assertTrue(match.isEmpty());
    }

    @Test
    void identifyReturnsBestMatch() {
        float[] vec = unitVector(192, 0);
        float[] similar = new float[192];
        System.arraycopy(vec, 0, similar, 0, 192);
        similar[1] = 0.1f;
        registry.register("Mark", new SpeakerEmbedding(vec, 192));
        registry.register("Sarah", new SpeakerEmbedding(unitVector(192, 1), 192));
        Optional<SpeakerMatch> match = registry.identify(new SpeakerEmbedding(similar, 192), 0.5);
        assertTrue(match.isPresent());
        assertEquals("Mark", match.get().name());
    }

    @Test
    void removeDeletesSpeaker() {
        registry.register("Mark", new SpeakerEmbedding(unitVector(192, 0), 192));
        registry.remove("Mark");
        assertEquals(0, registry.registeredSpeakers().size());
    }

    @Test
    void reRegisterReplacesEmbedding() {
        registry.register("Mark", new SpeakerEmbedding(unitVector(192, 0), 192));
        float[] newVec = unitVector(192, 1);
        registry.register("Mark", new SpeakerEmbedding(newVec, 192));
        assertEquals(1, registry.registeredSpeakers().size());
        Optional<SpeakerMatch> match = registry.identify(new SpeakerEmbedding(newVec, 192), 0.7);
        assertTrue(match.isPresent());
    }

    @Test
    void identifyReturnsEmptyWhenRegistryEmpty() {
        Optional<SpeakerMatch> match = registry.identify(
                new SpeakerEmbedding(unitVector(192, 0), 192), 0.7);
        assertTrue(match.isEmpty());
    }

    @Test
    void cosineSimilarityOfZeroVectors() {
        assertEquals(0, CosineDistanceSpeakerRegistry.cosineSimilarity(new float[3], new float[3]));
    }

    private static float[] unitVector(int dims, int hotIndex) {
        float[] v = new float[dims];
        v[hotIndex % dims] = 1.0f;
        return v;
    }

    static class InMemoryVoiceprintStore implements VoiceprintStore {
        private final Map<String, SpeakerEmbedding> data = new HashMap<>();
        public void save(String name, SpeakerEmbedding e) { data.put(name, e); }
        public Map<String, SpeakerEmbedding> loadAll() { return new HashMap<>(data); }
        public void delete(String name) { data.remove(name); }
    }
}
