package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.RecognitionStream;
import io.casehub.blocks.speech.StreamingSpeechToTextService;
import io.casehub.blocks.speech.TranscriptionOptions;
import io.casehub.blocks.speech.TranscriptionResult;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.Objects;

public final class SherpaOnnxStreamingSpeechToText implements StreamingSpeechToTextService {

    private final SherpaConfig config;
    private final SherpaLibrary lib;
    private final MemorySegment recognizer;
    private final Arena recognizerArena;

    public SherpaOnnxStreamingSpeechToText(SherpaConfig config) {
        this(config, SherpaLibrary.load());
    }

    SherpaOnnxStreamingSpeechToText(SherpaConfig config, SherpaLibrary lib) {
        this.config = Objects.requireNonNull(config);
        this.lib = lib;
        this.recognizerArena = Arena.ofShared();
        this.recognizer = createRecognizer();
    }

    @Override
    public RecognitionStream startStream(TranscriptionOptions options) {
        Objects.requireNonNull(options, "options");
        return new SherpaRecognitionStream();
    }

    public void close() {
        if (recognizer != null && !recognizer.equals(MemorySegment.NULL)) {
            try {
                lib.destroyOnlineRecognizer.invokeExact(recognizer);
            } catch (Throwable t) {
                // cleanup
            }
        }
        recognizerArena.close();
    }

    private MemorySegment createRecognizer() {
        MemorySegment configSeg = recognizerArena.allocate(SherpaLayouts.CONFIG_ALLOC_SIZE);
        configSeg.fill((byte) 0);

        configSeg.set(ValueLayout.JAVA_INT, SherpaLayouts.FEAT_SAMPLE_RATE, 16000);
        configSeg.set(ValueLayout.JAVA_INT, SherpaLayouts.FEAT_FEATURE_DIM, 80);

        Path modelDir = config.modelDir();
        configSeg.set(ValueLayout.ADDRESS, SherpaLayouts.ONLINE_TRANSDUCER_ENCODER,
                recognizerArena.allocateFrom(findModel(modelDir, "encoder")));
        configSeg.set(ValueLayout.ADDRESS, SherpaLayouts.ONLINE_TRANSDUCER_DECODER,
                recognizerArena.allocateFrom(findModel(modelDir, "decoder")));
        configSeg.set(ValueLayout.ADDRESS, SherpaLayouts.ONLINE_TRANSDUCER_JOINER,
                recognizerArena.allocateFrom(findModel(modelDir, "joiner")));
        configSeg.set(ValueLayout.ADDRESS, SherpaLayouts.ONLINE_MODEL_TOKENS,
                recognizerArena.allocateFrom(modelDir.resolve("tokens.txt").toString()));
        configSeg.set(ValueLayout.JAVA_INT, SherpaLayouts.ONLINE_MODEL_NUM_THREADS, config.numThreads());
        configSeg.set(ValueLayout.ADDRESS, SherpaLayouts.ONLINE_MODEL_PROVIDER,
                recognizerArena.allocateFrom(config.provider()));

        configSeg.set(ValueLayout.JAVA_INT, SherpaLayouts.ONLINE_ENABLE_ENDPOINT, 1);
        configSeg.set(ValueLayout.JAVA_FLOAT, SherpaLayouts.ONLINE_RULE1_MIN_TRAILING_SILENCE, 2.4f);
        configSeg.set(ValueLayout.JAVA_FLOAT, SherpaLayouts.ONLINE_RULE2_MIN_TRAILING_SILENCE, 1.2f);
        configSeg.set(ValueLayout.JAVA_FLOAT, SherpaLayouts.ONLINE_RULE3_MIN_UTTERANCE_LENGTH, 20.0f);

        MemorySegment rec;
        try {
            rec = (MemorySegment) lib.createOnlineRecognizer.invokeExact(configSeg);
        } catch (Throwable t) {
            throw new SherpaException("Failed to create online recognizer", t);
        }
        if (rec.equals(MemorySegment.NULL)) {
            throw new SherpaException("sherpa-onnx returned null online recognizer — check model paths in " + modelDir);
        }
        return rec;
    }

    private String findModel(Path modelDir, String component) {
        try (var files = java.nio.file.Files.list(modelDir)) {
            return files
                    .filter(p -> p.getFileName().toString().contains(component))
                    .filter(p -> p.toString().endsWith(".onnx"))
                    .findFirst()
                    .orElseThrow(() -> new SherpaException("No " + component + " model found in " + modelDir))
                    .toString();
        } catch (java.io.IOException e) {
            throw new SherpaException("Failed to scan model directory: " + modelDir, e);
        }
    }

    private final class SherpaRecognitionStream implements RecognitionStream {
        private final MemorySegment stream;
        private final Arena streamArena;
        private volatile boolean closed;

        SherpaRecognitionStream() {
            this.streamArena = Arena.ofConfined();
            try {
                this.stream = (MemorySegment) lib.createOnlineStream.invokeExact(recognizer);
            } catch (Throwable t) {
                streamArena.close();
                throw new SherpaException("Failed to create online stream", t);
            }
        }

        @Override
        public void acceptSamples(float[] samples, int sampleRate) {
            if (closed) throw new IllegalStateException("Stream is closed");
            try (Arena temp = Arena.ofConfined()) {
                MemorySegment samplesSeg = temp.allocateFrom(ValueLayout.JAVA_FLOAT, samples);
                lib.onlineStreamAcceptWaveform.invokeExact(stream, sampleRate, samplesSeg, samples.length);
            } catch (Throwable t) {
                throw new SherpaException("Failed to accept waveform", t);
            }
            decode();
        }

        @Override
        public boolean isEndpointDetected() {
            if (closed) return false;
            try {
                return ((int) lib.isEndpoint.invokeExact(recognizer, stream)) != 0;
            } catch (Throwable t) {
                throw new SherpaException("Failed to check endpoint", t);
            }
        }

        @Override
        public String partialResult() {
            return readResult();
        }

        @Override
        public TranscriptionResult finalResult() {
            String text = readResult();
            return new TranscriptionResult(text, text.isEmpty() ? "" : "en", 1.0);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            try {
                lib.destroyOnlineStream.invokeExact(stream);
            } catch (Throwable t) {
                // cleanup
            }
            streamArena.close();
        }

        private void decode() {
            try {
                while (((int) lib.isOnlineStreamReady.invokeExact(recognizer, stream)) != 0) {
                    lib.decodeOnlineStream.invokeExact(recognizer, stream);
                }
            } catch (Throwable t) {
                throw new SherpaException("Failed to decode online stream", t);
            }
        }

        private String readResult() {
            if (closed) return "";
            MemorySegment result;
            try {
                result = (MemorySegment) lib.getOnlineStreamResult.invokeExact(recognizer, stream);
            } catch (Throwable t) {
                throw new SherpaException("Failed to get online stream result", t);
            }
            try {
                MemorySegment textPtr = result.reinterpret(Long.MAX_VALUE).get(ValueLayout.ADDRESS, 0);
                return textPtr.reinterpret(Long.MAX_VALUE).getString(0);
            } finally {
                try { lib.destroyOnlineRecognizerResult.invokeExact(result); } catch (Throwable t) { /* cleanup */ }
            }
        }
    }
}
