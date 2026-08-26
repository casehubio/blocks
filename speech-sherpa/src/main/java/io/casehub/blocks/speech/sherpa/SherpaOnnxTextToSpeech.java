package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.PhonemeTiming;
import io.casehub.blocks.speech.SynthesisOptions;
import io.casehub.blocks.speech.SynthesisResult;
import io.casehub.blocks.speech.TextToSpeechService;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class SherpaOnnxTextToSpeech implements TextToSpeechService {

    private final SherpaConfig config;
    private final SherpaLibrary lib;

    public SherpaOnnxTextToSpeech(SherpaConfig config) {
        this(config, SherpaLibrary.load());
    }

    public SherpaOnnxTextToSpeech(SherpaConfig config, Path libraryPath) {
        this(config, SherpaLibrary.load(libraryPath));
    }

    SherpaOnnxTextToSpeech(SherpaConfig config, SherpaLibrary lib) {
        this.config = Objects.requireNonNull(config);
        this.lib = lib;
    }

    @Override
    public SynthesisResult synthesise(String text, SynthesisOptions options) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(options, "options");

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment configSeg = buildTtsConfig(arena);
            MemorySegment tts;
            try {
                tts = (MemorySegment) lib.createTts.invokeExact(configSeg);
            } catch (Throwable t) {
                throw new SherpaException("Failed to create TTS engine", t);
            }

            if (tts.equals(MemorySegment.NULL)) {
                throw new SherpaException("sherpa-onnx returned null TTS — check model paths in " + config.modelDir());
            }

            try {
                return doSynthesise(arena, tts, text, options);
            } finally {
                destroyQuietly(() -> lib.destroyTts.invokeExact(tts));
            }
        }
    }

    private SynthesisResult doSynthesise(Arena arena, MemorySegment tts, String text, SynthesisOptions options) {
        MemorySegment textSeg = arena.allocateFrom(text);
        int speakerId = 0;
        float speed = 1.0f;

        MemorySegment audioPtr;
        try {
            audioPtr = (MemorySegment) lib.ttsGenerate.invokeExact(tts, textSeg, speakerId, speed);
        } catch (Throwable t) {
            throw new SherpaException("Failed to generate audio", t);
        }

        if (audioPtr.equals(MemorySegment.NULL)) {
            throw new SherpaException("sherpa-onnx returned null audio for text: " + text);
        }

        try {
            MemorySegment audio = audioPtr.reinterpret(SherpaLayouts.GENERATED_AUDIO.byteSize());
            int sampleCount = (int) SherpaLayouts.AUDIO_N.get(audio, 0L);
            int sampleRate = (int) SherpaLayouts.AUDIO_SAMPLE_RATE.get(audio, 0L);
            MemorySegment samplesPtr = (MemorySegment) SherpaLayouts.AUDIO_SAMPLES.get(audio, 0L);

            float[] samples = samplesPtr
                    .reinterpret((long) sampleCount * ValueLayout.JAVA_FLOAT.byteSize())
                    .toArray(ValueLayout.JAVA_FLOAT);

            String format = options.audioFormat() != null ? options.audioFormat() : "wav";
            byte[] audioData = WavWriter.encode(samples, sampleRate, 1);
            List<PhonemeTiming> phonemes = List.of();

            return new SynthesisResult(audioData, format, phonemes);
        } finally {
            destroyQuietly(() -> lib.destroyGeneratedAudio.invokeExact(audioPtr));
        }
    }

    private MemorySegment buildTtsConfig(Arena arena) {
        MemorySegment seg = arena.allocate(SherpaLayouts.CONFIG_ALLOC_SIZE);
        seg.fill((byte) 0);

        Path modelDir = config.modelDir();
        seg.set(java.lang.foreign.ValueLayout.ADDRESS, SherpaLayouts.VITS_MODEL_PATH,
                arena.allocateFrom(findTtsModel(modelDir)));
        seg.set(java.lang.foreign.ValueLayout.ADDRESS, SherpaLayouts.VITS_TOKENS,
                arena.allocateFrom(modelDir.resolve("tokens.txt").toString()));
        seg.set(java.lang.foreign.ValueLayout.ADDRESS, SherpaLayouts.VITS_DATA_DIR,
                arena.allocateFrom(modelDir.resolve("espeak-ng-data").toString()));
        seg.set(java.lang.foreign.ValueLayout.JAVA_FLOAT, SherpaLayouts.VITS_LENGTH_SCALE, 1.0f);

        seg.set(java.lang.foreign.ValueLayout.JAVA_INT, SherpaLayouts.TTS_NUM_THREADS, config.numThreads());
        seg.set(java.lang.foreign.ValueLayout.ADDRESS, SherpaLayouts.TTS_PROVIDER,
                arena.allocateFrom(config.provider()));

        return seg;
    }

    static String findTtsModel(java.nio.file.Path modelDir) {
        try (var files = java.nio.file.Files.list(modelDir)) {
            var candidates = files
                                     .filter(p -> p.getFileName().toString().endsWith(".onnx"))
                                     .sorted(java.util.Comparator.<java.nio.file.Path, Boolean>comparing(
                                                         p -> !p.getFileName().toString().equals("model.onnx"))
                                                                 .thenComparing(java.nio.file.Path::getFileName))
                                     .toList();
            if (candidates.isEmpty()) {
                throw new SherpaException("No TTS model (.onnx) found in " + modelDir);
            }
            return candidates.getFirst().toString();
        } catch (java.io.IOException e) {
            throw new SherpaException("Failed to scan model directory: " + modelDir, e);
        }
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
