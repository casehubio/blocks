package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.VoiceActivityFilter;
import io.casehub.blocks.speech.VoiceActivityFilterFactory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.Objects;

public final class SherpaOnnxVoiceActivityFilter implements VoiceActivityFilterFactory {

    private final SherpaLibrary lib;
    private final Path modelPath;
    private final int numThreads;

    public static SherpaOnnxVoiceActivityFilter withDefaults() {
        return withDefaults("silero_vad");
    }

    public static SherpaOnnxVoiceActivityFilter withDefaults(String modelName) {
        Provisioner.ensureNativeLibrary();
        Path modelDir = Provisioner.ensureVadModel(modelName);
        SherpaLibrary lib = SherpaLibrary.load();
        return new SherpaOnnxVoiceActivityFilter(lib,
                modelDir.resolve(modelName + ".onnx"),
                Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
    }

    SherpaOnnxVoiceActivityFilter(SherpaLibrary lib, Path modelPath, int numThreads) {
        this.lib = lib;
        this.modelPath = Objects.requireNonNull(modelPath);
        this.numThreads = numThreads;
    }

    @Override
    public VoiceActivityFilter create() {
        return new VadFilterInstance(lib, modelPath, numThreads);
    }

    private static final class VadFilterInstance implements VoiceActivityFilter {

        private final SherpaLibrary lib;
        private final MemorySegment vad;
        private final Arena arena;
        private boolean closed;

        VadFilterInstance(SherpaLibrary lib, Path modelPath, int numThreads) {
            this.lib = lib;
            this.arena = Arena.ofShared();

            MemorySegment configSeg = arena.allocate(SherpaLayouts.CONFIG_ALLOC_SIZE);
            configSeg.fill((byte) 0);
            configSeg.set(ValueLayout.ADDRESS, SherpaLayouts.VAD_SILERO_MODEL,
                    arena.allocateFrom(modelPath.toString()));
            configSeg.set(ValueLayout.JAVA_FLOAT, SherpaLayouts.VAD_SILERO_THRESHOLD, 0.5f);
            configSeg.set(ValueLayout.JAVA_FLOAT, SherpaLayouts.VAD_SILERO_MIN_SILENCE, 0.5f);
            configSeg.set(ValueLayout.JAVA_FLOAT, SherpaLayouts.VAD_SILERO_MIN_SPEECH, 0.25f);
            configSeg.set(ValueLayout.JAVA_INT, SherpaLayouts.VAD_SILERO_WINDOW_SIZE, 512);
            configSeg.set(ValueLayout.JAVA_FLOAT, SherpaLayouts.VAD_SILERO_MAX_SPEECH, 20.0f);
            configSeg.set(ValueLayout.JAVA_INT, SherpaLayouts.VAD_SAMPLE_RATE, 16000);
            configSeg.set(ValueLayout.JAVA_INT, SherpaLayouts.VAD_NUM_THREADS, numThreads);
            configSeg.set(ValueLayout.ADDRESS, SherpaLayouts.VAD_PROVIDER,
                    arena.allocateFrom("cpu"));

            try {
                this.vad = (MemorySegment) lib.createVad.invokeExact(configSeg, 30.0f);
            } catch (Throwable t) {
                arena.close();
                throw new SherpaException("Failed to create VAD", t);
            }

            if (vad.equals(MemorySegment.NULL)) {
                arena.close();
                throw new SherpaException("sherpa-onnx returned null VAD — check model: " + modelPath);
            }
        }

        @Override
        public float[] filterChunk(float[] samples, int sampleRate) {
            Objects.requireNonNull(samples, "samples");
            if (closed) { throw new IllegalStateException("VAD filter is closed"); }
            if (samples.length == 0) { return samples; }

            try (Arena callArena = Arena.ofConfined()) {
                MemorySegment samplesSeg = callArena.allocateFrom(ValueLayout.JAVA_FLOAT, samples);
                try {
                    lib.vadAcceptWaveform.invokeExact(vad, samplesSeg, samples.length);
                } catch (Throwable t) {
                    throw new SherpaException("VAD acceptWaveform failed", t);
                }

                int detected;
                try {
                    detected = (int) lib.vadDetected.invokeExact(vad);
                } catch (Throwable t) {
                    throw new SherpaException("VAD detected check failed", t);
                }

                return detected != 0 ? samples : new float[0];
            }
        }

        @Override
        public void reset() {
            if (closed) { return; }
            try { lib.vadReset.invokeExact(vad); } catch (Throwable ignored) {}
        }

        @Override
        public void close() {
            if (closed) { return; }
            closed = true;
            try { lib.destroyVad.invokeExact(vad); } catch (Throwable ignored) {}
            arena.close();
        }
    }
}
