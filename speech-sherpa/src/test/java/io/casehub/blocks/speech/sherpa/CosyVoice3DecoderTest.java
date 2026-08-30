package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.*;

class CosyVoice3DecoderTest {

    private static final int MEL_BINS = 80;
    private static final int TOKEN_MEL_RATIO = 2;
    private static final int FLOW_STEPS = 10;
    private static final CosyVoice3Manifest MANIFEST = testManifest();

    @Test void implementsTtsDecoder() {
        var decoder = defaultDecoder();
        assertThat(decoder).isInstanceOf(TtsDecoder.class);
    }

    @Test void producesNonEmptyAudio() {
        var decoder = defaultDecoder();
        float[] audio = decoder.decode(testGeneratorOutput(), testVoiceData());
        assertThat(audio).isNotEmpty();
    }

    @Test void eulerOdeRunsConfiguredSteps() {
        List<float[]> capturedTimesteps = new ArrayList<>();
        CosyVoice3Decoder.FlowEstimator estimator = (x, mask, mu, t, spks, cond, melBins, melLen) -> {
            capturedTimesteps.add(t.clone());
            return new float[melBins * melLen];
        };
        var decoder = new CosyVoice3Decoder(
                mockFlowTokenProcessor(), mockSpeakerProjector(), estimator,
                mockVocoder(), MANIFEST, new Random(42));
        decoder.decode(testGeneratorOutput(), testVoiceData());
        assertThat(capturedTimesteps).hasSize(FLOW_STEPS);
    }

    @Test void eulerTimestepsAreCorrect() {
        List<float[]> capturedTimesteps = new ArrayList<>();
        CosyVoice3Decoder.FlowEstimator estimator = (x, mask, mu, t, spks, cond, melBins, melLen) -> {
            capturedTimesteps.add(t.clone());
            return new float[melBins * melLen];
        };
        var decoder = new CosyVoice3Decoder(
                mockFlowTokenProcessor(), mockSpeakerProjector(), estimator,
                mockVocoder(), MANIFEST, new Random(42));
        decoder.decode(testGeneratorOutput(), testVoiceData());

        for (int i = 0; i < FLOW_STEPS; i++) {
            float expected = (float) i / FLOW_STEPS;
            assertThat(capturedTimesteps.get(i)[0])
                    .as("timestep " + i).isCloseTo(expected, within(1e-6f));
            assertThat(capturedTimesteps.get(i)[1])
                    .as("timestep " + i + " batch").isCloseTo(expected, within(1e-6f));
        }
    }

    @Test void eulerOdeAccumulatesVelocity() {
        CosyVoice3Decoder.FlowEstimator estimator = (x, mask, mu, t, spks, cond, melBins, melLen) -> {
            float[] velocity = new float[melBins * melLen];
            java.util.Arrays.fill(velocity, 1.0f);
            return velocity;
        };
        float[] capturedMel = new float[1];
        CosyVoice3Decoder.Vocoder vocoder = (mel, melBins, melLen) -> {
            capturedMel[0] = mel[0];
            return new float[100];
        };
        var decoder = new CosyVoice3Decoder(
                mockFlowTokenProcessor(), mockSpeakerProjector(), estimator,
                vocoder, MANIFEST, new Random(0));
        decoder.decode(testGeneratorOutput(), testVoiceData());

        // After 10 steps with velocity=1.0 and dt=0.1, x shifts by +1.0
        // So mel = initial_noise + 1.0
        // The exact value depends on the random seed, but it should differ from 0
        assertThat(capturedMel[0]).isNotCloseTo(0f, within(0.01f));
    }

    @Test void speakerEmbeddingIsL2Normalized() {
        float[][] capturedEmb = new float[1][];
        CosyVoice3Decoder.SpeakerProjector projector = emb -> {
            capturedEmb[0] = emb.clone();
            return new float[10];
        };
        var decoder = new CosyVoice3Decoder(
                mockFlowTokenProcessor(), projector, mockFlowEstimator(),
                mockVocoder(), MANIFEST, new Random(42));

        float[] spk = {3.0f, 4.0f};
        var voiceData = new VoiceData.EmbeddingVoiceData(
                spk, new int[]{10, 20, 30}, new float[MEL_BINS * 10], "hello");
        decoder.decode(testGeneratorOutput(), voiceData);

        float normSq = 0;
        for (float v : capturedEmb[0]) normSq += v * v;
        assertThat(Math.sqrt(normSq)).isCloseTo(1.0, within(1e-5));
    }

    @Test void audioIsClampedToRange() {
        CosyVoice3Decoder.Vocoder vocoder = (mel, melBins, melLen) ->
                new float[]{-2.0f, 0.5f, 2.0f, -0.5f, 1.5f};
        var decoder = new CosyVoice3Decoder(
                mockFlowTokenProcessor(), mockSpeakerProjector(), mockFlowEstimator(),
                vocoder, MANIFEST, new Random(42));
        float[] audio = decoder.decode(testGeneratorOutput(), testVoiceData());

        for (float v : audio) {
            assertThat(v).isBetween(-0.99f, 0.99f);
        }
        assertThat(audio[0]).isEqualTo(-0.99f);
        assertThat(audio[2]).isEqualTo(0.99f);
    }

