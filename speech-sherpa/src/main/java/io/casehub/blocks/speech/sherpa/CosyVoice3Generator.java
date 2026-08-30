package io.casehub.blocks.speech.sherpa;

import org.jspecify.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

final class CosyVoice3Generator implements TtsGenerator {

    @FunctionalInterface
    interface TextEmbedder {
        float[] embed(int[] tokenIds);
    }

    @FunctionalInterface
    interface SpeechEmbedder {
        float[] embed(int[] tokenIds);
    }

    record BackboneResult(float[] hidden, float[] kvCache) {}

    @FunctionalInterface
    interface BackboneInitial {
        BackboneResult run(float[] inputEmbedding, int seqLen, float[] attentionMask);
    }

    @FunctionalInterface
    interface BackboneDecode {
        BackboneResult run(float[] tokenEmbedding, float[] attentionMask, float[] kvCache);
    }

    @FunctionalInterface
    interface LogitDecoder {
        float[] decode(float[] hidden);
    }

    private final TextEmbedder textEmbedder;
    private final SpeechEmbedder speechEmbedder;
    private final BackboneInitial backboneInitial;
    private final BackboneDecode backboneDecode;
    private final LogitDecoder logitDecoder;
    private final CosyVoice3Manifest manifest;
    private final TtsTokenizer tokenizer;
    private final @Nullable Random rng;

    CosyVoice3Generator(TextEmbedder textEmbedder, SpeechEmbedder speechEmbedder,
                         BackboneInitial backboneInitial, BackboneDecode backboneDecode,
                         LogitDecoder logitDecoder, CosyVoice3Manifest manifest,
                         TtsTokenizer tokenizer, @Nullable Random rng) {
        this.textEmbedder = textEmbedder;
        this.speechEmbedder = speechEmbedder;
        this.backboneInitial = backboneInitial;
        this.backboneDecode = backboneDecode;
        this.logitDecoder = logitDecoder;
        this.manifest = manifest;
        this.tokenizer = tokenizer;
        this.rng = rng;
    }

