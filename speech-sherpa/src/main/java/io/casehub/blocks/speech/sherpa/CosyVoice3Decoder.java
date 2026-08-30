package io.casehub.blocks.speech.sherpa;

import org.jspecify.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

final class CosyVoice3Decoder implements TtsDecoder {

    @FunctionalInterface
    interface FlowTokenProcessor {
        float[] process(int[] tokens);
    }

    @FunctionalInterface
    interface SpeakerProjector {
        float[] project(float[] normalizedSpeakerEmbedding);
    }

    @FunctionalInterface
    interface FlowEstimator {
        float[] estimate(float[] x, float[] mask, float[] mu, float[] t,
                         float[] spks, float[] cond, int melBins, int melLen);
    }

    @FunctionalInterface
    interface Vocoder {
        float[] synthesize(float[] mel, int melBins, int melLen);
    }

    private final FlowTokenProcessor flowTokenProcessor;
    private final SpeakerProjector speakerProjector;
    private final FlowEstimator flowEstimator;
    private final Vocoder vocoder;
    private final CosyVoice3Manifest manifest;
    private final @Nullable Random rng;

    CosyVoice3Decoder(FlowTokenProcessor flowTokenProcessor,
                       SpeakerProjector speakerProjector,
                       FlowEstimator flowEstimator,
                       Vocoder vocoder,
                       CosyVoice3Manifest manifest,
                       @Nullable Random rng) {
        this.flowTokenProcessor = flowTokenProcessor;
        this.speakerProjector = speakerProjector;
        this.flowEstimator = flowEstimator;
        this.vocoder = vocoder;
        this.manifest = manifest;
        this.rng = rng;
    }

    static CosyVoice3Decoder fromSessions(OnnxRuntimeLibrary.Session flowTokenEmbedding,
                                           OnnxRuntimeLibrary.Session flowPreLookahead,
                                           OnnxRuntimeLibrary.Session flowSpeakerProjection,
                                           OnnxRuntimeLibrary.Session flowEstimatorSession,
                                           OnnxRuntimeLibrary.Session hiftF0,
                                           OnnxRuntimeLibrary.Session hiftSource,
                                           OnnxRuntimeLibrary.Session hiftDecoder,
                                           CosyVoice3Manifest manifest) {
        int hiddenDim = manifest.hiddenDim();
        int melBins = manifest.melBins();
        int tokenMelRatio = manifest.tokenMelRatio();
        int nFft = manifest.hiftNFft();
        int hopLength = manifest.hiftHopLength();
        int nBins = nFft / 2 + 1;

        FlowTokenProcessor tokenProcessor = tokens -> {
            try (var arena = Arena.ofConfined()) {
                long[] longIds = new long[tokens.length];
                for (int i = 0; i < tokens.length; i++) longIds[i] = tokens[i];
                var data = arena.allocateFrom(ValueLayout.JAVA_LONG, longIds);
                var tensor = flowTokenEmbedding.createTensor(data,
                        (long) tokens.length * ValueLayout.JAVA_LONG.byteSize(),
                        new long[]{1, tokens.length}, OnnxRuntimeLibrary.INT64, arena);
                var embOutputs = flowTokenEmbedding.runRaw(
                        new String[]{flowTokenEmbedding.inputName(0)},
                        new MemorySegment[]{tensor},
                        new String[]{flowTokenEmbedding.outputName(0)},
                        arena);
                try {
                    long embCount = flowTokenEmbedding.tensorElementCount(embOutputs[0], arena);
                    float[] embeddings = flowTokenEmbedding.getTensorData(embOutputs[0], arena)
                            .reinterpret(embCount * ValueLayout.JAVA_FLOAT.byteSize())
                            .toArray(ValueLayout.JAVA_FLOAT);

                    var embSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, embeddings);
                    var embTensor2 = flowPreLookahead.createTensor(embSeg,
                            (long) embeddings.length * ValueLayout.JAVA_FLOAT.byteSize(),
                            new long[]{1, tokens.length, melBins}, OnnxRuntimeLibrary.FLOAT, arena);
                    var laOutputs = flowPreLookahead.runRaw(
                            new String[]{flowPreLookahead.inputName(0)},
                            new MemorySegment[]{embTensor2},
                            new String[]{flowPreLookahead.outputName(0)},
                            arena);
                    try {
                        long hCount = flowPreLookahead.tensorElementCount(laOutputs[0], arena);
                        float[] h = flowPreLookahead.getTensorData(laOutputs[0], arena)
                                .reinterpret(hCount * ValueLayout.JAVA_FLOAT.byteSize())
                                .toArray(ValueLayout.JAVA_FLOAT);

                        int melLen = tokens.length * tokenMelRatio;
                        float[] mu = new float[melBins * melLen];
                        for (int d = 0; d < melBins; d++) {
                            for (int f = 0; f < melLen; f++) {
                                mu[d * melLen + f] = h[f * melBins + d];
                            }
                        }
                        return mu;
                    } finally {
                        for (var out : laOutputs) flowPreLookahead.releaseValue(out);
                        flowPreLookahead.releaseValue(embTensor2);
                    }
                } finally {
                    for (var out : embOutputs) flowTokenEmbedding.releaseValue(out);
                    flowTokenEmbedding.releaseValue(tensor);
                }
            }
        };

