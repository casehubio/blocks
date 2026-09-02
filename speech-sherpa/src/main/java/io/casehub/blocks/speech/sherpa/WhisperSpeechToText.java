package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.RecognitionStream;
import io.casehub.blocks.speech.StreamingSpeechToTextService;
import io.casehub.blocks.speech.TranscriptionOptions;
import io.casehub.blocks.speech.TranscriptionResult;
import org.jspecify.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;

public final class WhisperSpeechToText implements StreamingSpeechToTextService, AutoCloseable {

    private static final int MAX_BUFFER_SAMPLES = 480_000; // 30s at 16kHz
    private static final long PARTIAL_THROTTLE_MS = 500;

    private final WhisperLibrary lib;
    private final MemorySegment ctx;
    private final Arena ctxArena;
    private final ReentrantLock inferenceLock = new ReentrantLock();
    private io.casehub.blocks.speech.StreamingSpeechDenoiserFactory denoiserFactory;
    private java.util.function.BooleanSupplier denoiserEnabled;

    WhisperSpeechToText(WhisperLibrary lib, Path modelPath) {
        this.lib = lib;
        this.ctxArena = Arena.ofShared();
        MemorySegment pathSeg = ctxArena.allocateFrom(modelPath.toString());
        try {
            MemorySegment ctxParams = ctxArena.allocate(WhisperLibrary.CTX_PARAMS_LAYOUT);
            ctxParams.fill((byte) 0);
            this.ctx = (MemorySegment) lib.whisperInitWithParams.invokeExact(pathSeg, ctxParams);
        } catch (Throwable t) {
            ctxArena.close();
            throw new SherpaException("Failed to init whisper context from " + modelPath, t);
        }
        if (ctx.equals(MemorySegment.NULL)) {
            ctxArena.close();
            throw new SherpaException("whisper_init_from_file returned null — check model: " + modelPath);
        }
    }

    public static WhisperSpeechToText withDefaults() {
        return withDefaults("ggml-base.en");
    }

    public static WhisperSpeechToText withDefaults(String modelName) {
        Path modelDir = Provisioner.ensureWhisperModel(modelName);
        String expectedFile = modelName + ".bin";
        Path modelPath = modelDir.resolve(expectedFile);
        WhisperLibrary lib = WhisperLibrary.load();
        return new WhisperSpeechToText(lib, modelPath);
    }

    @Override
    public RecognitionStream startStream(TranscriptionOptions options) {
        return new WhisperRecognitionStream(options);
    }

    public WhisperSpeechToText withStreamingDenoiser(
            io.casehub.blocks.speech.StreamingSpeechDenoiserFactory factory,
            java.util.function.BooleanSupplier enabled) {
        this.denoiserFactory = factory;
        this.denoiserEnabled = enabled;
        return this;
    }

    @Override
    public void close() {
        try {
            lib.whisperFree.invokeExact(ctx);
        } catch (Throwable t) {
            // cleanup
        }
        ctxArena.close();
    }

    private final class WhisperRecognitionStream implements RecognitionStream {

        private final @Nullable String vocabularyHint;
        private final String language;
        private float[] buffer = new float[16000]; // start at 1s, grow as needed
        private int sampleCount = 0;
        private String lastPartial = "";
        private long lastPartialTime = 0;
        private boolean closed = false;
        private final io.casehub.blocks.speech.StreamingSpeechDenoiser denoiser;

        WhisperRecognitionStream(TranscriptionOptions options) {
            this.vocabularyHint = options.vocabularyHint();
            this.language = options.languageHint() != null ? options.languageHint() : "en";
            this.denoiser = (denoiserFactory != null) ? denoiserFactory.create() : null;
        }

        @Override
        public void acceptSamples(float[] samples, int sampleRate) {
            if (closed) return;
            if (sampleRate != 16000) {
                throw new IllegalArgumentException("Whisper requires 16kHz audio, got " + sampleRate);
            }
            float[] processed = samples;
            if (denoiser != null && denoiserEnabled != null && denoiserEnabled.getAsBoolean()) {
                processed = denoiser.processChunk(samples, sampleRate);
            }
            int newCount = sampleCount + processed.length;
            if (newCount > MAX_BUFFER_SAMPLES) {
                int drop = newCount - MAX_BUFFER_SAMPLES;
                System.arraycopy(buffer, drop, buffer, 0, sampleCount - drop);
                sampleCount -= drop;
                newCount = sampleCount + processed.length;
            }
            if (newCount > buffer.length) {
                int newLen = Math.min(MAX_BUFFER_SAMPLES, Math.max(buffer.length * 2, newCount));
                float[] grown = new float[newLen];
                System.arraycopy(buffer, 0, grown, 0, sampleCount);
                buffer = grown;
            }
            System.arraycopy(processed, 0, buffer, sampleCount, processed.length);
            sampleCount += processed.length;
        }

        @Override
        public boolean isEndpointDetected() {
            return false;
        }

        @Override
        public String partialResult() {
            if (sampleCount == 0) return "";
            long now = System.currentTimeMillis();
            if (now - lastPartialTime < PARTIAL_THROTTLE_MS) {
                return lastPartial;
            }
            lastPartialTime = now;
            lastPartial = runInference(null);
            return lastPartial;
        }

        @Override
        public TranscriptionResult finalResult() {
            if (sampleCount == 0) {
                return new TranscriptionResult("", language, 0.0);
            }
            String text = runInference(vocabularyHint);
            return new TranscriptionResult(text, language, 1.0);
        }

        @Override
        public void close() {
            closed = true;
            buffer = null;
            sampleCount = 0;
            if (denoiser != null) { denoiser.close(); }
        }

        private String runInference(@Nullable String initialPrompt) {
            inferenceLock.lock();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment samplesSeg = arena.allocate(ValueLayout.JAVA_FLOAT, sampleCount);
                MemorySegment.copy(buffer, 0, samplesSeg, ValueLayout.JAVA_FLOAT, 0, sampleCount);

                int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
                MemorySegment langSeg = arena.allocateFrom(language);
                MemorySegment promptSeg = (initialPrompt != null && !initialPrompt.isEmpty())
                        ? arena.allocateFrom(initialPrompt) : MemorySegment.NULL;

                MemorySegment resultPtr;
                try {
                    resultPtr = (MemorySegment) lib.shimTranscribe.invokeExact(
                            ctx, samplesSeg, sampleCount, langSeg, promptSeg, threads);
                } catch (Throwable t) {
                    throw new SherpaException("shim_whisper_transcribe failed", t);
                }
                try {
                    return resultPtr.reinterpret(Long.MAX_VALUE).getString(0).trim();
                } finally {
                    try { lib.shimFreeText.invokeExact(resultPtr); } catch (Throwable ignored) {}
                }
            } finally {
                inferenceLock.unlock();
            }
        }
    }
}
