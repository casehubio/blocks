package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.SpeakerEmbedding;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CampplusSpeakerEmbeddingExtractorTest {

    static CampplusSpeakerEmbeddingExtractor extractor;

    @BeforeAll
    static void setup() {
        Path campplusDir = Provisioner.ensureCampplusModel();
        OnnxRuntimeLibrary.Session session = OnnxRuntimeLibrary.load()
                .createSession(campplusDir.resolve("campplus.onnx"), 2);
        extractor = new CampplusSpeakerEmbeddingExtractor(session);
    }

    @Test
    void extractProduces192DimEmbedding() {
        float[] silence = new float[16000 * 3];
        SpeakerEmbedding emb = extractor.extract(silence, 16000);
        assertEquals(192, emb.dimensions());
        assertEquals(192, emb.vector().length);
    }

    @Test
    void sameSpeakerProducesHighSimilarity() {
        float[] audio1 = generateTone(440, 3, 16000);
        float[] audio2 = generateTone(440, 3, 16000);
        SpeakerEmbedding emb1 = extractor.extract(audio1, 16000);
        SpeakerEmbedding emb2 = extractor.extract(audio2, 16000);
        double similarity = cosineSimilarity(emb1.vector(), emb2.vector());
        assertTrue(similarity > 0.9, "Same audio should produce similar embeddings, got " + similarity);
    }

    @Test
    void differentAudioProducesDifferentEmbeddings() {
        float[] audio1 = generateTone(440, 3, 16000);
        float[] audio2 = generateTone(880, 3, 16000);
        SpeakerEmbedding emb1 = extractor.extract(audio1, 16000);
        SpeakerEmbedding emb2 = extractor.extract(audio2, 16000);
        double similarity = cosineSimilarity(emb1.vector(), emb2.vector());
        assertTrue(similarity < 0.95, "Different audio should produce different embeddings, got " + similarity);
    }

    private static float[] generateTone(float freq, int seconds, int sampleRate) {
        float[] samples = new float[sampleRate * seconds];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (float) Math.sin(2 * Math.PI * freq * i / sampleRate);
        }
        return samples;
    }

    private static double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