    static CosyVoice3Generator fromSessions(OnnxRuntimeLibrary.Session textEmbedding,
                                             OnnxRuntimeLibrary.Session speechEmbedding,
                                             OnnxRuntimeLibrary.Session llmInitial,
                                             OnnxRuntimeLibrary.Session llmDecode,
                                             OnnxRuntimeLibrary.Session llmDecoder,
                                             CosyVoice3Manifest manifest,
                                             CosyVoice3Tokenizer tokenizer) {
        int hiddenDim = manifest.hiddenDim();

        TextEmbedder textEmb = tokenIds -> {
            try (var arena = Arena.ofConfined()) {
                long[] longIds = new long[tokenIds.length];
                for (int i = 0; i < tokenIds.length; i++) longIds[i] = tokenIds[i];
                var data = arena.allocateFrom(ValueLayout.JAVA_LONG, longIds);
                var tensor = textEmbedding.createTensor(data,
                        (long) tokenIds.length * ValueLayout.JAVA_LONG.byteSize(),
                        new long[]{1, tokenIds.length}, OnnxRuntimeLibrary.INT64, arena);
                var outputs = textEmbedding.runRaw(
                        new String[]{textEmbedding.inputName(0)},
                        new MemorySegment[]{tensor},
                        new String[]{textEmbedding.outputName(0)},
                        arena);
                try {
                    long count = textEmbedding.tensorElementCount(outputs[0], arena);
                    return textEmbedding.getTensorData(outputs[0], arena)
                            .reinterpret(count * ValueLayout.JAVA_FLOAT.byteSize())
                            .toArray(ValueLayout.JAVA_FLOAT);
                } finally {
                    for (var out : outputs) textEmbedding.releaseValue(out);
                    textEmbedding.releaseValue(tensor);
                }
            }
        };

        SpeechEmbedder speechEmb = tokenIds -> {
            try (var arena = Arena.ofConfined()) {
                long[] longIds = new long[tokenIds.length];
                for (int i = 0; i < tokenIds.length; i++) longIds[i] = tokenIds[i];
                var data = arena.allocateFrom(ValueLayout.JAVA_LONG, longIds);
                var tensor = speechEmbedding.createTensor(data,
                        (long) tokenIds.length * ValueLayout.JAVA_LONG.byteSize(),
                        new long[]{1, tokenIds.length}, OnnxRuntimeLibrary.INT64, arena);
                var outputs = speechEmbedding.runRaw(
                        new String[]{speechEmbedding.inputName(0)},
                        new MemorySegment[]{tensor},
                        new String[]{speechEmbedding.outputName(0)},
                        arena);
                try {
                    long count = speechEmbedding.tensorElementCount(outputs[0], arena);
                    return speechEmbedding.getTensorData(outputs[0], arena)
                            .reinterpret(count * ValueLayout.JAVA_FLOAT.byteSize())
                            .toArray(ValueLayout.JAVA_FLOAT);
                } finally {
                    for (var out : outputs) speechEmbedding.releaseValue(out);
                    speechEmbedding.releaseValue(tensor);
                }
            }
        };

        BackboneInitial initial = (inputEmbedding, seqLen, attentionMask) -> {
            try (var arena = Arena.ofConfined()) {
                var embData = arena.allocateFrom(ValueLayout.JAVA_FLOAT, inputEmbedding);
                var embTensor = llmInitial.createTensor(embData,
                        (long) inputEmbedding.length * ValueLayout.JAVA_FLOAT.byteSize(),
                        new long[]{1, seqLen, hiddenDim}, OnnxRuntimeLibrary.FLOAT, arena);

                var maskData = arena.allocateFrom(ValueLayout.JAVA_FLOAT, attentionMask);
                var maskTensor = llmInitial.createTensor(maskData,
                        (long) attentionMask.length * ValueLayout.JAVA_FLOAT.byteSize(),
                        new long[]{1, seqLen}, OnnxRuntimeLibrary.FLOAT, arena);

                var outputs = llmInitial.runRaw(
                        new String[]{llmInitial.inputName(0), llmInitial.inputName(1)},
                        new MemorySegment[]{embTensor, maskTensor},
                        new String[]{llmInitial.outputName(0), llmInitial.outputName(1)},
                        arena);
                try {
                    long hiddenCount = llmInitial.tensorElementCount(outputs[0], arena);
                    float[] allHidden = llmInitial.getTensorData(outputs[0], arena)
                            .reinterpret(hiddenCount * ValueLayout.JAVA_FLOAT.byteSize())
                            .toArray(ValueLayout.JAVA_FLOAT);
                    float[] lastHidden = new float[hiddenDim];
                    System.arraycopy(allHidden, (seqLen - 1) * hiddenDim, lastHidden, 0, hiddenDim);

                    long kvCount = llmInitial.tensorElementCount(outputs[1], arena);
                    float[] kvCache = llmInitial.getTensorData(outputs[1], arena)
                            .reinterpret(kvCount * ValueLayout.JAVA_FLOAT.byteSize())
                            .toArray(ValueLayout.JAVA_FLOAT);

                    return new BackboneResult(lastHidden, kvCache);
                } finally {
                    for (var out : outputs) llmInitial.releaseValue(out);
                    llmInitial.releaseValue(embTensor);
                    llmInitial.releaseValue(maskTensor);
                }
            }
        };

        BackboneDecode decode = (tokenEmbedding, attentionMask, kvCache) -> {
            try (var arena = Arena.ofConfined()) {
                var embData = arena.allocateFrom(ValueLayout.JAVA_FLOAT, tokenEmbedding);
                var embTensor = llmDecode.createTensor(embData,
                        (long) tokenEmbedding.length * ValueLayout.JAVA_FLOAT.byteSize(),
                        new long[]{1, 1, hiddenDim}, OnnxRuntimeLibrary.FLOAT, arena);

                var maskData = arena.allocateFrom(ValueLayout.JAVA_FLOAT, attentionMask);
                var maskTensor = llmDecode.createTensor(maskData,
                        (long) attentionMask.length * ValueLayout.JAVA_FLOAT.byteSize(),
                        new long[]{1, attentionMask.length}, OnnxRuntimeLibrary.FLOAT, arena);

                int kvHeadDim = manifest.kvHeadDim();
                int numLlmLayers = manifest.numLlmLayers();
                int kvSeqLen = kvCache.length / (numLlmLayers * 2 * 2 * kvHeadDim);
                var kvData = arena.allocateFrom(ValueLayout.JAVA_FLOAT, kvCache);
                var kvTensor = llmDecode.createTensor(kvData,
                        (long) kvCache.length * ValueLayout.JAVA_FLOAT.byteSize(),
                        new long[]{(long) numLlmLayers * 2, 1, 2, kvSeqLen, kvHeadDim},
                        OnnxRuntimeLibrary.FLOAT, arena);

                var outputs = llmDecode.runRaw(
                        new String[]{llmDecode.inputName(0), llmDecode.inputName(1), llmDecode.inputName(2)},
                        new MemorySegment[]{embTensor, maskTensor, kvTensor},
                        new String[]{llmDecode.outputName(0), llmDecode.outputName(1)},
                        arena);
                try {
                    long hiddenCount = llmDecode.tensorElementCount(outputs[0], arena);
                    float[] hidden = llmDecode.getTensorData(outputs[0], arena)
                            .reinterpret(hiddenCount * ValueLayout.JAVA_FLOAT.byteSize())
                            .toArray(ValueLayout.JAVA_FLOAT);

                    long newKvCount = llmDecode.tensorElementCount(outputs[1], arena);
                    float[] newKvCache = llmDecode.getTensorData(outputs[1], arena)
                            .reinterpret(newKvCount * ValueLayout.JAVA_FLOAT.byteSize())
                            .toArray(ValueLayout.JAVA_FLOAT);

                    return new BackboneResult(hidden, newKvCache);
                } finally {
                    for (var out : outputs) llmDecode.releaseValue(out);
                    llmDecode.releaseValue(embTensor);
                    llmDecode.releaseValue(maskTensor);
                    llmDecode.releaseValue(kvTensor);
                }
            }
        };

        LogitDecoder logitDec = hidden -> {
            try (var arena = Arena.ofConfined()) {
                var hiddenData = arena.allocateFrom(ValueLayout.JAVA_FLOAT, hidden);
                return llmDecoder.runFloat(
                        new String[]{llmDecoder.inputName(0)},
                        new MemorySegment[]{hiddenData},
                        new long[][]{{1, 1, hiddenDim}},
                        new String[]{llmDecoder.outputName(0)},
                        arena);
            }
        };

        return new CosyVoice3Generator(textEmb, speechEmb, initial, decode, logitDec,
                manifest, tokenizer, null);
    }

