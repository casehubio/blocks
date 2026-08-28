package io.casehub.blocks.speech.sherpa;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

final class DualARLoop implements AutoCloseable {
    record SlowStepResult(float[] logits, float[] hidden) {}

    static final class SlowState {
        final MemorySegment cacheKeys;
        final MemorySegment cacheValues;
        final MemorySegment convStates;
        final MemorySegment ssmStates;

        SlowState(MemorySegment cacheKeys, MemorySegment cacheValues,
                  MemorySegment convStates, MemorySegment ssmStates) {
            this.cacheKeys   = cacheKeys;
            this.cacheValues = cacheValues;
            this.convStates  = convStates;
            this.ssmStates   = ssmStates;
        }
    }


    private final OnnxRuntimeLibrary.Session slowAr;
    private final OnnxRuntimeLibrary.Session fastAr;
    private final RuntimeManifest manifest;
    private final String[]        slowOutputNames;
    private final String[]        fastOutputNames;


    DualARLoop(OnnxRuntimeLibrary.Session slowAr, OnnxRuntimeLibrary.Session fastAr,
               RuntimeManifest manifest) {
        this.slowAr          = slowAr;
        this.fastAr          = fastAr;
        this.manifest        = manifest;
        this.slowOutputNames = queryOutputNames(slowAr);
        this.fastOutputNames = queryOutputNames(fastAr);}

    int[][] generate(String text, String referenceText, int[] voiceCodes,
                     Audio8Config config, Audio8Tokenizer tokenizer) {
        int numCb = manifest.numCodebooks();
        long[][][] prompt = buildPrompt(text, referenceText, voiceCodes, numCb,
                                        manifest.semanticBeginId(), tokenizer);

        int promptLen = prompt[0][0].length;
        if (promptLen >= manifest.maxSeqLen()) {
            throw new SherpaException("Prompt length " + promptLen
                                      + " exceeds max sequence length " + manifest.maxSeqLen());
        }
        int maxNewTokens = Math.min(config.maxTokens(), manifest.maxSeqLen() - promptLen);

        var rng      = new Random(42);
        var frames   = new ArrayList<int[]>();
        var previous = new ArrayList<Integer>();

        try (var arena = Arena.ofConfined()) {
            SlowState      slowState = createEmptySlowState(arena);
            SlowStepResult result    = null;

            for (int pos = 0; pos < promptLen; pos++) {
                long[] column = new long[numCb + 1];
                for (int row = 0; row < numCb + 1; row++) {
                    column[row] = prompt[0][row][pos];
                }
                result = slowStep(column, pos, slowState, arena);
            }

            int     begin         = manifest.semanticBeginId();
            int     eos           = manifest.imEndId();
            int     cbSize        = manifest.codebookSize();
            boolean compactLayout = manifest.isCompactLogitsLayout();

            for (int step = 0; step < maxNewTokens; step++) {
                int semantic = sampleSemantic(result.logits(), previous, config.temperature(),
                                              config.topP(), config.topK(), rng, begin, manifest.semanticEndId(),
                                              eos, compactLayout);
                if (semantic == eos) {break;}

                previous.add(semantic);
                if (previous.size() > 10) {previous.removeFirst();}

                MemorySegment[] fastCaches = createEmptyFastCaches(arena);
                fastStep(result.hidden(), 0, true, 0, fastCaches, arena);
                int   token     = Math.min(Math.max(semantic - begin, 0), cbSize - 1);
                int[] codebooks = new int[numCb];
                codebooks[0] = token;

                for (int cb = 1; cb < numCb; cb++) {
                    float[] fastLogits = fastStep(result.hidden(), token, false, cb,
                                                  fastCaches, arena);
                    token         = sample(fastLogits, config.temperature(), config.topP(),
                                           config.topK(), rng);
                    codebooks[cb] = token;
                }
                frames.add(codebooks);

                long[] column = buildColumn(semantic, codebooks, numCb);
                result = slowStep(column, promptLen + step, slowState, arena);
            }
        }
        return frames.toArray(new int[0][]);}

    @Override
    public void close() {
        slowAr.close();
        fastAr.close();
    }

    // --- Sampling (package-private for testing) ---

