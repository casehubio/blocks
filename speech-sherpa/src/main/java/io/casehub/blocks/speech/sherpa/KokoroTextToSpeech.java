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

public final class KokoroTextToSpeech implements TextToSpeechService, AutoCloseable {

    private static final System.Logger LOG = System.getLogger("casehub-speech");

    private final KokoroConfig                             config;
    private final SherpaLibrary                            lib;
    private final Arena                                    engineArena;
    private final MemorySegment                            ttsHandle;
    private final java.util.concurrent.locks.ReentrantLock synthesiseLock = new java.util.concurrent.locks.ReentrantLock();

    public static KokoroTextToSpeech withDefaults() {
        return withDefaults(0);
    }

    public static KokoroTextToSpeech withDefaults(int voiceId) {
        Provisioner.ensureNativeLibrary();
        Path modelDir = Provisioner.ensureKokoroModel("kokoro-multi-lang-v1_0");
        return new KokoroTextToSpeech(KokoroConfig.defaults(modelDir, voiceId));
    }

    public static void ensureProvisioned() {
        Provisioner.ensureNativeLibrary();
        Provisioner.ensureKokoroModel("kokoro-multi-lang-v1_0");
    }

    public KokoroTextToSpeech(KokoroConfig config) {
        this(config, SherpaLibrary.load());
    }

    KokoroTextToSpeech(KokoroConfig config, SherpaLibrary lib) {
        this.config      = Objects.requireNonNull(config);
        this.lib         = lib;
        this.engineArena = Arena.ofShared();
        MemorySegment configSeg = buildTtsConfig(engineArena);
        try {
            this.ttsHandle = (MemorySegment) lib.createTts.invokeExact(configSeg);
        } catch (Throwable t) {
            engineArena.close();
            throw new SherpaException("Failed to create Kokoro TTS engine", t);
        }
        if (ttsHandle.equals(MemorySegment.NULL)) {
            engineArena.close();
            throw new SherpaException("sherpa-onnx returned null TTS — check model paths in " + config.modelDir());
        }
        LOG.log(System.Logger.Level.INFO, "Kokoro TTS engine cached — voice {0}, model {1}",
                config.voiceId(), config.modelDir().getFileName());
    }

    @Override
    public void warmUp() {
        // Engine already created in constructor — this is a no-op.
        // Exists so pre-warm callers have a uniform API.
    }

    public TextToSpeechService forVoice(int voiceId) {
        return new KokoroVoice(this, voiceId);
    }

    SynthesisResult synthesise(String text, int voiceId, SynthesisOptions options) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(options, "options");

        synthesiseLock.lock();
        try (Arena arena = Arena.ofConfined()) {
            return doSynthesise(arena, ttsHandle, text, voiceId, options);
        } finally {
            synthesiseLock.unlock();
        }
    }


    @Override
    public SynthesisResult synthesise(String text, SynthesisOptions options) {return synthesise(text, config.voiceId(), options);}

    private SynthesisResult doSynthesise(Arena arena, MemorySegment tts, String text, int voiceId, SynthesisOptions options) {
        MemorySegment textSeg = arena.allocateFrom(text);
        float         speed   = config.lengthScale();

        MemorySegment audioPtr;
        try {
            audioPtr = (MemorySegment) lib.ttsGenerate.invokeExact(tts, textSeg, voiceId, speed);
        } catch (Throwable t) {
            throw new SherpaException("Failed to generate audio", t);
        }

        if (audioPtr.equals(MemorySegment.NULL)) {
            throw new SherpaException("sherpa-onnx returned null audio for text: " + text);
        }

        try {
            MemorySegment audio       = audioPtr.reinterpret(SherpaLayouts.GENERATED_AUDIO.byteSize());
            int           sampleCount = (int) SherpaLayouts.AUDIO_N.get(audio, 0L);
            int           sampleRate  = (int) SherpaLayouts.AUDIO_SAMPLE_RATE.get(audio, 0L);
            MemorySegment samplesPtr  = (MemorySegment) SherpaLayouts.AUDIO_SAMPLES.get(audio, 0L);

            float[] samples = samplesPtr
                                      .reinterpret((long) sampleCount * ValueLayout.JAVA_FLOAT.byteSize())
                                      .toArray(ValueLayout.JAVA_FLOAT);

            byte[]              audioData = WavWriter.encode(samples, sampleRate, 1);
            List<PhonemeTiming> phonemes  = List.of();

            String format = options.audioFormat() != null ? options.audioFormat() : "wav";
            return new SynthesisResult(audioData, format, phonemes);
        } finally {
            destroyQuietly(() -> lib.destroyGeneratedAudio.invokeExact(audioPtr));
        }
    }

    @Override
    public void close() {
        synthesiseLock.lock();
        try {
            destroyQuietly(() -> lib.destroyTts.invokeExact(ttsHandle));
        } finally {
            synthesiseLock.unlock();
            engineArena.close();
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

        String lexicon = detectLexicon(modelDir);
        if (lexicon != null) {
            seg.set(ValueLayout.ADDRESS, SherpaLayouts.KOKORO_LEXICON,
                    arena.allocateFrom(lexicon));
        }

        seg.set(ValueLayout.JAVA_INT, SherpaLayouts.TTS_NUM_THREADS, config.numThreads());
        seg.set(ValueLayout.ADDRESS, SherpaLayouts.TTS_PROVIDER,
                arena.allocateFrom(config.provider()));

        return seg;
    }

    private static String detectLexicon(Path modelDir) {
        var lexicons = new java.util.ArrayList<String>();
        for (String name : List.of("lexicon-us-en.txt", "lexicon-gb-en.txt", "lexicon-zh.txt")) {
            Path p = modelDir.resolve(name);
            if (java.nio.file.Files.exists(p)) {
                lexicons.add(p.toString());
            }
        }
        return lexicons.isEmpty() ? null : String.join(",", lexicons);
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
