package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.VoiceActivityFilter;
import io.casehub.blocks.speech.VoiceActivityFilterFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIf("io.casehub.blocks.speech.sherpa.SherpaLibrary#isAvailable")
class SherpaOnnxVoiceActivityFilterTest {

    @Test
    void factoryCreatesFilterInstances() {
        VoiceActivityFilterFactory factory = SherpaOnnxVoiceActivityFilter.withDefaults();
        try (VoiceActivityFilter f1 = factory.create();
             VoiceActivityFilter f2 = factory.create()) {
            assertNotNull(f1);
            assertNotNull(f2);
            assertNotSame(f1, f2);
        }
    }

    @Test
    void silenceReturnsEmptyArray() {
        VoiceActivityFilterFactory factory = SherpaOnnxVoiceActivityFilter.withDefaults();
        try (VoiceActivityFilter filter = factory.create()) {
            float[] silence = new float[512];
            float[] result = filter.filterChunk(silence, 16000);
            assertEquals(0, result.length);
        }
    }

    @Test
    void resetClearsState() {
        VoiceActivityFilterFactory factory = SherpaOnnxVoiceActivityFilter.withDefaults();
        try (VoiceActivityFilter filter = factory.create()) {
            float[] silence = new float[512];
            filter.filterChunk(silence, 16000);
            assertDoesNotThrow(filter::reset);
        }
    }

    @Test
    void emptyInputReturnsEmpty() {
        VoiceActivityFilterFactory factory = SherpaOnnxVoiceActivityFilter.withDefaults();
        try (VoiceActivityFilter filter = factory.create()) {
            float[] result = filter.filterChunk(new float[0], 16000);
            assertEquals(0, result.length);
        }
    }
}
