package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.RecognitionStream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.util.Objects;

public final class MicrophoneCapture implements AutoCloseable {

    private static final int DEFAULT_SAMPLE_RATE = 16_000;
    private static final int CHUNK_MILLIS = 100;

    private final RecognitionStream stream;
    private final TargetDataLine line;
    private final int sampleRate;
    private final int chunkSamples;
    private volatile boolean running;
    private volatile boolean closed;
    private volatile Throwable captureError;
    private Thread captureThread;

    public MicrophoneCapture(RecognitionStream stream, TargetDataLine line, int sampleRate) {
        this.stream = Objects.requireNonNull(stream, "stream");
        this.line = Objects.requireNonNull(line, "line");
        if (sampleRate <= 0) throw new IllegalArgumentException("sampleRate must be positive");
        this.sampleRate = sampleRate;
        this.chunkSamples = sampleRate * CHUNK_MILLIS / 1000;
    }

    public static MicrophoneCapture openDefault(RecognitionStream stream) {
        return openDefault(stream, DEFAULT_SAMPLE_RATE);
    }

    public static MicrophoneCapture openDefault(RecognitionStream stream, int sampleRate) {
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        try {
            TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format);
            return new MicrophoneCapture(stream, line, sampleRate);
        } catch (LineUnavailableException e) {
            throw new SherpaException("No microphone available", e);
        }
    }

    public void start() {
        if (closed) {throw new IllegalStateException("Capture is closed");}
        if (running) {throw new IllegalStateException("Capture is already running");}
        running = true;
        line.start();
        captureThread = Thread.ofPlatform().daemon(true).name("mic-capture").start(this::captureLoop);
    }

    public void stop() {
        running = false;
        if (captureThread == null) {return;}
        line.stop();
        try {
            captureThread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (captureThread.isAlive()) {
            captureThread.interrupt();
        }
        if (captureError != null) {
            Throwable error = captureError;
            captureError = null;
            throw new SherpaException("Capture failed", error);
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        stop();
        line.close();
    }

    private void captureLoop() {
        int bytesPerChunk = chunkSamples * 2;
        byte[] buffer = new byte[bytesPerChunk];
        while (running) {
            int read = line.read(buffer, 0, bytesPerChunk);
            if (read <= 0) continue;
            try {
                float[] samples = pcmToFloat(buffer, read);
                stream.acceptSamples(samples, sampleRate);
            } catch (Throwable t) {
                captureError = t;
                running = false;
            }
        }
    }

    static float[] pcmToFloat(byte[] pcm, int length) {
        int sampleCount = length / 2;
        float[] samples = new float[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            int lo = pcm[2 * i] & 0xFF;
            int hi = pcm[2 * i + 1];
            samples[i] = (short) (lo | (hi << 8)) / 32768f;
        }
        return samples;
    }
}
