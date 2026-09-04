package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.VoiceActivityFilter;
import io.casehub.blocks.speech.VoiceActivityFilterFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class VadIntegrationTest {

    @Test
    @EnabledIf("io.casehub.blocks.speech.sherpa.WhisperLibrary#isAvailable")
    void whisperSttCallsVadFilterWhenEnabled() {
        var callCount = new AtomicInteger();
        var enabled = new AtomicBoolean(true);

        VoiceActivityFilterFactory factory = () -> new VoiceActivityFilter() {
            @Override
            public float[] filterChunk(float[] samples, int sampleRate) {
                callCount.incrementAndGet();
                return samples;
            }
            @Override public void reset() {}
            @Override public void close() {}
        };

        var stt = WhisperSpeechToText.withDefaults()
                .withVoiceActivityFilter(factory, enabled::get);
        var stream = stt.startStream(
                io.casehub.blocks.speech.TranscriptionOptions.defaults());

        stream.acceptSamples(new float[1600], 16000);
        assertEquals(1, callCount.get());

        enabled.set(false);
        stream.acceptSamples(new float[1600], 16000);
        assertEquals(1, callCount.get());

        stream.close();
    }

    @Test
    @EnabledIf("io.casehub.blocks.speech.sherpa.WhisperLibrary#isAvailable")
    void vadFilterDroppingChunkPreventsAccumulation() {
        VoiceActivityFilterFactory factory = () -> new VoiceActivityFilter() {
            @Override
            public float[] filterChunk(float[] samples, int sampleRate) {
                return new float[0];
            }
            @Override public void reset() {}
            @Override public void close() {}
        };

        var stt = WhisperSpeechToText.withDefaults()
                .withVoiceActivityFilter(factory, () -> true);
        var stream = stt.startStream(
                io.casehub.blocks.speech.TranscriptionOptions.defaults());

        stream.acceptSamples(new float[1600], 16000);
        stream.acceptSamples(new float[1600], 16000);
        var result = stream.finalResult();
        assertEquals("", result.text());

        stream.close();
    }
}