    @Test void vocoderReceivesOnlyGeneratedMel() {
        int[] capturedMelLen = new int[1];
        CosyVoice3Decoder.Vocoder vocoder = (mel, melBins, melLen) -> {
            capturedMelLen[0] = melLen;
            return new float[100];
        };
        var decoder = new CosyVoice3Decoder(
                mockFlowTokenProcessor(), mockSpeakerProjector(), mockFlowEstimator(),
                vocoder, MANIFEST, new Random(42));

        // promptTokens=3, genTokens=5, ratio=2
        // prompt mel frames = 3*2 = 6, gen mel frames = 5*2 = 10
        decoder.decode(
                new GeneratorOutput.SpeechTokenOutput(new int[]{1, 2, 3, 4, 5}),
                testVoiceData());
        assertThat(capturedMelLen[0]).isEqualTo(10);
    }

    @Test void conditioningContainsPromptMel() {
        float[][] capturedCond = new float[1][];
        CosyVoice3Decoder.FlowEstimator estimator = (x, mask, mu, t, spks, cond, melBins, melLen) -> {
            if (capturedCond[0] == null) capturedCond[0] = cond.clone();
            return new float[melBins * melLen];
        };
        float[] promptMel = new float[MEL_BINS * 6];
        java.util.Arrays.fill(promptMel, 0, MEL_BINS, 0.42f);
        var voiceData = new VoiceData.EmbeddingVoiceData(
                new float[192], new int[]{10, 20, 30}, promptMel, "hello");
        var decoder = new CosyVoice3Decoder(
                mockFlowTokenProcessor(), mockSpeakerProjector(), estimator,
                mockVocoder(), MANIFEST, new Random(42));
        decoder.decode(testGeneratorOutput(), voiceData);

        // First mel band's first frame should contain prompt mel data
        // prompt mel stored frames = 6, prompt mel frames = 3*2 = 6 → exact match
        // cond layout: [melBins * melLen], mel-band-major
        // total melLen = (3+5)*2 = 16
        assertThat(capturedCond[0][0]).isCloseTo(0.42f, within(1e-6f));
    }

    @Test void nullVoiceDataThrows() {
        var decoder = defaultDecoder();
        assertThatThrownBy(() -> decoder.decode(testGeneratorOutput(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void codecVoiceDataThrows() {
        var decoder = defaultDecoder();
        assertThatThrownBy(() -> decoder.decode(
                testGeneratorOutput(), new VoiceData.CodecVoiceData(new int[]{1})))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void wrongGeneratorOutputThrows() {
        var decoder = defaultDecoder();
        assertThatThrownBy(() -> decoder.decode(
                new GeneratorOutput.CodecFrameOutput(new int[][]{{1}}), testVoiceData()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void allTokensConcatenatedForFlowProcessing() {
        int[][] capturedTokens = new int[1][];
        CosyVoice3Decoder.FlowTokenProcessor processor = tokens -> {
            capturedTokens[0] = tokens.clone();
            int melLen = tokens.length * TOKEN_MEL_RATIO;
            return new float[MEL_BINS * melLen];
        };
        var decoder = new CosyVoice3Decoder(
                processor, mockSpeakerProjector(), mockFlowEstimator(),
                mockVocoder(), MANIFEST, new Random(42));

        // prompt speech tokens = [10, 20, 30], generated = [1, 2, 3, 4, 5]
        decoder.decode(
                new GeneratorOutput.SpeechTokenOutput(new int[]{1, 2, 3, 4, 5}),
                testVoiceData());

        assertThat(capturedTokens[0]).containsExactly(10, 20, 30, 1, 2, 3, 4, 5);
    }

    // --- Helpers ---

    private static CosyVoice3Decoder defaultDecoder() {
        return new CosyVoice3Decoder(
                mockFlowTokenProcessor(), mockSpeakerProjector(), mockFlowEstimator(),
                mockVocoder(), MANIFEST, new Random(42));
    }

    private static CosyVoice3Decoder.FlowTokenProcessor mockFlowTokenProcessor() {
        return tokens -> {
            int melLen = tokens.length * TOKEN_MEL_RATIO;
            return new float[MEL_BINS * melLen];
        };
    }

    private static CosyVoice3Decoder.SpeakerProjector mockSpeakerProjector() {
        return emb -> new float[10];
    }

    private static CosyVoice3Decoder.FlowEstimator mockFlowEstimator() {
        return (x, mask, mu, t, spks, cond, melBins, melLen) -> new float[melBins * melLen];
    }

    private static CosyVoice3Decoder.Vocoder mockVocoder() {
        return (mel, melBins, melLen) -> new float[100];
    }

    private static GeneratorOutput.SpeechTokenOutput testGeneratorOutput() {
        return new GeneratorOutput.SpeechTokenOutput(new int[]{1, 2, 3, 4, 5});
    }

    private static VoiceData.EmbeddingVoiceData testVoiceData() {
        return new VoiceData.EmbeddingVoiceData(
                new float[192], new int[]{10, 20, 30},
                new float[MEL_BINS * 6], "hello");
    }

    private static CosyVoice3Manifest testManifest() {
        var header = new PipelineHeader("cosyvoice3-test", 24000,
                Map.of(), null, Map.of());
        return new CosyVoice3Manifest(header, 896, 6561, 6561, 6562, 6563,
                24, 64, 2, 10, 80, 16, 4, 192, "tokenizer", Map.of());
    }
}
