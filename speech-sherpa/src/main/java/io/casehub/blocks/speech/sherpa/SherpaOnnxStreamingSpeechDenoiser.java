package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.StreamingSpeechDenoiser;
import io.casehub.blocks.speech.StreamingSpeechDenoiserFactory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.Objects;

public final class SherpaOnnxStreamingSpeechDenoiser implements StreamingSpeechDenoiserFactory {

    private final SherpaLibrary lib;
    private final Path modelPath;
    private final int numThreads;

    public static SherpaOnnxStreamingSpeechDenoiser withDefaults() {
        return withDefaults("gtcrn_simple");
    }

    public static SherpaOnnxStreamingSpeechDenoiser withDefaults(String modelName) {
        Provisioner.ensureNativeLibrary();
        Path modelDir = Provisioner.ensureDenoiserModel(modelName);
        SherpaLibrary lib = SherpaLibrary.load();
        return new SherpaOnnxStreamingSpeechDenoiser(lib,
                modelDir.resolve(modelName + ".onnx"),
                Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
    }

    SherpaOnnxStreamingSpeechDenoiser(SherpaLibrary lib, Path modelPath, int numThreads) {
        this.lib = lib;
        this.modelPath = Objects.requireNonNull(modelPath);
        this.numThreads = numThreads;
    }

    @Override
    public StreamingSpeechDenoiser create() {
        return new OnlineDenoiserInstance(lib, modelPath, numThreads);
    }

    private static final class OnlineDenoiserInstance implements StreamingSpeechDenoiser {

        private final SherpaLibrary lib;
        private final MemorySegment denoiser;
        private final Arena arena;
        private boolean closed;

        OnlineDenoiserInstance(SherpaLibrary lib, Path modelPath, int numThreads) {
            this.lib = lib;
            this.arena = Arena.ofShared();

            MemorySegment configSeg = arena.allocate(SherpaLayouts.CONFIG_ALLOC_SIZE);
            configSeg.fill((byte) 0);
            configSeg.set(ValueLayout.ADDRESS, SherpaLayouts.DENOISER_GTCRN_MODEL,
                    arena.allocateFrom(modelPath.toString()));
            configSeg.set(ValueLayout.JAVA_INT, SherpaLayouts.DENOISER_NUM_THREADS, numThreads);
            configSeg.set(ValueLayout.ADDRESS, SherpaLayouts.DENOISER_PROVIDER,
                    arena.allocateFrom("cpu"));

            try {
                this.denoiser = (MemorySegment) lib.createOnlineDenoiser.invokeExact(configSeg);
            } catch (Throwable t) {
                arena.close();
                throw new SherpaException("Failed to create online speech denoiser", t);
            }

            if (denoiser.equals(MemorySegment.NULL)) {
                arena.close();
                throw new SherpaException("sherpa-onnx returned null online denoiser — check model: " + modelPath);
            }
        }

        @Override
        public float[] processChunk(float[] samples, int sampleRate) {
            Objects.requireNonNull(samples, "samples");
            if (closed) { throw new IllegalStateException("Denoiser is closed"); }
            if (samples.length == 0) { return samples; }

            try (Arena callArena = Arena.ofConfined()) {
                MemorySegment samplesSeg = callArena.allocateFrom(ValueLayout.JAVA_FLOAT, samples);

                MemorySegment resultPtr;
                try {
                    resultPtr = (MemorySegment) lib.onlineDenoiserRun.invokeExact(
                            denoiser, samplesSeg, samples.length, sampleRate);
                } catch (Throwable t) {
                    throw new SherpaException("Online denoiser run failed", t);
                }

                try {
                    MemorySegment result = resultPtr.reinterpret(SherpaLayouts.GENERATED_AUDIO.byteSize());
                    int n = (int) SherpaLayouts.AUDIO_N.get(result, 0L);
                    if (n == 0) { return new float[0]; }
                    MemorySegment denoisedPtr = (MemorySegment) SherpaLayouts.AUDIO_SAMPLES.get(result, 0L);
                    return denoisedPtr
                            .reinterpret((long) n * ValueLayout.JAVA_FLOAT.byteSize())
                            .toArray(ValueLayout.JAVA_FLOAT);
                } finally {
                    try { lib.destroyDenoisedAudio.invokeExact(resultPtr); } catch (Throwable ignored) {}
                }
            }
        }

        @Override
        public void reset() {
            if (closed) { return; }
            try { lib.onlineDenoiserReset.invokeExact(denoiser); } catch (Throwable ignored) {}
        }

        @Override
        public void close() {
            if (closed) { return; }
            closed = true;
            try { lib.destroyOnlineDenoiser.invokeExact(denoiser); } catch (Throwable ignored) {}
            arena.close();
        }
    }
}
