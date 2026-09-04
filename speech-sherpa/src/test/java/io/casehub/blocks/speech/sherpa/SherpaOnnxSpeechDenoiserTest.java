package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.SpeechDenoiser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIf("io.casehub.blocks.speech.sherpa.SherpaLibrary#isAvailable")
class SherpaOnnxSpeechDenoiserTest {

    @Test
    void denoisesAudioAndReturnsSameLengthOutput() {
        SpeechDenoiser denoiser = SherpaOnnxSpeechDenoiser.withDefaults();
        float[] noisy = new float[16000];
        for (int i = 0; i < noisy.length; i++) {
            noisy[i] = (float) (Math.sin(2 * Math.PI * 440 * i / 16000) * 0.5
                                + Math.random() * 0.1);
        }

        float[] denoised = denoiser.denoise(noisy, 16000);

        assertNotNull(denoised);
        assertEquals(noisy.length, denoised.length);
        assertNotEquals(0, denoised.length);
    }

    @Test
    void implementsSpeechDenoiserInterface() {
        SpeechDenoiser denoiser = SherpaOnnxSpeechDenoiser.withDefaults();
        assertInstanceOf(SpeechDenoiser.class, denoiser);
    }

    @Test
    void emptyInputReturnsEmpty() {
        SpeechDenoiser denoiser = SherpaOnnxSpeechDenoiser.withDefaults();
        float[] result = denoiser.denoise(new float[0], 16000);
        assertEquals(0, result.length);
    }
}
