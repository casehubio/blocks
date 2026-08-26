package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.TextFilter;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;

import static java.lang.foreign.ValueLayout.JAVA_LONG;

public final class GectorFilter implements TextFilter, AutoCloseable {

    public static final String NAME = "grammar";
    public static final int DESTRUCTIVENESS = 3;

    private final GectorConfig config;
    private final SentencePieceTokenizer tokenizer;
    private final OnnxRuntimeLibrary.Session session;

    public GectorFilter(GectorConfig config, OnnxRuntimeLibrary lib) throws IOException {
        this.config = config;
        this.tokenizer = new SentencePieceTokenizer(config.spModelPath());
        this.session = lib.createSession(config.modelPath(), config.numThreads());
    }

    @Override
    public String apply(String text) {
        var sentences = WordTokenizer.splitSentences(text);
        if (sentences.isEmpty()) return text;
        var corrected = new ArrayList<String>();
        for (String sentence : sentences) {
            corrected.add(correctSentence(sentence));
        }
        return String.join(" ", corrected);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int destructiveness() {
        return DESTRUCTIVENESS;
    }

    @Override
    public void close() {
        session.close();
    }

    private String correctSentence(String sentence) {
        List<String> words = WordTokenizer.tokenize(sentence);
        if (words.isEmpty()) return sentence;

        for (int iter = 0; iter < config.maxIterations(); iter++) {
            int[] wordTags = predictTags(words);
            var result = GectorTagApplier.apply(words, wordTags, config);
            if (!result.changed()) break;
            words = result.tokens();
        }
        return String.join(" ", words);
    }

    private int[] predictTags(List<String> words) {
        int clsId = tokenizer.pieceToId("<s>");
        int sepId = tokenizer.pieceToId("</s>");

        var allIds = new ArrayList<Integer>();
        var wordBoundaries = new int[words.size()];
        allIds.add(clsId);

        for (int w = 0; w < words.size(); w++) {
            wordBoundaries[w] = allIds.size();
            int[] subIds = tokenizer.encode(words.get(w));
            for (int id : subIds) allIds.add(id);
        }
        allIds.add(sepId);

        int seqLen = Math.min(allIds.size(), 512);

        try (Arena arena = Arena.ofConfined()) {
            long[] inputData = new long[seqLen];
            long[] maskData = new long[seqLen];
            for (int i = 0; i < seqLen; i++) {
                inputData[i] = allIds.get(i);
                maskData[i] = 1;
            }

            MemorySegment inputSeg = arena.allocateFrom(JAVA_LONG, inputData);
            MemorySegment maskSeg = arena.allocateFrom(JAVA_LONG, maskData);

            long[] shape = {1, seqLen};
            long dataBytes = (long) seqLen * JAVA_LONG.byteSize();
            MemorySegment inputTensor = session.createTensor(
                    inputSeg, dataBytes, shape, OnnxRuntimeLibrary.INT64, arena);
            MemorySegment maskTensor = session.createTensor(
                    maskSeg, dataBytes, shape, OnnxRuntimeLibrary.INT64, arena);

            MemorySegment[] outputs = session.runRaw(
                    new String[]{"input_ids", "attention_mask"},
                    new MemorySegment[]{inputTensor, maskTensor},
                    new String[]{"logits"},
                    arena);

            MemorySegment logitsPtr = session.getTensorData(outputs[0], arena);
            long elemCount = session.tensorElementCount(outputs[0], arena);
            float[] logits = logitsPtr.reinterpret(elemCount * ValueLayout.JAVA_FLOAT.byteSize())
                    .toArray(ValueLayout.JAVA_FLOAT);

            int numTags = config.tagVocabulary().size();
            int[] subwordTags = new int[seqLen];
            int keepId = config.keepTagId();

            for (int pos = 0; pos < seqLen; pos++) {
                float[] posLogits = new float[numTags];
                System.arraycopy(logits, pos * numTags, posLogits, 0, numTags);
                float[] probs = softmax(posLogits);

                boolean forceKeep = false;
                if (config.keepConfidence() > 0 && probs[keepId] > config.keepConfidence()) {
                    forceKeep = true;
                }
                if (config.minErrorProb() > 0 && (1 - probs[keepId]) < config.minErrorProb()) {
                    forceKeep = true;
                }
                subwordTags[pos] = forceKeep ? keepId : argmax(probs);
            }

            for (var output : outputs) session.releaseValue(output);

            return aggregateSubwordTags(subwordTags, wordBoundaries, words.size());
        }
    }

    static float[] softmax(float[] logits) {
        float max = Float.NEGATIVE_INFINITY;
        for (float v : logits) if (v > max) max = v;

        float sum = 0;
        float[] result = new float[logits.length];
        for (int i = 0; i < logits.length; i++) {
            result[i] = (float) Math.exp(logits[i] - max);
            sum += result[i];
        }
        for (int i = 0; i < result.length; i++) {
            result[i] /= sum;
        }
        return result;
    }

    static int[] aggregateSubwordTags(int[] subwordTags, int[] wordBoundaries, int wordCount) {
        int[] wordTags = new int[wordCount];
        for (int w = 0; w < wordCount; w++) {
            wordTags[w] = subwordTags[wordBoundaries[w]];
        }
        return wordTags;
    }

    static int[] buildWordBoundaries(int[] ids, String[] pieces) {
        var boundaries = new ArrayList<Integer>();
        for (int i = 0; i < pieces.length; i++) {
            if (pieces[i].charAt(0) == SentencePieceTokenizer.SPACE_SYMBOL) {
                boundaries.add(i);
            }
        }
        return boundaries.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int argmax(float[] values) {
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[best]) best = i;
        }
        return best;
    }
}