    static int sample(float[] logits, float temperature, float topP, int topK, Random rng) {
        int size = logits.length;
        double[] values = new double[size];
        for (int i = 0; i < size; i++) values[i] = logits[i];

        Integer[] order = new Integer[size];
        for (int i = 0; i < size; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> Double.compare(values[b], values[a]));

        double[] sorted = new double[size];
        for (int i = 0; i < size; i++) sorted[i] = values[order[i]];

        double max = sorted[0];
        double[] base = new double[size];
        double sum = 0;
        for (int i = 0; i < size; i++) {
            base[i] = Math.exp(sorted[i] - max);
            sum += base[i];
        }
        for (int i = 0; i < size; i++) base[i] /= sum;

        double cumulative = 0;
        boolean[] remove = new boolean[size];
        for (int i = 0; i < size; i++) {
            cumulative += base[i];
            if ((cumulative > topP || i >= topK) && i > 0) {
                remove[i] = true;
            }
        }

        double[] masked = values.clone();
        for (int i = 0; i < size; i++) {
            if (remove[i]) masked[order[i]] = Double.NEGATIVE_INFINITY;
        }

        double temp = Math.max(temperature, 1e-5);
        double maxMasked = Double.NEGATIVE_INFINITY;
        for (double v : masked) if (v > maxMasked) maxMasked = v;

        double[] probs = new double[size];
        double probSum = 0;
        for (int i = 0; i < size; i++) {
            probs[i] = Math.exp((masked[i] - maxMasked) / temp);
            probSum += probs[i];
        }
        for (int i = 0; i < size; i++) probs[i] /= probSum;

        double[] noise = new double[size];
        for (int i = 0; i < size; i++) {
            noise[i] = -Math.log(Math.max(rng.nextDouble(), 1e-12));
        }

        int best = 0;
        double bestScore = probs[0] / noise[0];
        for (int i = 1; i < size; i++) {
            double score = probs[i] / noise[i];
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    static int sampleSemantic(float[] logits, List<Integer> previous,
                              float temperature, float topP, int topK, Random rng,
                              int semanticBegin, int semanticEnd, int eosId,
                              boolean compactLayout) {
        int semanticCount = semanticEnd - semanticBegin + 1;
        int[] allowedIds = new int[semanticCount + 1];
        for (int i = 0; i < semanticCount; i++) allowedIds[i] = semanticBegin + i;
        allowedIds[semanticCount] = eosId;

        float[] allowedLogits;
        if (compactLayout) {
            allowedLogits = logits;
        } else {
            allowedLogits = new float[allowedIds.length];
            for (int i = 0; i < allowedIds.length; i++) {
                int idx = allowedIds[i];
                allowedLogits[i] = idx < logits.length ? logits[idx] : Float.NEGATIVE_INFINITY;
            }
        }

        if (allowedLogits.length != allowedIds.length) {
            throw new SherpaException("Unexpected slow logits size: " + allowedLogits.length
                    + ", expected " + allowedIds.length);
        }

        int normalIndex = sample(allowedLogits, temperature, topP, topK, rng);
        int normal = allowedIds[normalIndex];

        int highIndex = sample(allowedLogits, 1.0f, 0.9f, topK, rng);
        int high = allowedIds[highIndex];

        if (normal >= semanticBegin && normal <= semanticEnd && previous.contains(normal)) {
            return high;
        }
        return normal;
    }

    // --- Prompt building (package-private for testing) ---

    static long[][][] buildPrompt(String targetText, String referenceText,
                                  int[] referenceCodes, int numCodebooks,
                                  int semanticBeginId, Audio8Tokenizer tokenizer) {
        String refFormatted = referenceText;
        if (!refFormatted.matches(".*<\\|speaker:\\d+\\|>.*")) {
            refFormatted = "<|speaker:0|>" + refFormatted;
        }

        int[] prefix = concatArrays(
                tokenizer.encode("<|im_start|>system\n"),
                tokenizer.encode("convert the provided text to speech reference to the following:\n\nText:\n"),
                tokenizer.encode(refFormatted),
                tokenizer.encode("\n\nSpeech:\n")
        );
        int[] suffix = concatArrays(
                tokenizer.encode("<|im_end|>\n"),
                tokenizer.encode("<|im_start|>user\n"),
                tokenizer.encode(targetText),
                tokenizer.encode("<|im_end|>\n"),
                tokenizer.encode("<|im_start|>assistant\n<|voice|>")
        );

        int refFrames = referenceCodes != null ? referenceCodes.length / numCodebooks : 0;
        int[] semanticIds = new int[refFrames];
        if (referenceCodes != null) {
            for (int t = 0; t < refFrames; t++) {
                semanticIds[t] = referenceCodes[t] + semanticBeginId;
            }
        }

        int totalLen = prefix.length + semanticIds.length + suffix.length;
        long[][][] result = new long[1][numCodebooks + 1][totalLen];

        // Row 0: prefix + semantic IDs + suffix
        long[] row0 = result[0][0];
        int pos = 0;
        for (int v : prefix) row0[pos++] = v;
        for (int v : semanticIds) row0[pos++] = v;
        for (int v : suffix) row0[pos++] = v;

        // Rows 1-N: zero everywhere except reference region
        if (referenceCodes != null) {
            int refStart = prefix.length;
            for (int cb = 0; cb < numCodebooks; cb++) {
                for (int t = 0; t < refFrames; t++) {
                    result[0][cb + 1][refStart + t] = referenceCodes[cb * refFrames + t];
                }
            }
        }
        return result;
    }

    // --- ORT session interaction (integration-tested only) ---

    private SlowStepResult slowStep(long[] column, int position, SlowState state, Arena arena) {
        long stride = ValueLayout.JAVA_FLOAT.byteSize();
        String[] inputNames = {"codes", "position", "cache_keys", "cache_values",
                               "conv_states", "ssm_states"};
        MemorySegment[] inputValues = new MemorySegment[6];

        MemorySegment codesData = arena.allocateFrom(ValueLayout.JAVA_LONG, column);
        inputValues[0] = slowAr.createTensor(codesData, (long) column.length * 8,
                                             new long[]{1, manifest.numCodebooks() + 1, 1}, OnnxRuntimeLibrary.INT64, arena);

        MemorySegment posData = arena.allocateFrom(ValueLayout.JAVA_LONG, new long[]{position});
        inputValues[1] = slowAr.createTensor(posData, 8,
                                             new long[]{1}, OnnxRuntimeLibrary.INT64, arena);

        long[] kvShape = {manifest.numLayers(), 1, manifest.nLocalHeads(),
                          manifest.maxSeqLen(), manifest.headDim()};
        long kvBytes = (long) manifest.numLayers() * manifest.nLocalHeads()
                       * manifest.maxSeqLen() * manifest.headDim() * stride;
        inputValues[2] = slowAr.createTensor(state.cacheKeys, kvBytes, kvShape,
                                             OnnxRuntimeLibrary.FLOAT, arena);
        inputValues[3] = slowAr.createTensor(state.cacheValues, kvBytes, kvShape,
                                             OnnxRuntimeLibrary.FLOAT, arena);

        long[] convShape = {manifest.numLayers(), 1, manifest.convStateDim(), manifest.mambaDConv()};
        long convBytes = (long) manifest.numLayers() * manifest.convStateDim()
                         * manifest.mambaDConv() * stride;
        inputValues[4] = slowAr.createTensor(state.convStates, convBytes, convShape,
                                             OnnxRuntimeLibrary.FLOAT, arena);

        long[] ssmShape = {manifest.numLayers(), 1, manifest.mambaNHeads(),
                           manifest.mambaDHead(), manifest.mambaDState()};
        long ssmBytes = (long) manifest.numLayers() * manifest.mambaNHeads()
                        * manifest.mambaDHead() * manifest.mambaDState() * stride;
        inputValues[5] = slowAr.createTensor(state.ssmStates, ssmBytes, ssmShape,
                                             OnnxRuntimeLibrary.FLOAT, arena);

        MemorySegment[] outputs = slowAr.runRaw(inputNames, inputValues, slowOutputNames, arena);
        try {
            MemorySegment keyDelta = slowAr.getTensorData(outputs[2], arena)
                                           .reinterpret(slowAr.tensorElementCount(outputs[2], arena) * stride);
            scatterSlowCache(state.cacheKeys, keyDelta, position);
            MemorySegment valDelta = slowAr.getTensorData(outputs[3], arena)
                                           .reinterpret(slowAr.tensorElementCount(outputs[3], arena) * stride);
            scatterSlowCache(state.cacheValues, valDelta, position);

            MemorySegment nextConv = slowAr.getTensorData(outputs[4], arena)
                                           .reinterpret(convBytes);
            MemorySegment.copy(nextConv, 0, state.convStates, 0, convBytes);
            MemorySegment nextSsm = slowAr.getTensorData(outputs[5], arena)
                                          .reinterpret(ssmBytes);
            MemorySegment.copy(nextSsm, 0, state.ssmStates, 0, ssmBytes);

            long logitsCount = slowAr.tensorElementCount(outputs[0], arena);
            float[] logits = slowAr.getTensorData(outputs[0], arena)
                                   .reinterpret(logitsCount * stride).toArray(ValueLayout.JAVA_FLOAT);
            long hiddenCount = slowAr.tensorElementCount(outputs[1], arena);
            float[] hidden = slowAr.getTensorData(outputs[1], arena)
                                   .reinterpret(hiddenCount * stride).toArray(ValueLayout.JAVA_FLOAT);

            return new SlowStepResult(logits, hidden);
        } finally {
            for (MemorySegment out : outputs) {slowAr.releaseValue(out);}
            for (MemorySegment in : inputValues) {slowAr.releaseValue(in);}
        }
    }

    private void scatterSlowCache(MemorySegment cache, MemorySegment delta, int position) {
        int  numLayers = manifest.numLayers();
        int  nHeads    = manifest.nLocalHeads();
        int  maxSeqLen = manifest.maxSeqLen();
        int  headDim   = manifest.headDim();
        long stride    = ValueLayout.JAVA_FLOAT.byteSize();
        long copyLen   = (long) headDim * stride;
        for (int l = 0; l < numLayers; l++) {
            for (int h = 0; h < nHeads; h++) {
                long srcOff = ((long) l * nHeads + h) * headDim * stride;
                long dstOff = (((long) l * nHeads + h) * maxSeqLen + position) * headDim * stride;
                MemorySegment.copy(delta, srcOff, cache, dstOff, copyLen);
            }
        }
    }

    private float[] fastStep(float[] hidden, int tokenId, boolean useHidden,
                             int position, MemorySegment[] fastCaches, Arena arena) {
        int numFastLayers = manifest.numFastLayers();
        int numInputs     = 4 + 2 * numFastLayers;

        String[]        inputNames  = new String[numInputs];
        MemorySegment[] inputValues = new MemorySegment[numInputs];

        MemorySegment hiddenData = arena.allocateFrom(ValueLayout.JAVA_FLOAT, hidden);
        inputNames[0]  = "slow_hidden";
        inputValues[0] = fastAr.createTensor(hiddenData,
                                             (long) hidden.length * ValueLayout.JAVA_FLOAT.byteSize(),
                                             new long[]{1, 1, hidden.length}, OnnxRuntimeLibrary.FLOAT, arena);

        MemorySegment tokenData = arena.allocateFrom(ValueLayout.JAVA_LONG, new long[]{tokenId});
        inputNames[1]  = "token_id";
        inputValues[1] = fastAr.createTensor(tokenData, 8,
                                             new long[]{1, 1}, OnnxRuntimeLibrary.INT64, arena);

        MemorySegment boolData = arena.allocate(ValueLayout.JAVA_BYTE, 1);
        boolData.set(ValueLayout.JAVA_BYTE, 0, (byte) (useHidden ? 1 : 0));
        inputNames[2]  = "use_slow_hidden";
        inputValues[2] = fastAr.createTensor(boolData, 1,
                                             new long[]{1}, OnnxRuntimeLibrary.BOOL, arena);

        MemorySegment posData = arena.allocateFrom(ValueLayout.JAVA_LONG, new long[]{position});
        inputNames[3]  = "input_pos";
        inputValues[3] = fastAr.createTensor(posData, 8,
                                             new long[]{1}, OnnxRuntimeLibrary.INT64, arena);

        long[] cacheShape = {1, manifest.fastNLocalHeads(), manifest.numCodebooks(),
                             manifest.fastHeadDim()};
        long cacheBytes = 1L * manifest.fastNLocalHeads() * manifest.numCodebooks()
                          * manifest.fastHeadDim() * ValueLayout.JAVA_FLOAT.byteSize();
        for (int i = 0; i < numFastLayers; i++) {
            inputNames[4 + 2 * i]  = "cache_key_" + i;
            inputValues[4 + 2 * i] = fastAr.createTensor(fastCaches[2 * i], cacheBytes,
                                                         cacheShape, OnnxRuntimeLibrary.FLOAT, arena);
            inputNames[5 + 2 * i]  = "cache_value_" + i;
            inputValues[5 + 2 * i] = fastAr.createTensor(fastCaches[2 * i + 1], cacheBytes,
                                                         cacheShape, OnnxRuntimeLibrary.FLOAT, arena);
        }

        MemorySegment[] outputs = fastAr.runRaw(inputNames, inputValues, fastOutputNames, arena);
        try {
            for (int d = 0; d < 2 * numFastLayers; d++) {
                MemorySegment deltaPtr   = fastAr.getTensorData(outputs[1 + d], arena);
                long          deltaCount = fastAr.tensorElementCount(outputs[1 + d], arena);
                MemorySegment delta = deltaPtr.reinterpret(
                        deltaCount * ValueLayout.JAVA_FLOAT.byteSize());
                scatterFastCache(fastCaches[d], delta, position,
                        manifest.fastNLocalHeads(), manifest.numCodebooks(),
                        manifest.fastHeadDim());
            }

            long logitsCount = fastAr.tensorElementCount(outputs[0], arena);
            MemorySegment logitsPtr = fastAr.getTensorData(outputs[0], arena)
                                            .reinterpret(logitsCount * ValueLayout.JAVA_FLOAT.byteSize());
            return logitsPtr.toArray(ValueLayout.JAVA_FLOAT);
        } finally {
            for (MemorySegment out : outputs) {fastAr.releaseValue(out);}
            for (MemorySegment in : inputValues) {fastAr.releaseValue(in);}
        }
    }

    private static void scatterFastCache(MemorySegment cache, MemorySegment delta,
                                         int position, int nHeads, int seqLenDim, int headDim) {
        long stride  = ValueLayout.JAVA_FLOAT.byteSize();
        long copyLen = (long) headDim * stride;
        for (int h = 0; h < nHeads; h++) {
            long srcOff = (long) h * headDim * stride;
            long dstOff = ((long) h * seqLenDim + position) * headDim * stride;
            MemorySegment.copy(delta, srcOff, cache, dstOff, copyLen);
        }
    }

    private SlowState createEmptySlowState(Arena arena) {
        long kvSize = (long) manifest.numLayers() * manifest.nLocalHeads()
                      * manifest.maxSeqLen() * manifest.headDim();
        MemorySegment keys = arena.allocate(ValueLayout.JAVA_FLOAT, kvSize);
        keys.fill((byte) 0);
        MemorySegment values = arena.allocate(ValueLayout.JAVA_FLOAT, kvSize);
        values.fill((byte) 0);

        long          convSize = (long) manifest.numLayers() * manifest.convStateDim() * manifest.mambaDConv();
        MemorySegment conv     = arena.allocate(ValueLayout.JAVA_FLOAT, convSize);
        conv.fill((byte) 0);

        long ssmSize = (long) manifest.numLayers() * manifest.mambaNHeads()
                       * manifest.mambaDHead() * manifest.mambaDState();
        MemorySegment ssm = arena.allocate(ValueLayout.JAVA_FLOAT, ssmSize);
        ssm.fill((byte) 0);

        return new SlowState(keys, values, conv, ssm);
    }

    private MemorySegment[] createEmptyFastCaches(Arena arena) {
        int             count  = 2 * manifest.numFastLayers();
        MemorySegment[] caches = new MemorySegment[count];
        long            size   = 1L * manifest.fastNLocalHeads() * manifest.numCodebooks() * manifest.fastHeadDim();
        for (int i = 0; i < count; i++) {
            caches[i] = arena.allocate(ValueLayout.JAVA_FLOAT, size);
            caches[i].fill((byte) 0);
        }
        return caches;
    }

    private long[] buildColumn(int semantic, int[] codebooks, int numCb) {
        long[] col = new long[numCb + 1];
        col[0] = semantic;
        for (int i = 0; i < numCb; i++) col[i + 1] = codebooks[i];
        return col;
    }

    private static int[] concatArrays(int[]... arrays) {
        int total = 0;
        for (int[] a : arrays) total += a.length;
        int[] result = new int[total];
        int pos = 0;
        for (int[] a : arrays) {
            System.arraycopy(a, 0, result, pos, a.length);
            pos += a.length;
        }
        return result;
    }

    private static String[] queryOutputNames(OnnxRuntimeLibrary.Session session) {
        int      count = session.outputCount();
        String[] names = new String[count];
        for (int i = 0; i < count; i++) {names[i] = session.outputName(i);}
        return names;
    }

}
