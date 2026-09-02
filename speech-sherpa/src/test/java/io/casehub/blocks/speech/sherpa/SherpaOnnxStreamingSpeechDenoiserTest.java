package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.StreamingSpeechDenoiser;
import io.casehub.blocks.speech.StreamingSpeechDenoiserFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIf("io.casehub.blocks.speech.sherpa.SherpaLibrary#isAvailable")
class SherpaOnnxStreamingSpeechDenoiserTest {

    @Test
    void factoryCreatesDenoiserInstances() {
        StreamingSpeechDenoiserFactory factory = SherpaOnnxStreamingSpeechDenoiser.withDefaults();
        try (StreamingSpeechDenoiser d1 = factory.create();
             StreamingSpeechDenoiser d2 = factory.create()) {
            assertNotNull(d1);
            assertNotNull(d2);
            assertNotSame(d1, d2);
        }
    }

    @Test
    void processChunkReturnsDenoisedAudio() {
        StreamingSpeechDenoiserFactory factory = SherpaOnnxStreamingSpeechDenoiser.withDefaults();
        try (StreamingSpeechDenoiser denoiser = factory.create()) {
            float[] chunk = new float[4096];
            for (int i = 0; i < chunk.length; i++) {
                chunk[i] = (float) (Math.sin(2 * Math.PI * 440 * i / 16000) * 0.5
                                    + Math.random() * 0.1);
            }

            float[] denoised = denoiser.processChunk(chunk, 16000);
            assertNotNull(denoised);
            assertTrue(denoised.length > 0);
        }
    }

    @Test
    void resetClearsInternalState() {
        StreamingSpeechDenoiserFactory factory = SherpaOnnxStreamingSpeechDenoiser.withDefaults();
        try (StreamingSpeechDenoiser denoiser = factory.create()) {
            float[] chunk = new float[4096];
            denoiser.processChunk(chunk, 16000);
            assertDoesNotThrow(denoiser::reset);
            float[] afterReset = denoiser.processChunk(chunk, 16000);
            assertNotNull(afterReset);
        }
    }

    @Test
    void emptyChunkReturnsEmpty() {
        StreamingSpeechDenoiserFactory factory = SherpaOnnxStreamingSpeechDenoiser.withDefaults();
        try (StreamingSpeechDenoiser denoiser = factory.create()) {
            float[] result = denoiser.processChunk(new float[0], 16000);
            assertEquals(0, result.length);
        }
    }
}
