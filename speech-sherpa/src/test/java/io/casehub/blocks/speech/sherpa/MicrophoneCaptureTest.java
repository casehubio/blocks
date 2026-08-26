package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.RecognitionStream;
import io.casehub.blocks.speech.TranscriptionResult;
import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.Control;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.TargetDataLine;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

class MicrophoneCaptureTest {

    // --- pcmToFloat tests ---

    @Test
    void pcmToFloat_silence() {
        byte[] pcm = new byte[]{0, 0, 0, 0, 0, 0};
        float[] result = MicrophoneCapture.pcmToFloat(pcm, pcm.length);
        assertThat(result).hasSize(3);
        assertThat(result).containsExactly(0f, 0f, 0f);
    }

    @Test
    void pcmToFloat_maxPositive() {
        byte[] pcm = new byte[]{(byte) 0xFF, 0x7F};
        float[] result = MicrophoneCapture.pcmToFloat(pcm, pcm.length);
        assertThat(result).hasSize(1);
        assertThat(result[0]).isCloseTo(32767f / 32768f, offset(0.0001f));
    }

    @Test
    void pcmToFloat_maxNegative() {
        byte[] pcm = new byte[]{0x00, (byte) 0x80};
        float[] result = MicrophoneCapture.pcmToFloat(pcm, pcm.length);
        assertThat(result).hasSize(1);
        assertThat(result[0]).isEqualTo(-1.0f);
    }

    @Test
    void pcmToFloat_oddByteCount_ignoresTrailingByte() {
        byte[] pcm = new byte[]{0x00, 0x40, (byte) 0xFF};
        float[] result = MicrophoneCapture.pcmToFloat(pcm, pcm.length);
        assertThat(result).hasSize(1);
        assertThat(result[0]).isCloseTo(0.5f, offset(0.0001f));
    }

    @Test
    void pcmToFloat_partialLength() {
        byte[] pcm = new byte[]{0x00, 0x40, (byte) 0xFF, 0x7F};
        float[] result = MicrophoneCapture.pcmToFloat(pcm, 2);
        assertThat(result).hasSize(1);
        assertThat(result[0]).isCloseTo(0.5f, offset(0.0001f));
    }

    // --- Capture loop tests ---

    @Test
    void captureLoop_feedsSamplesToStream() throws Exception {
        byte[] pcmData = new byte[]{0x00, 0x40, 0x00, 0x40};
        var readCount = new AtomicInteger(0);
        var latch = new CountDownLatch(1);

        TargetDataLine mockLine = new StubTargetDataLine() {
            @Override
            public int read(byte[] b, int off, int len) {
                if (readCount.getAndIncrement() == 0) {
                    System.arraycopy(pcmData, 0, b, off, pcmData.length);
                    return pcmData.length;
                }
                latch.countDown();
                return 0;
            }
        };

        List<float[]> received = new CopyOnWriteArrayList<>();
        RecognitionStream mockStream = new StubRecognitionStream() {
            @Override
            public void acceptSamples(float[] samples, int sampleRate) {
                received.add(samples);
            }
        };

        var capture = new MicrophoneCapture(mockStream, mockLine, 16000);
        capture.start();
        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        capture.stop();

        assertThat(received).hasSize(1);
        assertThat(received.getFirst()).hasSize(2);
        assertThat(received.getFirst()[0]).isCloseTo(0.5f, offset(0.001f));
        assertThat(received.getFirst()[1]).isCloseTo(0.5f, offset(0.001f));
    }

    @Test
    void captureLoop_stopsOnStreamException() throws Exception {
        var readCount = new AtomicInteger(0);
        TargetDataLine mockLine = new StubTargetDataLine() {
            @Override
            public int read(byte[] b, int off, int len) {
                if (readCount.getAndIncrement() < 10) {
                    java.util.Arrays.fill(b, off, off + Math.min(len, 4), (byte) 0);
                    return 4;
                }
                return 0;
            }
        };

        RecognitionStream failingStream = new StubRecognitionStream() {
            @Override
            public void acceptSamples(float[] samples, int sampleRate) {
                throw new RuntimeException("stream error");
            }
        };

        var capture = new MicrophoneCapture(failingStream, mockLine, 16000);
        capture.start();
        Thread.sleep(200);
        assertThatThrownBy(capture::stop)
                .isInstanceOf(SherpaException.class)
                .hasMessageContaining("Capture failed");
    }

    // --- Lifecycle tests ---

    @Test
    void start_whenAlreadyRunning_throws() {
        var capture = new MicrophoneCapture(new StubRecognitionStream(), new StubTargetDataLine(), 16000);
        capture.start();
        assertThatThrownBy(capture::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already running");
        capture.close();
    }

    @Test
    void start_afterClose_throws() {
        var capture = new MicrophoneCapture(new StubRecognitionStream(), new StubTargetDataLine(), 16000);
        capture.close();
        assertThatThrownBy(capture::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void stop_whenNotStarted_isNoOp() {
        var capture = new MicrophoneCapture(new StubRecognitionStream(), new StubTargetDataLine(), 16000);
        capture.stop();
    }

    @Test
    void close_isIdempotent() {
        var capture = new MicrophoneCapture(new StubRecognitionStream(), new StubTargetDataLine(), 16000);
        capture.close();
        capture.close();
    }

    // --- Test doubles ---

    static class StubTargetDataLine implements TargetDataLine {
        @Override public void open(AudioFormat format) {}
        @Override public void open(AudioFormat format, int bufferSize) {}
        @Override public int read(byte[] b, int off, int len) { return 0; }
        @Override public void drain() {}
        @Override public void flush() {}
        @Override public void start() {}
        @Override public void stop() {}
        @Override public boolean isRunning() { return false; }
        @Override public boolean isActive() { return false; }
        @Override public AudioFormat getFormat() { return null; }
        @Override public int getBufferSize() { return 0; }
        @Override public int available() { return 0; }
        @Override public int getFramePosition() { return 0; }
        @Override public long getLongFramePosition() { return 0; }
        @Override public long getMicrosecondPosition() { return 0; }
        @Override public float getLevel() { return 0; }
        @Override public Line.Info getLineInfo() { return null; }
        @Override public void open() {}
        @Override public void close() {}
        @Override public boolean isOpen() { return true; }
        @Override public Control[] getControls() { return new Control[0]; }
        @Override public boolean isControlSupported(Control.Type control) { return false; }
        @Override public Control getControl(Control.Type control) { return null; }
        @Override public void addLineListener(LineListener listener) {}
        @Override public void removeLineListener(LineListener listener) {}
    }

    static class StubRecognitionStream implements RecognitionStream {
        @Override public void acceptSamples(float[] samples, int sampleRate) {}
        @Override public boolean isEndpointDetected() { return false; }
        @Override public String partialResult() { return ""; }
        @Override public TranscriptionResult finalResult() { return new TranscriptionResult("", "", 0); }
        @Override public void close() {}
    }
}
