package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class CosyVoice3GeneratorTest {

    private static final int HIDDEN_DIM = 896;
    private static final int SOS_ID = 6561;
    private static final int EOS_ID = 6562;
    private static final int TASK_ID = 6563;
    private static final int SPEECH_VOCAB_SIZE = 6561;
    private static final CosyVoice3Manifest MANIFEST = testManifest();

    @Test void implementsTtsGenerator() {
        var gen = defaultGenerator(eosAfterSteps(1));
        assertThat(gen).isInstanceOf(TtsGenerator.class);
    }

    @Test void returnsSpeechTokenOutput() {
        var gen = defaultGenerator(eosAfterSteps(3));
        var result = gen.generate(new int[]{100, 200}, testVoiceData(),
                new GeneratorConfig(1.0f, 25, 0.9f, 500, 1));
        assertThat(result).isInstanceOf(GeneratorOutput.SpeechTokenOutput.class);
    }

    @Test void eosTerminatesGeneration() {
        var gen = defaultGenerator(eosAfterSteps(5));
        var result = (GeneratorOutput.SpeechTokenOutput) gen.generate(
                new int[]{100, 200}, testVoiceData(),
                new GeneratorConfig(1.0f, 25, 0.9f, 500, 1));
        assertThat(result.speechTokens()).hasSize(5);
    }

    @Test void eosBeforeMinLengthIsIgnored() {
        CosyVoice3Generator.LogitDecoder decoder = hidden -> {
            float[] logits = new float[SPEECH_VOCAB_SIZE + 3];
            Arrays.fill(logits, -10f);
            logits[EOS_ID] = 10f;
            logits[42] = 5f;
            return logits;
        };
        var gen = defaultGenerator(decoder);
        // min = max(10, 1*2) = 10, max = min(15, 1*20) = 15
        var result = (GeneratorOutput.SpeechTokenOutput) gen.generate(
                new int[]{100}, testVoiceData(),
                new GeneratorConfig(1.0f, 25, 0.9f, 15, 10));
        assertThat(result.speechTokens()).hasSize(10);
    }

    @Test void maxTokenGuardStopsGeneration() {
        CosyVoice3Generator.LogitDecoder decoder = hidden -> {
            float[] logits = new float[SPEECH_VOCAB_SIZE + 3];
            Arrays.fill(logits, -10f);
            logits[42] = 10f;
            return logits;
        };
        var gen = defaultGenerator(decoder);
        // max = min(20, 1*20) = 20
        var result = (GeneratorOutput.SpeechTokenOutput) gen.generate(
                new int[]{100}, testVoiceData(),
                new GeneratorConfig(1.0f, 25, 0.9f, 20, 1));
        assertThat(result.speechTokens()).hasSize(20);
    }

    @Test void nanLogitsThrowsSherpaException() {
        CosyVoice3Generator.LogitDecoder decoder = hidden -> {
            float[] logits = new float[SPEECH_VOCAB_SIZE + 3];
            logits[0] = Float.NaN;
            return logits;
        };
        var gen = defaultGenerator(decoder);
        assertThatThrownBy(() -> gen.generate(
                new int[]{100}, testVoiceData(), GeneratorConfig.defaults()))
                .isInstanceOf(SherpaException.class)
                .hasMessageContaining("NaN");
    }

    @Test void infLogitsThrowsSherpaException() {
        CosyVoice3Generator.LogitDecoder decoder = hidden -> {
            float[] logits = new float[SPEECH_VOCAB_SIZE + 3];
            logits[0] = Float.POSITIVE_INFINITY;
            return logits;
        };
        var gen = defaultGenerator(decoder);
        assertThatThrownBy(() -> gen.generate(
                new int[]{100}, testVoiceData(), GeneratorConfig.defaults()))
                .isInstanceOf(SherpaException.class)
                .hasMessageContaining("Inf");
    }

    @Test void nullVoiceDataThrows() {
        var gen = defaultGenerator(eosAfterSteps(1));
        assertThatThrownBy(() -> gen.generate(
                new int[]{100}, null, GeneratorConfig.defaults()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void codecVoiceDataThrows() {
        var gen = defaultGenerator(eosAfterSteps(1));
        assertThatThrownBy(() -> gen.generate(
                new int[]{100}, new VoiceData.CodecVoiceData(new int[]{1}),
                GeneratorConfig.defaults()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void inputSequenceHasCorrectLength() {
        int[] capturedSeqLen = new int[1];
        CosyVoice3Generator.BackboneInitial initial = (embedding, seqLen, mask) -> {
            capturedSeqLen[0] = seqLen;
            return new CosyVoice3Generator.BackboneResult(
                    new float[HIDDEN_DIM], new float[10]);
        };
        var gen = new CosyVoice3Generator(
                mockTextEmbedder(), mockSpeechEmbedder(), initial,
                mockBackboneDecode(), eosAfterSteps(1),
                MANIFEST, mockTokenizer(), new Random(42));
        gen.generate(new int[]{100, 200}, testVoiceData(),
                new GeneratorConfig(1.0f, 25, 0.9f, 500, 1));

        // mockTokenizer: "hello" → 5 tokens. TTS tokens: [100, 200] (2).
        // Combined text tokens: 5 + 2 = 7.
        // seqLen = 1(SOS) + 7(text) + 1(TASK_ID) + 3(speech tokens) = 12
        assertThat(capturedSeqLen[0]).isEqualTo(12);
    }

    @Test void promptTextTokensPrependedToTtsTokens() {
        int[][] capturedTextTokens = new int[1][];
        CosyVoice3Generator.TextEmbedder textEmb = tokenIds -> {
            capturedTextTokens[0] = tokenIds.clone();
            return new float[tokenIds.length * HIDDEN_DIM];
        };
        var gen = new CosyVoice3Generator(
                textEmb, mockSpeechEmbedder(), mockBackboneInitial(),
                mockBackboneDecode(), eosAfterSteps(1),
                MANIFEST, mockTokenizer(), new Random(42));
        gen.generate(new int[]{100, 200}, testVoiceData(),
                new GeneratorConfig(1.0f, 25, 0.9f, 500, 1));

        // mockTokenizer: "hello" → [0, 1, 2, 3, 4]. TTS: [100, 200].
        // Combined: [0, 1, 2, 3, 4, 100, 200]
        assertThat(capturedTextTokens[0]).hasSize(7);
        assertThat(capturedTextTokens[0]).endsWith(100, 200);
    }

    @Test void speechEmbedderCalledForSosAndTaskId() {
        List<int[]> capturedCalls = new ArrayList<>();
        CosyVoice3Generator.SpeechEmbedder speechEmb = tokenIds -> {
            capturedCalls.add(tokenIds.clone());
            return new float[tokenIds.length * HIDDEN_DIM];
        };
        var gen = new CosyVoice3Generator(
                mockTextEmbedder(), speechEmb, mockBackboneInitial(),
                mockBackboneDecode(), eosAfterSteps(1),
                MANIFEST, mockTokenizer(), new Random(42));
        gen.generate(new int[]{100}, testVoiceData(),
                new GeneratorConfig(1.0f, 25, 0.9f, 500, 1));

        // Initial sequence calls: SOS, TASK_ID, prompt speech tokens
        assertThat(capturedCalls).hasSizeGreaterThanOrEqualTo(3);
        assertThat(capturedCalls.get(0)).containsExactly(SOS_ID);
        assertThat(capturedCalls.get(1)).containsExactly(TASK_ID);
        assertThat(capturedCalls.get(2)).containsExactly(10, 20, 30);
    }

    @Test void kvCachePassedBetweenDecodeSteps() {
        List<float[]> capturedKvCaches = new ArrayList<>();
        float[] initialKv = {1.0f, 2.0f, 3.0f};
        float[] step1Kv = {4.0f, 5.0f, 6.0f};

        CosyVoice3Generator.BackboneInitial initial = (emb, seqLen, mask) ->
                new CosyVoice3Generator.BackboneResult(new float[HIDDEN_DIM], initialKv);

        AtomicInteger decodeStep = new AtomicInteger();
        CosyVoice3Generator.BackboneDecode decode = (emb, mask, kvCache) -> {
            capturedKvCaches.add(kvCache);
            float[] nextKv = decodeStep.getAndIncrement() == 0 ? step1Kv : new float[]{7, 8, 9};
            return new CosyVoice3Generator.BackboneResult(new float[HIDDEN_DIM], nextKv);
        };

        var gen = new CosyVoice3Generator(
                mockTextEmbedder(), mockSpeechEmbedder(), initial, decode,
                eosAfterSteps(3), MANIFEST, mockTokenizer(), new Random(42));
        gen.generate(new int[]{100}, testVoiceData(),
                new GeneratorConfig(1.0f, 25, 0.9f, 500, 1));

        assertThat(capturedKvCaches.get(0)).isEqualTo(initialKv);
        assertThat(capturedKvCaches.get(1)).isEqualTo(step1Kv);
    }

    @Test void attentionMaskGrowsEachDecodeStep() {
        int[] capturedInitialMaskLen = new int[1];
        List<Integer> capturedDecodeMaskLens = new ArrayList<>();

        CosyVoice3Generator.BackboneInitial initial = (emb, seqLen, mask) -> {
            capturedInitialMaskLen[0] = mask.length;
            return new CosyVoice3Generator.BackboneResult(new float[HIDDEN_DIM], new float[10]);
        };
        CosyVoice3Generator.BackboneDecode decode = (emb, mask, kvCache) -> {
            capturedDecodeMaskLens.add(mask.length);
            return new CosyVoice3Generator.BackboneResult(new float[HIDDEN_DIM], kvCache);
        };

        var gen = new CosyVoice3Generator(
                mockTextEmbedder(), mockSpeechEmbedder(), initial, decode,
                eosAfterSteps(3), MANIFEST, mockTokenizer(), new Random(42));
        gen.generate(new int[]{100}, testVoiceData(),
                new GeneratorConfig(1.0f, 25, 0.9f, 500, 1));

        int baseLen = capturedInitialMaskLen[0];
        for (int i = 0; i < capturedDecodeMaskLens.size(); i++) {
            assertThat(capturedDecodeMaskLens.get(i))
                    .as("mask length at decode step " + i)
                    .isEqualTo(baseLen + i + 1);
        }
    }

    @Test void generatedTokensAreNonEos() {
        var gen = defaultGenerator(eosAfterSteps(5));
        var result = (GeneratorOutput.SpeechTokenOutput) gen.generate(
                new int[]{100, 200}, testVoiceData(),
                new GeneratorConfig(1.0f, 25, 0.9f, 500, 1));
        for (int token : result.speechTokens()) {
            assertThat(token).isNotEqualTo(EOS_ID);
        }
    }

    // --- Helpers ---

    private static CosyVoice3Generator defaultGenerator(CosyVoice3Generator.LogitDecoder logitDecoder) {
        return new CosyVoice3Generator(
                mockTextEmbedder(), mockSpeechEmbedder(), mockBackboneInitial(),
                mockBackboneDecode(), logitDecoder,
                MANIFEST, mockTokenizer(), new Random(42));
    }

    private static CosyVoice3Generator.LogitDecoder eosAfterSteps(int n) {
        AtomicInteger step = new AtomicInteger();
        return hidden -> {
            float[] logits = new float[SPEECH_VOCAB_SIZE + 3];
            Arrays.fill(logits, -10f);
            int current = step.getAndIncrement();
            if (current >= n) {
                logits[EOS_ID] = 10f;
            } else {
                logits[current % SPEECH_VOCAB_SIZE] = 10f;
            }
            return logits;
        };
    }

    private static CosyVoice3Generator.TextEmbedder mockTextEmbedder() {
        return tokenIds -> new float[tokenIds.length * HIDDEN_DIM];
    }

    private static CosyVoice3Generator.SpeechEmbedder mockSpeechEmbedder() {
        return tokenIds -> new float[tokenIds.length * HIDDEN_DIM];
    }

    private static CosyVoice3Generator.BackboneInitial mockBackboneInitial() {
        return (embedding, seqLen, mask) ->
                new CosyVoice3Generator.BackboneResult(new float[HIDDEN_DIM], new float[10]);
    }

    private static CosyVoice3Generator.BackboneDecode mockBackboneDecode() {
        return (embedding, mask, kvCache) ->
                new CosyVoice3Generator.BackboneResult(new float[HIDDEN_DIM], kvCache);
    }

    private static TtsTokenizer mockTokenizer() {
        return text -> {
            int[] ids = new int[text.length()];
            for (int i = 0; i < text.length(); i++) ids[i] = i;
            return ids;
        };
    }

    private static VoiceData.EmbeddingVoiceData testVoiceData() {
        return new VoiceData.EmbeddingVoiceData(
                new float[192], new int[]{10, 20, 30},
                new float[80 * 10], "hello");
    }

    private static CosyVoice3Manifest testManifest() {
        var header = new PipelineHeader("cosyvoice3-test", 24000,
                Map.of(), null, Map.of());
        return new CosyVoice3Manifest(header, 896, 6561, 6561, 6562, 6563,
                24, 64, 2, 10, 80, 16, 4, 192, "tokenizer", Map.of());
    }
}
