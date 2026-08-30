package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.SynthesisOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

@EnabledIfSystemProperty(named = "speech.integration", matches = "true")
class CosyVoice3IntegrationTest {

    @Test void pipelineConstructionSucceeds() {
        Path modelDir = Provisioner.ensureCosyVoice3Model();
        try (var pipeline = TtsPipeline.fromModelDir(modelDir)) {
            assertThat(pipeline.sessionCount()).isEqualTo(14);
            assertThat(pipeline.modelName()).isEqualTo("cosyvoice3");
        }
    }

    @Test void endToEndSynthesisWithVoiceCloning() {
        Path modelDir = Provisioner.ensureCosyVoice3Model();
        try (var pipeline = TtsPipeline.fromModelDir(modelDir)) {
            byte[] referenceWav = makeTestWav(16000, 3.0f);
            String voiceId = pipeline.registerVoice(
                    writeTemp(referenceWav), "Hello, this is a test voice.");
            try {
                var result = pipeline.synthesise("Hello world",
                        new SynthesisOptions(voiceId, null, "wav", false));
                assertThat(result.audioData()).isNotEmpty();
                assertThat(result.audioFormat()).isEqualTo("wav");
            } finally {
                pipeline.releaseVoice(voiceId);
            }
        }
    }

    private static byte[] makeTestWav(int sampleRate, float durationSeconds) {
        int n = (int) (sampleRate * durationSeconds);
        float[] samples = new float[n];
        for (int i = 0; i < n; i++) {
            samples[i] = 0.3f * (float) Math.sin(2 * Math.PI * 220 * i / sampleRate);
        }
        return WavWriter.encode(samples, sampleRate, 1);
    }

    private static java.nio.file.Path writeTemp(byte[] data) {
        try {
            var path = java.nio.file.Files.createTempFile("cosyvoice3-test-", ".wav");
            path.toFile().deleteOnExit();
            java.nio.file.Files.write(path, data);
            return path;
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }
}
