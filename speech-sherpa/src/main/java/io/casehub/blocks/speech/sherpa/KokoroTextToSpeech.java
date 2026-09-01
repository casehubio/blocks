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

public final class KokoroTextToSpeech implements TextToSpeechService {

    private final KokoroConfig config;
    private final SherpaLibrary lib;

    public static KokoroTextToSpeech withDefaults() {
        return withDefaults(0);
    }

    public static KokoroTextToSpeech withDefaults(int voiceId) {
        Provisioner.ensureNativeLibrary();
        Path modelDir = Provisioner.ensureKokoroModel("kokoro-multi-lang-v1_1");
        return new KokoroTextToSpeech(KokoroConfig.defaults(modelDir, voiceId));
    }

    public static void ensureProvisioned() {
        Provisioner.ensureNativeLibrary();
        Provisioner.ensureKokoroModel("kokoro-multi-lang-v1_1");
    }

    public KokoroTextToSpeech(KokoroConfig config) {
        this(config, SherpaLibrary.load());
    }

    KokoroTextToSpeech(KokoroConfig config, SherpaLibrary lib) {
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
                throw new SherpaException("Failed to create Kokoro TTS engine", t);
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
        int speakerId = config.voiceId();
        float speed = config.lengthScale();

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

            byte[] audioData = WavWriter.encode(samples, sampleRate, 1);
            List<PhonemeTiming> phonemes = List.of();

            String format = options.audioFormat() != null ? options.audioFormat() : "wav";
            return new SynthesisResult(audioData, format, phonemes);
        } finally {
            destroyQuietly(() -> lib.destroyGeneratedAudio.invokeExact(audioPtr));
        }
    }

    private MemorySegment buildTtsConfig(Arena arena) {
        MemorySegment seg = arena.allocate(SherpaLayouts.CONFIG_ALLOC_SIZE);
        seg.fill((byte) 0);

        Path modelDir = config.modelDir();

        seg.set(ValueLayout.ADDRESS, SherpaLayouts.KOKORO_MODEL,
                arena.allocateFrom(modelDir.resolve("model.onnx").toString()));
        seg.set(ValueLayout.ADDRESS, SherpaLayouts.KOKORO_VOICES,
                arena.allocateFrom(modelDir.resolve("voices.bin").toString()));
        seg.set(ValueLayout.ADDRESS, SherpaLayouts.KOKORO_TOKENS,
                arena.allocateFrom(modelDir.resolve("tokens.txt").toString()));
        seg.set(ValueLayout.ADDRESS, SherpaLayouts.KOKORO_DATA_DIR,
                arena.allocateFrom(modelDir.resolve("espeak-ng-data").toString()));
        seg.set(ValueLayout.JAVA_FLOAT, SherpaLayouts.KOKORO_LENGTH_SCALE, config.lengthScale());

        seg.set(ValueLayout.JAVA_INT, SherpaLayouts.TTS_NUM_THREADS, config.numThreads());
        seg.set(ValueLayout.ADDRESS, SherpaLayouts.TTS_PROVIDER,
                arena.allocateFrom(config.provider()));

        return seg;
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