    @Override
    public GeneratorOutput generate(int[] tokens, @Nullable VoiceData voiceData,
                                    GeneratorConfig config) {
        if (voiceData == null) {
            throw new IllegalArgumentException("CosyVoice3 requires voice data for synthesis");
        }
        if (!(voiceData instanceof VoiceData.EmbeddingVoiceData evd)) {
            throw new IllegalArgumentException("CosyVoice3 requires EmbeddingVoiceData, got "
                    + voiceData.getClass().getSimpleName());
        }

        int[] promptTokens = tokenizer.encode(evd.promptText());
        int[] allTextTokens = concatInt(promptTokens, tokens);

        float[] textEmb = textEmbedder.embed(allTextTokens);
        float[] sosEmb = speechEmbedder.embed(new int[]{manifest.sosId()});
        float[] taskIdEmb = speechEmbedder.embed(new int[]{manifest.taskId()});
        float[] promptSpeechEmb = speechEmbedder.embed(evd.speechTokens());

        float[] inputEmb = concatFloat(sosEmb, textEmb, taskIdEmb, promptSpeechEmb);
        int seqLen = 1 + allTextTokens.length + 1 + evd.speechTokens().length;

        float[] mask = new float[seqLen];
        Arrays.fill(mask, 1.0f);

        BackboneResult result = backboneInitial.run(inputEmb, seqLen, mask);

        float[] logits = logitDecoder.decode(result.hidden());
        checkLogits(logits);

        int minLen = Math.max(config.minTokens(), tokens.length * 2);
        int maxLen = Math.min(config.maxTokens(), tokens.length * 20);

        var speechTokens = new ArrayList<Integer>();
        float[] kvCache = result.kvCache();
        int currentSeqLen = seqLen;
        Random random = rng != null ? rng : ThreadLocalRandom.current();

        while (speechTokens.size() < maxLen) {
            if (speechTokens.size() < minLen) {
                logits[manifest.eosId()] = Float.NEGATIVE_INFINITY;
            }

            int token = DualARLoop.sample(logits, config.temperature(), config.topP(),
                    config.topK(), random);

            if (token == manifest.eosId()) break;

            speechTokens.add(token);

            if (speechTokens.size() >= maxLen) break;

            float[] tokenEmb = speechEmbedder.embed(new int[]{token});
            currentSeqLen++;
            float[] newMask = new float[currentSeqLen];
            Arrays.fill(newMask, 1.0f);

            BackboneResult step = backboneDecode.run(tokenEmb, newMask, kvCache);
            kvCache = step.kvCache();

            logits = logitDecoder.decode(step.hidden());
            checkLogits(logits);
        }

        return new GeneratorOutput.SpeechTokenOutput(
                speechTokens.stream().mapToInt(Integer::intValue).toArray());
    }

    private static void checkLogits(float[] logits) {
        for (float v : logits) {
            if (Float.isNaN(v)) {
                throw new SherpaException("NaN detected in logits — model failure");
            }
            if (Float.isInfinite(v)) {
                throw new SherpaException("Inf detected in logits — model failure");
            }
        }
    }

    private static int[] concatInt(int[] a, int[] b) {
        int[] result = new int[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private static float[] concatFloat(float[]... arrays) {
        int total = 0;
        for (float[] a : arrays) total += a.length;
        float[] result = new float[total];
        int pos = 0;
        for (float[] a : arrays) {
            System.arraycopy(a, 0, result, pos, a.length);
            pos += a.length;
        }
        return result;
    }
}
