package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.SpeechDenoiser;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.Objects;

public final class SherpaOnnxSpeechDenoiser implements SpeechDenoiser, AutoCloseable {

    private final SherpaLibrary lib;
    private final MemorySegment denoiser;
    private final Arena denoiserArena;

    public static SherpaOnnxSpeechDenoiser withDefaults() {
        return withDefaults("dpdfnet_baseline");
    }

    public static SherpaOnnxSpeechDenoiser withDefaults(String modelName) {
        Provisioner.ensureNativeLibrary();
        Path modelDir = Provisioner.ensureDenoiserModel(modelName);
        SherpaLibrary lib = SherpaLibrary.load();
        return new SherpaOnnxSpeechDenoiser(lib, modelDir.resolve(modelName + ".onnx"));
    }

    SherpaOnnxSpeechDenoiser(SherpaLibrary lib, Path modelPath) {
        this.lib = lib;
        this.denoiserArena = Arena.ofShared();

        MemorySegment configSeg = denoiserArena.allocate(SherpaLayouts.CONFIG_ALLOC_SIZE);
        configSeg.fill((byte) 0);
        configSeg.set(ValueLayout.ADDRESS, SherpaLayouts.DENOISER_DPDFNET_MODEL,
                denoiserArena.allocateFrom(modelPath.toString()));
        configSeg.set(ValueLayout.JAVA_INT, SherpaLayouts.DENOISER_NUM_THREADS,
                Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
        configSeg.set(ValueLayout.ADDRESS, SherpaLayouts.DENOISER_PROVIDER,
                denoiserArena.allocateFrom("cpu"));

        try {
            this.denoiser = (MemorySegment) lib.createOfflineDenoiser.invokeExact(configSeg);
        } catch (Throwable t) {
            denoiserArena.close();
            throw new SherpaException("Failed to create offline speech denoiser", t);
        }

        if (denoiser.equals(MemorySegment.NULL)) {
            denoiserArena.close();
            throw new SherpaException("sherpa-onnx returned null offline denoiser — check model: " + modelPath);
        }
    }

    @Override
    public float[] denoise(float[] samples, int sampleRate) {
        Objects.requireNonNull(samples, "samples");
        if (samples.length == 0) { return samples; }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment samplesSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, samples);

            MemorySegment resultPtr;
            try {
                resultPtr = (MemorySegment) lib.offlineDenoiserRun.invokeExact(
                        denoiser, samplesSeg, samples.length, sampleRate);
            } catch (Throwable t) {
                throw new SherpaException("Offline denoiser run failed", t);
            }

            try {
                MemorySegment result = resultPtr.reinterpret(SherpaLayouts.GENERATED_AUDIO.byteSize());
                int n = (int) SherpaLayouts.AUDIO_N.get(result, 0L);
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
    public void close() {
        try { lib.destroyOfflineDenoiser.invokeExact(denoiser); } catch (Throwable ignored) {}
        denoiserArena.close();
    }
}