        SpeakerProjector projector = normalizedEmb -> {
            try (var arena = Arena.ofConfined()) {
                var data = arena.allocateFrom(ValueLayout.JAVA_FLOAT, normalizedEmb);
                return flowSpeakerProjection.runFloat(
                        new String[]{flowSpeakerProjection.inputName(0)},
                        new MemorySegment[]{data},
                        new long[][]{{1, normalizedEmb.length}},
                        new String[]{flowSpeakerProjection.outputName(0)},
                        arena);
            }
        };

        FlowEstimator estimator = (x, mask, mu, t, spks, cond, mb, ml) -> {
            try (var arena = Arena.ofConfined()) {
                float[] x2 = concatFloat(x, x);
                float[] mask2 = concatFloat(mask, mask);
                float[] mu2 = concatFloat(mu, mu);
                float[] spks2 = concatFloat(spks, spks);
                float[] cond2 = concatFloat(cond, cond);

                var xData = arena.allocateFrom(ValueLayout.JAVA_FLOAT, x2);
                var xTensor = flowEstimatorSession.createTensor(xData,
                        (long) x2.length * ValueLayout.JAVA_FLOAT.byteSize(),
                        new long[]{2, mb, ml}, OnnxRuntimeLibrary.FLOAT, arena);

                var maskData = arena.allocateFrom(ValueLayout.JAVA_FLOAT, mask2);
                var maskTensor = flowEstimatorSession.createTensor(maskData,
                        (long) mask2.length * ValueLayout.JAVA_FLOAT.byteSize(),
                        new long[]{2, 1, ml}, OnnxRuntimeLibrary.FLOAT, arena);

                var muData = arena.allocateFrom(ValueLayout.JAVA_FLOAT, mu2);
                var muTensor = flowEstimatorSession.createTensor(muData,
                        (long) mu2.length * ValueLayout.JAVA_FLOAT.byteSize(),
                        new long[]{2, mb, ml}, OnnxRuntimeLibrary.FLOAT, arena);

                var tData = arena.allocateFrom(ValueLayout.JAVA_FLOAT, t);
                var tTensor = flowEstimatorSession.createTensor(tData,
                        (long) t.length * ValueLayout.JAVA_FLOAT.byteSize(),
                        new long[]{2}, OnnxRuntimeLibrary.FLOAT, arena);

                var spksData = arena.allocateFrom(ValueLayout.JAVA_FLOAT, spks2);
                var spksTensor = flowEstimatorSession.createTensor(spksData,
                        (long) spks2.length * ValueLayout.JAVA_FLOAT.byteSize(),
                        new long[]{2, spks.length}, OnnxRuntimeLibrary.FLOAT, arena);

                var condData = arena.allocateFrom(ValueLayout.JAVA_FLOAT, cond2);
                var condTensor = flowEstimatorSession.createTensor(condData,
                        (long) cond2.length * ValueLayout.JAVA_FLOAT.byteSize(),
                        new long[]{2, mb, ml}, OnnxRuntimeLibrary.FLOAT, arena);

                int inputCount = flowEstimatorSession.inputCount();
                String[] inputNames = new String[inputCount];
                MemorySegment[] inputValues = {xTensor, maskTensor, muTensor, tTensor, spksTensor, condTensor};
                for (int i = 0; i < inputCount; i++) inputNames[i] = flowEstimatorSession.inputName(i);

                int outputCount = flowEstimatorSession.outputCount();
                String[] outputNames = new String[outputCount];
                for (int i = 0; i < outputCount; i++) outputNames[i] = flowEstimatorSession.outputName(i);

                var outputs = flowEstimatorSession.runRaw(inputNames, inputValues, outputNames, arena);
                try {
                    long count = flowEstimatorSession.tensorElementCount(outputs[0], arena);
                    float[] velocity2 = flowEstimatorSession.getTensorData(outputs[0], arena)
                            .reinterpret(count * ValueLayout.JAVA_FLOAT.byteSize())
                            .toArray(ValueLayout.JAVA_FLOAT);
                    int batchSize = mb * ml;
                    return Arrays.copyOf(velocity2, batchSize);
                } finally {
                    for (var out : outputs) flowEstimatorSession.releaseValue(out);
                    for (var in : inputValues) flowEstimatorSession.releaseValue(in);
                }
            }
        };

