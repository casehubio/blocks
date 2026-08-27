package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.PhonemeTiming;
import io.casehub.blocks.speech.SynthesisOptions;
import io.casehub.blocks.speech.SynthesisResult;
import io.casehub.blocks.speech.TextToSpeechService;

import java.nio.file.Path;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class VitsTextToSpeech implements TextToSpeechService, AutoCloseable {

    private static final System.Logger LOG = System.getLogger("casehub-speech");
    private static final int HOP_LENGTH = 256;

    private final VitsConfig config;
    private final OnnxRuntimeLibrary ort;
    private final EspeakLibrary espeak;
    private final OnnxRuntimeLibrary.Session session;
    private final boolean phonemeTimingAvailable;


    public static VitsTextToSpeech withDefaults() {
        return withDefaults("vits-piper-en_US-lessac-medium");
    }

    public static VitsTextToSpeech withDefaults(String modelName) {
        Provisioner.ensureNativeLibrary();
        Path ttsModelDir = Provisioner.ensureTtsModel(modelName);
        ModelPatcher.patch(ttsModelDir);
        Path espeakDir = Provisioner.ensureEspeak();
        var config = VitsConfig.fromModelDir(ttsModelDir);
        var ort = OnnxRuntimeLibrary.load();
        var espeak = EspeakLibrary.load(
                espeakDir.resolve(Provisioner.espeakLibName()),
                espeakDir.resolve("espeak-ng-data"));
        return new VitsTextToSpeech(config, ort, espeak);
    }

    public VitsTextToSpeech(VitsConfig config, OnnxRuntimeLibrary ort, EspeakLibrary espeak) {
        this.config = Objects.requireNonNull(config);
        this.ort = Objects.requireNonNull(ort);
        this.espeak = Objects.requireNonNull(espeak);

        this.session = ort.createSession(config.modelPath(), config.numThreads());
        this.phonemeTimingAvailable = session.outputCount() >= 2;

        if (!phonemeTimingAvailable) {
            LOG.log(System.Logger.Level.WARNING,
                    "VITS model has {0} output(s) — phoneme timing unavailable. "
                    + "Run ModelPatcher.patch() to add duration output.",
                    session.outputCount());
        }
    }

    @Override
    public SynthesisResult synthesise(String text, SynthesisOptions options) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(options, "options");

        String ipa = espeak.textToPhonemes(text, config.espeakVoice());
        List<Integer> interspersedIds = config.tokenize(ipa);

        try (Arena arena = Arena.ofConfined()) {
            long[] inputIds = interspersedIds.stream().mapToLong(Integer::longValue).toArray();
            MemorySegment inputSeg = arena.allocateFrom(ValueLayout.JAVA_LONG, inputIds);
            long[] inputShape = {1, inputIds.length};

            MemorySegment lengthSeg = arena.allocateFrom(ValueLayout.JAVA_LONG, new long[]{inputIds.length});
            long[] lengthShape = {1};

            float[] scales = {config.noiseScale(), config.lengthScale(), config.noiseScaleW()};
            MemorySegment scalesSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, scales);
            long[] scalesShape = {3};

            MemorySegment inputTensor = session.createTensor(inputSeg,
                    inputIds.length * ValueLayout.JAVA_LONG.byteSize(), inputShape,
                    OnnxRuntimeLibrary.INT64, arena);
            MemorySegment lengthTensor = session.createTensor(lengthSeg,
                    ValueLayout.JAVA_LONG.byteSize(), lengthShape,
                    OnnxRuntimeLibrary.INT64, arena);
            MemorySegment scalesTensor = session.createTensor(scalesSeg,
                    scales.length * ValueLayout.JAVA_FLOAT.byteSize(), scalesShape,
                    OnnxRuntimeLibrary.FLOAT, arena);

            boolean wantPhonemes = options.includePhonemes() && phonemeTimingAvailable;
            String[] outputNames = wantPhonemes
                    ? new String[]{"output", ModelPatcher.DURATION_OUTPUT_NAME}
                    : new String[]{"output"};

            MemorySegment[] outputs = session.runRaw(
                    new String[]{"input", "input_lengths", "scales"},
                    new MemorySegment[]{inputTensor, lengthTensor, scalesTensor},
                    outputNames, arena);

            try {
                MemorySegment audioData = session.getTensorData(outputs[0], arena);
                long audioSamples = getTensorElementCount(outputs[0], arena);
                float[] samples = audioData
                        .reinterpret(audioSamples * ValueLayout.JAVA_FLOAT.byteSize())
                        .toArray(ValueLayout.JAVA_FLOAT);

                String format = options.audioFormat() != null ? options.audioFormat() : "wav";
                byte[] wavData = WavWriter.encode(samples, config.sampleRate(), 1);

                List<PhonemeTiming> phonemes = List.of();
                if (wantPhonemes && outputs.length >= 2) {
                    phonemes = extractPhonemeTimings(outputs[1], interspersedIds, arena);
                }

                return new SynthesisResult(wavData, format, phonemes);
            } finally {
                for (MemorySegment output : outputs) {
                    session.releaseValue(output);
                }
                session.releaseValue(inputTensor);
                session.releaseValue(lengthTensor);
                session.releaseValue(scalesTensor);
            }
        }
    }

    private List<PhonemeTiming> extractPhonemeTimings(MemorySegment durValue,
                                                      List<Integer> interspersedIds,
                                                      Arena arena) {
        MemorySegment durData = session.getTensorData(durValue, arena);
        long durCount = getTensorElementCount(durValue, arena);
        float[] durFrames = durData
                .reinterpret(durCount * ValueLayout.JAVA_FLOAT.byteSize())
                .toArray(ValueLayout.JAVA_FLOAT);

        int padId = config.padId();
        int bosId = config.bosId();
        int eosId = config.eosId();

        List<PhonemeTiming> phonemes = new ArrayList<>();
        double cumulativeMs = 0.0;

        int limit = Math.min(interspersedIds.size(), durFrames.length);
        for (int i = 0; i < limit; i++) {
            double durationMs = durFrames[i] * HOP_LENGTH * 1000.0 / config.sampleRate();
            int id = interspersedIds.get(i);

            if (id != padId && id != bosId && id != eosId) {
                long startMs = Math.round(cumulativeMs);
                cumulativeMs += durationMs;
                long endMs = Math.round(cumulativeMs);

                String phoneme = config.idToPhoneme(id);
                if (phoneme != null && endMs > startMs) {
                    phonemes.add(new PhonemeTiming(phoneme, startMs, endMs));
                }
            } else {
                cumulativeMs += durationMs;
            }
        }

        return phonemes;
    }

    private long getTensorElementCount(MemorySegment value, Arena arena) {
        return session.tensorElementCount(value, arena);
    }

    @Override
    public void close() {
        session.close();
    }
}
