package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DualARLoopTest {

    private static final Path REAL_MANIFEST = resolveRealManifest();

    // --- RuntimeManifest parsing ---

    @Test
    void parsesRuntimeManifest() throws Exception {
        assumeTrue(Files.exists(REAL_MANIFEST), "Real runtime_manifest.json not available");
        var manifest = RuntimeManifest.load(REAL_MANIFEST);

        assertThat(manifest.sampleRate()).isEqualTo(44100);
        assertThat(manifest.numCodebooks()).isEqualTo(10);
        assertThat(manifest.codebookSize()).isEqualTo(4096);
        assertThat(manifest.semanticBeginId()).isEqualTo(65537);
        assertThat(manifest.semanticEndId()).isEqualTo(69632);
        assertThat(manifest.imEndId()).isEqualTo(4096);
        assertThat(manifest.maxSeqLen()).isEqualTo(2048);
        assertThat(manifest.numLayers()).isEqualTo(24);
        assertThat(manifest.numFastLayers()).isEqualTo(4);
    }

    // --- Sampling ---

    @Test
    void sampleSelectsHighestLogitWithLowTemperature() {
        float[] logits = {1.0f, 5.0f, 2.0f, 3.0f};
        int result = DualARLoop.sample(logits, 0.01f, 0.9f, 50, new Random(42));
        assertThat(result).isEqualTo(1); // index of 5.0
    }

    @Test
    void sampleRespectsTopK() {
        float[] logits = new float[100];
        logits[50] = 10.0f; // one dominant logit
        for (int i = 0; i < 100; i++) {
            if (i != 50) logits[i] = -10.0f;
        }
        int result = DualARLoop.sample(logits, 1.0f, 1.0f, 1, new Random(42));
        assertThat(result).isEqualTo(50);
    }

    @Test
    void sampleWithUniformLogitsProducesVariedResults() {
        float[] logits = new float[10];
        for (int i = 0; i < 10; i++) logits[i] = 0.0f;

        var results = new java.util.HashSet<Integer>();
        for (int seed = 0; seed < 100; seed++) {
            results.add(DualARLoop.sample(logits, 1.0f, 1.0f, 10, new Random(seed)));
        }
        assertThat(results.size()).isGreaterThan(1);
    }

    // --- Semantic sampling ---

    @Test
    void sampleSemanticReturnsSemanticTokenOrEos() {
        int begin = 100;
        int end   = 200;
        int eos   = 50;

// Logits in semantic_then_eos layout: 101 semantic + 1 eos = 102 elements
        float[] logits = new float[102];
        logits[10] = 5.0f; // semantic token at index 10 → token ID begin + 10 = 110

        int result = DualARLoop.sampleSemantic(logits, new ArrayList<>(),
                                               0.01f, 0.9f, 50, new Random(42), begin, end, eos, true);
        assertThat(result).isEqualTo(110);
    }

    @Test
    void sampleSemanticReturnsEosWhenEosLogitDominates() {
        int begin = 100;
        int end = 200;
        int eos = 50;

        float[] logits = new float[102]; // 101 semantic + 1 eos
        logits[101] = 10.0f; // eos is the last element

        int result = DualARLoop.sampleSemantic(logits, new ArrayList<>(),
                0.01f, 0.9f, 50, new Random(42), begin, end, eos, true);
        assertThat(result).isEqualTo(eos);
    }

    @Test
    void sampleSemanticAvoidsRecentRepetition() {
        int begin = 100;
        int end = 200;
        int eos = 50;

        float[] logits = new float[102];
        logits[10] = 5.0f; // preferred
        logits[20] = 4.9f; // close second
        List<Integer> previous = new ArrayList<>(List.of(110)); // 110 = begin + 10

        int result = DualARLoop.sampleSemantic(logits, previous,
                0.01f, 0.9f, 50, new Random(42), begin, end, eos, true);
        // Should pick high-temp alternative since 110 is in previous
        assertThat(result).isNotEqualTo(eos);
    }

    // --- Prompt building ---

    @Test
    void buildPromptHasCorrectShape() throws Exception {
        assumeTrue(Files.exists(REAL_MANIFEST), "Real runtime_manifest.json not available");
        var manifest  = RuntimeManifest.load(REAL_MANIFEST);
        var tokenizer = loadRealTokenizer();
        assumeTrue(tokenizer != null, "Real tokenizer not available");

        int[] refCodes = new int[manifest.numCodebooks() * 10]; // 10 frames
        long[][][] prompt = DualARLoop.buildPrompt(
                "Hello world", "reference text", refCodes, manifest.numCodebooks(),
                manifest.semanticBeginId(), tokenizer);

// Shape: [1, numCodebooks+1, totalLen]
        assertThat(prompt.length).isEqualTo(1);
        assertThat(prompt[0].length).isEqualTo(manifest.numCodebooks() + 1);
        assertThat(prompt[0][0].length).isGreaterThan(10);}

    @Test
    void buildPromptContainsSemanticIds() throws Exception {
        assumeTrue(Files.exists(REAL_MANIFEST), "Real runtime_manifest.json not available");
        var manifest = RuntimeManifest.load(REAL_MANIFEST);
        var tokenizer = loadRealTokenizer();
        assumeTrue(tokenizer != null, "Real tokenizer not available");

        int numFrames = 5;
        int[] refCodes = new int[manifest.numCodebooks() * numFrames];
        for (int cb = 0; cb < manifest.numCodebooks(); cb++) {
            for (int t = 0; t < numFrames; t++) {
                refCodes[cb * numFrames + t] = t + 1; // codec values 1-5
            }
        }

        long[][][] prompt = DualARLoop.buildPrompt(
                "Hi", "ref text", refCodes, manifest.numCodebooks(),
                manifest.semanticBeginId(), tokenizer);

        // Row 0 should contain semantic IDs (refCodes[0] + semanticBeginId)
        long[] row0 = prompt[0][0];
        boolean foundSemantic = false;
        for (long val : row0) {
            if (val >= manifest.semanticBeginId() && val <= manifest.semanticEndId()) {
                foundSemantic = true;
                break;
            }
        }
        assertThat(foundSemantic).isTrue();
    }

    // --- Helpers ---

    private static Audio8Tokenizer loadRealTokenizer() {
        Path tokenizerPath = resolveRealTokenizer();
        if (!Files.exists(tokenizerPath)) return null;
        try {
            return Audio8Tokenizer.load(tokenizerPath);
        } catch (Exception e) {
            return null;
        }
    }

    private static Path resolveRealTokenizer() {
        Path base = Path.of(System.getProperty("user.home"),
                ".cache", "huggingface", "hub",
                "models--Audio8--audio8-TTS-0.1B-ONNX-INT8");
        if (!Files.isDirectory(base)) return base.resolve("tokenizer.json");
        try (var stream = Files.walk(base)) {
            return stream.filter(p -> p.endsWith("tokenizer/tokenizer.json"))
                    .findFirst().orElse(base.resolve("tokenizer.json"));
        } catch (Exception e) {
            return base.resolve("tokenizer.json");
        }
    }

    private static Path resolveRealManifest() {
        Path base = Path.of(System.getProperty("user.home"),
                ".cache", "huggingface", "hub",
                "models--Audio8--audio8-TTS-0.1B-ONNX-INT8");
        if (!Files.isDirectory(base)) return base.resolve("runtime_manifest.json");
        try (var stream = Files.walk(base)) {
            return stream.filter(p -> p.getFileName().toString().equals("runtime_manifest.json"))
                    .findFirst().orElse(base.resolve("runtime_manifest.json"));
        } catch (Exception e) {
            return base.resolve("runtime_manifest.json");
        }
    }
}