        Vocoder voc = (mel, mb, ml) -> {
            try (var arena = Arena.ofConfined()) {
                var melData = arena.allocateFrom(ValueLayout.JAVA_FLOAT, mel);

                float[] f0 = hiftF0.runFloat(
                        new String[]{hiftF0.inputName(0)},
                        new MemorySegment[]{melData},
                        new long[][]{{1, mb, ml}},
                        new String[]{hiftF0.outputName(0)},
                        arena);

                var f0Seg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, f0);
                float[] source = hiftSource.runFloat(
                        new String[]{hiftSource.inputName(0)},
                        new MemorySegment[]{f0Seg},
                        new long[][]{{1, 1, f0.length}},
                        new String[]{hiftSource.outputName(0)},
                        arena);

                int pad = nFft / 2;
                float[] centeredSource = new float[source.length + 2 * pad];
                System.arraycopy(source, 0, centeredSource, pad, source.length);
                for (int i = 0; i < pad; i++) {
                    centeredSource[pad - 1 - i] = source[Math.min(i + 1, source.length - 1)];
                    centeredSource[centeredSource.length - pad + i] = source[Math.max(source.length - 2 - i, 0)];
                }
                float[][][] stftResult = StftUtils.stft(centeredSource, nFft, hopLength);
                float[][] stftReal = stftResult[0];
                float[][] stftImag = stftResult[1];
                int stftFrames = stftReal[0].length;

                float[] sourceStft = new float[2 * nBins * stftFrames];
                for (int b = 0; b < nBins; b++) {
                    System.arraycopy(stftReal[b], 0, sourceStft, b * stftFrames, stftFrames);
                }
                for (int b = 0; b < nBins; b++) {
                    System.arraycopy(stftImag[b], 0, sourceStft, (nBins + b) * stftFrames, stftFrames);
                }

                var stftSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, sourceStft);
                var stftTensor = hiftDecoder.createTensor(stftSeg,
                        (long) sourceStft.length * ValueLayout.JAVA_FLOAT.byteSize(),
                        new long[]{1, 2 * nBins, stftFrames}, OnnxRuntimeLibrary.FLOAT, arena);
                var melTensor = hiftDecoder.createTensor(melData,
                        (long) mel.length * ValueLayout.JAVA_FLOAT.byteSize(),
                        new long[]{1, mb, ml}, OnnxRuntimeLibrary.FLOAT, arena);

                int hiftOutputCount = hiftDecoder.outputCount();
                String[] hiftOutputNames = new String[hiftOutputCount];
                for (int i = 0; i < hiftOutputCount; i++) hiftOutputNames[i] = hiftDecoder.outputName(i);

