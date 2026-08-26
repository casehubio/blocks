package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GectorTagApplierTest {

    private static GectorConfig config;

    @BeforeAll
    static void setUp(@TempDir Path tempDir) throws Exception {
        try (var in = GectorTagApplierTest.class.getResourceAsStream("/test-gector-labels.txt")) {
            Files.copy(in, tempDir.resolve("labels.txt"));
        }
        Files.createFile(tempDir.resolve("model.onnx"));
        Files.createFile(tempDir.resolve("spiece.model"));
        Files.writeString(tempDir.resolve("verb-form-vocab.txt"),
                "go\tVB_VBZ\tgoes\ngo\tVBZ_VB\tgo\nbe\tVB_VBZ\tis\n");
        config = GectorConfig.fromModelDir(tempDir);
    }

    @Test
    void keepPreservesToken() {
        var result = apply(List.of("hello", "world"), new int[]{0, 0});
        assertThat(result.tokens()).containsExactly("hello", "world");
        assertThat(result.changed()).isFalse();
    }

    @Test
    void deleteRemovesToken() {
        var result = apply(List.of("the", "the", "cat"), new int[]{0, 1, 0});
        assertThat(result.tokens()).containsExactly("the", "cat");
        assertThat(result.changed()).isTrue();
    }

    @Test
    void appendInsertsWordAfter() {
        // $APPEND_the is tag index 2
        var result = apply(List.of("on", "mat"), new int[]{0, 2});
        assertThat(result.tokens()).containsExactly("on", "mat", "the");
        assertThat(result.changed()).isTrue();
    }

    @Test
    void appendA() {
        // $APPEND_a is tag index 3
        var result = apply(List.of("have", "cat"), new int[]{0, 3});
        assertThat(result.tokens()).containsExactly("have", "cat", "a");
        assertThat(result.changed()).isTrue();
    }

    @Test
    void replaceSubstitutesWord() {
        // $REPLACE_the is tag index 5, $REPLACE_a is tag index 6
        var result = apply(List.of("a", "cat"), new int[]{5, 0});
        assertThat(result.tokens()).containsExactly("the", "cat");
        assertThat(result.changed()).isTrue();
    }

    @Test
    void transformCaseCapital() {
        // $TRANSFORM_CASE_CAPITAL is tag index 9
        var result = apply(List.of("hello"), new int[]{9});
        assertThat(result.tokens()).containsExactly("Hello");
        assertThat(result.changed()).isTrue();
    }

    @Test
    void transformCaseLower() {
        // $TRANSFORM_CASE_LOWER is tag index 10
        var result = apply(List.of("Hello"), new int[]{10});
        assertThat(result.tokens()).containsExactly("hello");
        assertThat(result.changed()).isTrue();
    }

    @Test
    void transformVerbForm() {
        // $TRANSFORM_VERB_VB_VBZ is tag index 7
        var result = apply(List.of("go"), new int[]{7});
        assertThat(result.tokens()).containsExactly("goes");
        assertThat(result.changed()).isTrue();
    }

    @Test
    void transformVerbFormReverse() {
        // $TRANSFORM_VERB_VBZ_VB is tag index 8
        var result = apply(List.of("go"), new int[]{8});
        assertThat(result.tokens()).containsExactly("go");
    }

    @Test
    void transformVerbUnknownFallsBack() {
        // $TRANSFORM_VERB_VB_VBZ is tag index 7, "run" not in verb dict
        var result = apply(List.of("run"), new int[]{7});
        assertThat(result.tokens()).containsExactly("run");
    }

    @Test
    void transformAgreementSingular() {
        // $TRANSFORM_AGREEMENT_SINGULAR is tag index 11
        var result = apply(List.of("cats"), new int[]{11});
        assertThat(result.tokens()).containsExactly("cat");
        assertThat(result.changed()).isTrue();
    }

    @Test
    void transformAgreementPlural() {
        // $TRANSFORM_AGREEMENT_PLURAL is tag index 12
        var result = apply(List.of("cat"), new int[]{12});
        assertThat(result.tokens()).containsExactly("cats");
        assertThat(result.changed()).isTrue();
    }

    @Test
    void mergeHyphen() {
        // $MERGE_HYPHEN is tag index 13
        var result = apply(List.of("well", "known"), new int[]{13, 0});
        assertThat(result.tokens()).containsExactly("well-known");
        assertThat(result.changed()).isTrue();
    }

    @Test
    void multipleEdits() {
        // $DELETE=1, $APPEND_a=3, $TRANSFORM_CASE_CAPITAL=9
        var result = apply(List.of("the", "I", "have", "cat"), new int[]{1, 9, 0, 3});
        assertThat(result.tokens()).containsExactly("I", "have", "cat", "a");
        assertThat(result.changed()).isTrue();
    }

    private GectorTagApplier.Result apply(List<String> tokens, int[] tagIds) {
        return GectorTagApplier.apply(tokens, tagIds, config);
    }
}
