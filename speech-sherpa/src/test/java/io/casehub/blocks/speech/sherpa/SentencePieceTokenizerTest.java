package io.casehub.blocks.speech.sherpa;

import com.google.protobuf.CodedOutputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SentencePieceTokenizerTest {

    private static SentencePieceTokenizer tokenizer;

    @BeforeAll
    static void setUp(@TempDir Path tempDir) throws Exception {
        tokenizer = new SentencePieceTokenizer(writeTestModel(tempDir));
    }

    @Test
    void vocabSize() {
        assertThat(tokenizer.vocabSize()).isEqualTo(13);
    }

    @Test
    void idToPiece() {
        assertThat(tokenizer.idToPiece(0)).isEqualTo("<unk>");
        assertThat(tokenizer.idToPiece(1)).isEqualTo("<s>");
        assertThat(tokenizer.idToPiece(3)).isEqualTo("▁the");
    }

    @Test
    void pieceToId() {
        assertThat(tokenizer.pieceToId("▁the")).isEqualTo(3);
        assertThat(tokenizer.pieceToId("▁cat")).isEqualTo(4);
    }

    @Test
    void pieceToId_unknownReturnsZero() {
        assertThat(tokenizer.pieceToId("nonexistent")).isEqualTo(0);
    }

    @Test
    void encodeSingleWord() {
        assertThat(tokenizer.encode("the")).containsExactly(3);
    }

    @Test
    void encodeTwoWords() {
        assertThat(tokenizer.encode("the cat")).containsExactly(3, 4);
    }

    @Test
    void encodeSubwordSplit() {
        assertThat(tokenizer.encode("cats")).containsExactly(4, 9);
    }

    @Test
    void encodeSentence() {
        assertThat(tokenizer.encode("the cat sat on a"))
                .containsExactly(3, 4, 5, 6, 7);
    }

    @Test
    void encodeWordWithPrefix() {
        assertThat(tokenizer.encode("he sat")).containsExactly(12, 5);
    }

    @Test
    void decode() {
        assertThat(tokenizer.decode(new int[]{3, 4})).isEqualTo("the cat");
    }

    @Test
    void decodeSkipsControlTokens() {
        assertThat(tokenizer.decode(new int[]{1, 3, 4, 2})).isEqualTo("the cat");
    }

    @Test
    void encodeEmpty() {
        assertThat(tokenizer.encode("")).isEmpty();
    }

    @Test
    void normalize() {
        assertThat(tokenizer.normalize("hello world")).isEqualTo("▁hello▁world");
        assertThat(tokenizer.normalize("hello")).isEqualTo("▁hello");
    }

    @Test
    void normalizeStripsLeadingAndDuplicateSpaces() {
        assertThat(tokenizer.normalize("  hello  world  ")).isEqualTo("▁hello▁world");
    }

    @Test
    void denormalize() {
        assertThat(tokenizer.denormalize("▁hello▁world")).isEqualTo("hello world");
    }

    @Test
    void unknownCharacters() {
        int[] result = tokenizer.encode("z");
        assertThat(result).containsExactly(8, 0);
    }

    @Test
    void consecutiveUnknownsCollapsed() {
        int[] result = tokenizer.encode("xyz");
        assertThat(result).containsExactly(8, 0);
    }

    @Test
    void roundTrip() {
        String text = "the cat sat";
        int[] encoded = tokenizer.encode(text);
        String decoded = tokenizer.decode(encoded);
        assertThat(decoded).isEqualTo(text);
    }

    private static Path writeTestModel(Path dir) throws IOException {
        Path model = dir.resolve("test.model");
        try (var fos = Files.newOutputStream(model)) {
            var cos = CodedOutputStream.newInstance(fos);
            writePiece(cos, "<unk>", 0f, SentencePieceTokenizer.TYPE_UNKNOWN);
            writePiece(cos, "<s>", 0f, SentencePieceTokenizer.TYPE_CONTROL);
            writePiece(cos, "</s>", 0f, SentencePieceTokenizer.TYPE_CONTROL);
            writePiece(cos, "▁the", -1.0f, SentencePieceTokenizer.TYPE_NORMAL);
            writePiece(cos, "▁cat", -1.5f, SentencePieceTokenizer.TYPE_NORMAL);
            writePiece(cos, "▁sat", -2.0f, SentencePieceTokenizer.TYPE_NORMAL);
            writePiece(cos, "▁on", -1.8f, SentencePieceTokenizer.TYPE_NORMAL);
            writePiece(cos, "▁a", -0.5f, SentencePieceTokenizer.TYPE_NORMAL);
            writePiece(cos, "▁", -5.0f, SentencePieceTokenizer.TYPE_NORMAL);
            writePiece(cos, "s", -4.0f, SentencePieceTokenizer.TYPE_NORMAL);
            writePiece(cos, "at", -3.5f, SentencePieceTokenizer.TYPE_NORMAL);
            writePiece(cos, "he", -3.0f, SentencePieceTokenizer.TYPE_NORMAL);
            writePiece(cos, "▁he", -1.2f, SentencePieceTokenizer.TYPE_NORMAL);
            cos.flush();
        }
        return model;
    }

    private static void writePiece(CodedOutputStream cos, String piece,
                                   float score, int type) throws IOException {
        var buf = new ByteArrayOutputStream();
        var sub = CodedOutputStream.newInstance(buf);
        sub.writeString(1, piece);
        sub.writeFloat(2, score);
        sub.writeEnum(3, type);
        sub.flush();
        cos.writeByteArray(1, buf.toByteArray());
    }
}
