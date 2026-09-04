package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.SpeakerEmbedding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FileVoiceprintStoreTest {

    @TempDir Path tempDir;
    private FileVoiceprintStore store;

    @BeforeEach
    void setup() {
        store = new FileVoiceprintStore(tempDir);
    }

    @Test
    void saveAndLoadRoundTrip() {
        float[] vec = {1.0f, 2.0f, 3.0f};
        store.save("mark", new SpeakerEmbedding(vec, 3));
        Map<String, SpeakerEmbedding> loaded = store.loadAll();
        assertEquals(1, loaded.size());
        assertArrayEquals(vec, loaded.get("mark").vector(), 0.001f);
        assertEquals(3, loaded.get("mark").dimensions());
    }

    @Test
    void deleteRemovesFile() {
        store.save("mark", new SpeakerEmbedding(new float[]{1.0f}, 1));
        store.delete("mark");
        assertTrue(store.loadAll().isEmpty());
    }

    @Test
    void loadAllWithMultipleSpeakers() {
        store.save("mark", new SpeakerEmbedding(new float[]{1.0f}, 1));
        store.save("sarah", new SpeakerEmbedding(new float[]{2.0f}, 1));
        Map<String, SpeakerEmbedding> loaded = store.loadAll();
        assertEquals(2, loaded.size());
    }

    @Test
    void saveOverwritesExisting() {
        store.save("mark", new SpeakerEmbedding(new float[]{1.0f}, 1));
        store.save("mark", new SpeakerEmbedding(new float[]{2.0f}, 1));
        Map<String, SpeakerEmbedding> loaded = store.loadAll();
        assertEquals(1, loaded.size());
        assertArrayEquals(new float[]{2.0f}, loaded.get("mark").vector(), 0.001f);
    }

    @Test
    void loadAllFromEmptyDirectory() {
        assertTrue(store.loadAll().isEmpty());
    }
}