                var hiftOutputs = hiftDecoder.runRaw(
                        new String[]{hiftDecoder.inputName(0), hiftDecoder.inputName(1)},
                        new MemorySegment[]{melTensor, stftTensor},
                        hiftOutputNames,
                        arena);
                try {
                    long magCount = hiftDecoder.tensorElementCount(hiftOutputs[0], arena);
                    float[] magFlat = hiftDecoder.getTensorData(hiftOutputs[0], arena)
                            .reinterpret(magCount * ValueLayout.JAVA_FLOAT.byteSize())
                            .toArray(ValueLayout.JAVA_FLOAT);
                    long phaseCount = hiftDecoder.tensorElementCount(hiftOutputs[1], arena);
                    float[] phaseFlat = hiftDecoder.getTensorData(hiftOutputs[1], arena)
                            .reinterpret(phaseCount * ValueLayout.JAVA_FLOAT.byteSize())
                            .toArray(ValueLayout.JAVA_FLOAT);

                    int outFrames = magFlat.length / nBins;
                    float[][] magnitude = new float[nBins][outFrames];
                    float[][] phase = new float[nBins][outFrames];
                    for (int b = 0; b < nBins; b++) {
                        System.arraycopy(magFlat, b * outFrames, magnitude[b], 0, outFrames);
                        System.arraycopy(phaseFlat, b * outFrames, phase[b], 0, outFrames);
                    }
                    return StftUtils.istft(magnitude, phase, nFft, hopLength);
                } finally {
                    for (var out : hiftOutputs) hiftDecoder.releaseValue(out);
                    hiftDecoder.releaseValue(stftTensor);
                    hiftDecoder.releaseValue(melTensor);
                }
            }
        };

        return new CosyVoice3Decoder(tokenProcessor, projector, estimator, voc, manifest, null);
    }

    @Override
    public float[] decode(GeneratorOutput generatorOutput, @Nullable VoiceData voiceData) {
        if (voiceData == null) {
            throw new IllegalArgumentException("CosyVoice3 requires voice data for decoding");
        }
        if (!(voiceData instanceof VoiceData.EmbeddingVoiceData evd)) {
            throw new IllegalArgumentException("CosyVoice3 requires EmbeddingVoiceData, got "
                                               + voiceData.getClass().getSimpleName());
        }
        if (!(generatorOutput instanceof GeneratorOutput.SpeechTokenOutput sto)) {
            throw new IllegalArgumentException("CosyVoice3 requires SpeechTokenOutput, got "
                                               + generatorOutput.getClass().getSimpleName());
        }

        int[] promptTokens  = evd.speechTokens();
        int[] genTokens     = sto.speechTokens();
        int[] allTokens     = concatInt(promptTokens, genTokens);
        int   melBins       = manifest.melBins();
        int   tokenMelRatio = manifest.tokenMelRatio();

        float[] mu        = flowTokenProcessor.process(allTokens);
        int     rawMelLen = mu.length / melBins;
        int     melLen    = rawMelLen % 2 == 0 ? rawMelLen : rawMelLen + 1;
        if (melLen != rawMelLen) {
            float[] paddedMu = new float[melBins * melLen];
            System.arraycopy(mu, 0, paddedMu, 0, mu.length);
            mu = paddedMu;
        }

        int promptMelFrames = promptTokens.length * tokenMelRatio;
        if (promptMelFrames > melLen) {promptMelFrames = melLen;}
        int genMelFrames = melLen - promptMelFrames;

        float[] normalizedSpk = l2Normalize(evd.speakerEmbedding());
        float[] spks          = speakerProjector.project(normalizedSpk);

        float[] cond = buildConditioning(evd.promptMel(), melBins, melLen, promptMelFrames);

        float[] mask = new float[melLen];
        Arrays.fill(mask, 1.0f);

        Random  random = rng != null ? rng : ThreadLocalRandom.current();
        float[] x      = new float[melBins * melLen];
        for (int i = 0; i < x.length; i++) {
            x[i] = (float) random.nextGaussian();
        }

        int   flowSteps = manifest.flowSteps();
        float dt        = 1.0f / flowSteps;
        for (int step = 0; step < flowSteps; step++) {
            float[] t        = {(float) step / flowSteps, (float) step / flowSteps};
            float[] velocity = flowEstimator.estimate(x, mask, mu, t, spks, cond, melBins, melLen);
            for (int i = 0; i < x.length; i++) {
                x[i] += velocity[i] * dt;
            }
        }

        float[] genMel = new float[melBins * genMelFrames];
        for (int m = 0; m < melBins; m++) {
            System.arraycopy(x, m * melLen + promptMelFrames,
                             genMel, m * genMelFrames, genMelFrames);
        }

        float[] audio = vocoder.synthesize(genMel, melBins, genMelFrames);

        for (int i = 0; i < audio.length; i++) {
            audio[i] = Math.max(-0.99f, Math.min(0.99f, audio[i]));
        }

        return audio;
    }

    static float[] l2Normalize(float[] vec) {
        float sumSq = 0;
        for (float v : vec) sumSq += v * v;
        float norm = (float) Math.sqrt(sumSq);
        if (norm < 1e-12f) return vec.clone();
        float[] result = new float[vec.length];
        for (int i = 0; i < vec.length; i++) result[i] = vec[i] / norm;
        return result;
    }

    private float[] buildConditioning(float[] promptMel, int melBins, int melLen, int promptMelFrames) {
        float[] cond = new float[melBins * melLen];
        int storedFrames = promptMel.length / melBins;
        int framesToCopy = Math.min(storedFrames, promptMelFrames);
        for (int m = 0; m < melBins; m++) {
            System.arraycopy(promptMel, m * storedFrames,
                    cond, m * melLen, framesToCopy);
        }
        return cond;
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
