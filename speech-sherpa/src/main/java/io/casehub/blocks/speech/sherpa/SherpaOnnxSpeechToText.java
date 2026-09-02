package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.SpeechToTextService;
import io.casehub.blocks.speech.TranscriptionOptions;
import io.casehub.blocks.speech.TranscriptionResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.Objects;

public final class SherpaOnnxSpeechToText implements SpeechToTextService {

    private final SherpaConfig config;
    private final SherpaLibrary lib;
    private io.casehub.blocks.speech.SpeechDenoiser denoiser;
    private java.util.function.BooleanSupplier denoiserEnabled;

    public static SherpaOnnxSpeechToText withDefaults() {
        SherpaLibrary lib = SherpaLibrary.load();
        SherpaConfig config = SherpaConfig.defaults();
        if (Provisioner.isAutoDownloadEnabled()
                && !java.nio.file.Files.isDirectory(config.modelDir())) {
            Provisioner.ensureModel("sherpa-onnx-whisper-tiny");
        }
        return new SherpaOnnxSpeechToText(config, lib);
    }

    public SherpaOnnxSpeechToText(SherpaConfig config) {
        this(config, SherpaLibrary.load());
    }

    public SherpaOnnxSpeechToText(SherpaConfig config, Path libraryPath) {
        this(config, SherpaLibrary.load(libraryPath));
    }

    SherpaOnnxSpeechToText(SherpaConfig config, SherpaLibrary lib) {
        this.config = Objects.requireNonNull(config);
        this.lib    = lib;
    }

    public SherpaOnnxSpeechToText withDenoiser(
            io.casehub.blocks.speech.SpeechDenoiser denoiser,
            java.util.function.BooleanSupplier enabled) {
        this.denoiser = denoiser;
        this.denoiserEnabled = enabled;
        return this;
    }

    @Override
    public TranscriptionResult transcribe(Path audioFile, TranscriptionOptions options) {
        Objects.requireNonNull(audioFile, "audioFile");
        Objects.requireNonNull(options, "options");

        WavData wav;
        try {
            wav = WavReader.read(audioFile);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read audio file: " + audioFile, e);
        }

        if (denoiser != null && denoiserEnabled != null && denoiserEnabled.getAsBoolean()) {
            float[] denoised = denoiser.denoise(wav.samples(), wav.sampleRate());
            wav = new WavData(denoised, wav.sampleRate(), wav.channels());
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment configSeg = buildRecognizerConfig(arena, options);
            MemorySegment recognizer;
            try {
                recognizer = (MemorySegment) lib.createRecognizer.invokeExact(configSeg);
            } catch (Throwable t) {
                throw new SherpaException("Failed to create recognizer", t);
            }

            if (recognizer.equals(MemorySegment.NULL)) {
                throw new SherpaException("sherpa-onnx returned null recognizer — check model paths in " + config.modelDir());
            }

            try {
                return doTranscribe(arena, recognizer, wav);
            } finally {
                destroyQuietly(() -> lib.destroyRecognizer.invokeExact(recognizer));
            }
        }
    }

    private TranscriptionResult doTranscribe(Arena arena, MemorySegment recognizer, WavData wav) {
        MemorySegment stream;
        try {
            stream = (MemorySegment) lib.createStream.invokeExact(recognizer);
        } catch (Throwable t) {
            throw new SherpaException("Failed to create stream", t);
        }

        try {
            MemorySegment samples = arena.allocateFrom(java.lang.foreign.ValueLayout.JAVA_FLOAT, wav.samples());
            try {
                lib.acceptWaveform.invokeExact(stream, wav.sampleRate(), samples, wav.samples().length);
            } catch (Throwable t) {
                throw new SherpaException("Failed to accept waveform", t);
            }

            try {
                lib.decodeStream.invokeExact(recognizer, stream);
            } catch (Throwable t) {
                throw new SherpaException("Failed to decode stream", t);
            }

            MemorySegment result;
            try {
                result = (MemorySegment) lib.getResult.invokeExact(stream);
            } catch (Throwable t) {
                throw new SherpaException("Failed to get result", t);
            }

            try {
                MemorySegment textPtr = result.reinterpret(Long.MAX_VALUE).get(
                        java.lang.foreign.ValueLayout.ADDRESS, 0);
                String text = textPtr.reinterpret(Long.MAX_VALUE).getString(0);
                String language = resolveLanguage(text);
                return new TranscriptionResult(text, language, 1.0);
            } finally {
                destroyQuietly(() -> lib.destroyResult.invokeExact(result));
            }
        } finally {
            destroyQuietly(() -> lib.destroyStream.invokeExact(stream));
        }
    }

    private MemorySegment buildRecognizerConfig(Arena arena, TranscriptionOptions options) {
        MemorySegment seg = arena.allocate(SherpaLayouts.CONFIG_ALLOC_SIZE);
        seg.fill((byte) 0);

        seg.set(java.lang.foreign.ValueLayout.JAVA_INT, SherpaLayouts.FEAT_SAMPLE_RATE, 16000);
        seg.set(java.lang.foreign.ValueLayout.JAVA_INT, SherpaLayouts.FEAT_FEATURE_DIM, 80);

        String modelSize = options.modelSize() != null ? options.modelSize() : "tiny";
        Path   modelDir  = config.modelDir();
        seg.set(java.lang.foreign.ValueLayout.ADDRESS, SherpaLayouts.WHISPER_ENCODER,
                arena.allocateFrom(modelDir.resolve(modelSize + "-encoder.onnx").toString()));
        seg.set(java.lang.foreign.ValueLayout.ADDRESS, SherpaLayouts.WHISPER_DECODER,
                arena.allocateFrom(modelDir.resolve(modelSize + "-decoder.onnx").toString()));

        if (options.languageHint() != null) {
            seg.set(java.lang.foreign.ValueLayout.ADDRESS, SherpaLayouts.WHISPER_LANGUAGE,
                    arena.allocateFrom(options.languageHint()));
        }

        seg.set(java.lang.foreign.ValueLayout.ADDRESS, SherpaLayouts.MODEL_TOKENS,
                arena.allocateFrom(modelDir.resolve(modelSize + "-tokens.txt").toString()));
        seg.set(java.lang.foreign.ValueLayout.JAVA_INT, SherpaLayouts.MODEL_NUM_THREADS, config.numThreads());
        seg.set(java.lang.foreign.ValueLayout.ADDRESS, SherpaLayouts.MODEL_PROVIDER,
                arena.allocateFrom(config.provider()));

        return seg;
    }

    private String resolveLanguage(String text) {
        return text.isEmpty() ? "" : "en";
    }

    private static void destroyQuietly(DestroyAction action) {
        try {
            action.run();
        } catch (Throwable t) {
            // native cleanup — log and continue
        }
    }

    @FunctionalInterface
    private interface DestroyAction {
        void run() throws Throwable;
    }
}
