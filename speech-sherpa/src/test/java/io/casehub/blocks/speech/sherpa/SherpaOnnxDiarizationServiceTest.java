package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.DiarizedSegment;
import io.casehub.blocks.speech.DiarizationOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SherpaOnnxDiarizationServiceTest {

    static SherpaOnnxDiarizationService service;

    @BeforeAll
    static void setup() {
        Path segModelDir = Provisioner.ensureDiarizationModels();
        Path campplusDir = Provisioner.ensureCampplusModel();
        service = new SherpaOnnxDiarizationService(
                SherpaLibrary.load(),
                segModelDir.resolve("model.onnx"),
                campplusDir.resolve("campplus.onnx"));
    }

    @AfterAll
    static void teardown() {
        if (service != null) service.close();
    }

    @Test
    void diarizeRunsWithoutCrash() throws IOException {
        Path testWav = createTestWav(6, 16000);
        try {
            List<DiarizedSegment> segments = service.diarize(testWav,
                    new DiarizationOptions(-1, 0.0f));
            assertNotNull(segments);
            for (DiarizedSegment seg : segments) {
                assertTrue(seg.startMs() >= 0);
                assertTrue(seg.endMs() > seg.startMs());
                assertNotNull(seg.speakerLabel());
                assertTrue(seg.samples().length > 0);
                assertEquals(16000, seg.sampleRate());
            }
        } finally {
            Files.deleteIfExists(testWav);
        }
    }

    @Test
    void diarizeWithSpeakerCountHint() throws IOException {
        Path testWav = createTestWav(6, 16000);
        try {
            List<DiarizedSegment> segments = service.diarize(testWav,
                    new DiarizationOptions(2, 0.0f));
            assertNotNull(segments);
            long distinctSpeakers = segments.stream()
                    .map(DiarizedSegment::speakerLabel).distinct().count();
            assertTrue(distinctSpeakers <= 2, "Should not exceed hinted speaker count");
        } finally {
            Files.deleteIfExists(testWav);
        }
    }

    @Test
    void segmentSamplesAreConsistent() throws IOException {
        Path testWav = createTestWav(4, 16000);
        try {
            List<DiarizedSegment> segments = service.diarize(testWav,
                    new DiarizationOptions(-1, 0.0f));
            assertNotNull(segments);
            for (DiarizedSegment seg : segments) {
                long expectedSamples = (seg.endMs() - seg.startMs()) * seg.sampleRate() / 1000;
                long actualSamples = seg.samples().length;
                assertTrue(Math.abs(actualSamples - expectedSamples) <= seg.sampleRate(),
                        "Sample count should roughly match duration");
            }
        } finally {
            Files.deleteIfExists(testWav);
        }
    }

    private static Path createTestWav(int seconds, int sampleRate) throws IOException {
        float[] audio = new float[sampleRate * seconds];
        int half = audio.length / 2;
        for (int i = 0; i < half; i++) {
            audio[i] = (float) Math.sin(2 * Math.PI * 200 * i / sampleRate) * 0.5f;
        }
        for (int i = half; i < audio.length; i++) {
            audio[i] = (float) Math.sin(2 * Math.PI * 800 * i / sampleRate) * 0.5f;
        }
        byte[] wavBytes = WavWriter.encode(audio, sampleRate, 1);
        Path wavPath = Files.createTempFile("test-diarization-", ".wav");
        Files.write(wavPath, wavBytes);
        return wavPath;
    }
}
