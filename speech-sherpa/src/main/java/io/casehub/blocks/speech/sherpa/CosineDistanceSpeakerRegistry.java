package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.SpeakerEmbedding;
import io.casehub.blocks.speech.SpeakerMatch;
import io.casehub.blocks.speech.SpeakerRegistry;
import io.casehub.blocks.speech.VoiceprintStore;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class CosineDistanceSpeakerRegistry implements SpeakerRegistry {

    private final ConcurrentHashMap<String, SpeakerEmbedding> cache = new ConcurrentHashMap<>();
    private final VoiceprintStore store;

    public CosineDistanceSpeakerRegistry(VoiceprintStore store) {
        this.store = store;
        cache.putAll(store.loadAll());
    }

    @Override
    public void register(String name, SpeakerEmbedding embedding) {
        cache.put(name, embedding);
        store.save(name, embedding);
    }

    @Override
    public Optional<SpeakerMatch> identify(SpeakerEmbedding embedding, double confidenceThreshold) {
        String bestName = null;
        double bestSimilarity = -1;
        for (var entry : cache.entrySet()) {
            double sim = cosineSimilarity(embedding.vector(), entry.getValue().vector());
            if (sim > bestSimilarity) {
                bestSimilarity = sim;
                bestName = entry.getKey();
            }
        }
        if (bestName != null && bestSimilarity >= confidenceThreshold) {
            return Optional.of(new SpeakerMatch(bestName, bestSimilarity));
        }
        return Optional.empty();
    }

    @Override
    public List<String> registeredSpeakers() {
        return List.copyOf(cache.keySet());
    }

    @Override
    public void remove(String name) {
        cache.remove(name);
        store.delete(name);
    }

    static double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0 : dot / denom;
    }
}
