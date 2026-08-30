package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class CosyVoice3TokenizerTest {

    private static CosyVoice3Tokenizer tokenizer;

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void setup() throws IOException {
        // Build a minimal Qwen2-style vocab and merges for testing.
        // Byte-level BPE: the base vocab maps each byte-level character to an ID.
        // Then merges combine pairs into longer tokens.

        // Base byte-level chars: GPT-2 mapping gives chars for bytes 0-255.
        // For simplicity, build vocab with ASCII printable range + common merges.
        var vocabBuilder = new StringBuilder("{\n");
        int id = 0;

        // Map all 256 byte-level chars to IDs 0-255
        char[] byteEncoder = buildByteToUnicode();
        for (int b = 0; b < 256; b++) {
            if (id > 0) vocabBuilder.append(",\n");
            vocabBuilder.append("  \"").append(escapeJson(String.valueOf(byteEncoder[b]))).append("\": ").append(id);
            id++;
        }

        // Add merged tokens
        String[][] mergedTokens = {
                {"H", "e"}, {"l", "l"}, {"He", "ll"}, {"o", " "},
                {"Hell", "o"}, {"w", "o"}, {"r", "l"}, {"d", "!"},
                {"wo", "rl"}, {"Hello", " "}, {" ", "w"},
        };
        String[] mergeLines = new String[mergedTokens.length];
        for (int i = 0; i < mergedTokens.length; i++) {
            String left = toByteLevelChars(mergedTokens[i][0], byteEncoder);
            String right = toByteLevelChars(mergedTokens[i][1], byteEncoder);
            String merged = left + right;
            vocabBuilder.append(",\n  \"").append(escapeJson(merged)).append("\": ").append(id);
            mergeLines[i] = left + " " + right;
            id++;
        }
        vocabBuilder.append("\n}");

        Files.writeString(tempDir.resolve("vocab.json"), vocabBuilder.toString());

        var mergesContent = new StringBuilder("#version: 0.2\n");
        for (String line : mergeLines) {
            mergesContent.append(line).append("\n");
        }
        Files.writeString(tempDir.resolve("merges.txt"), mergesContent.toString());

        tokenizer = CosyVoice3Tokenizer.load(tempDir);
    }

    @Test void encodesSimpleText() {
        int[] ids = tokenizer.encode("Hello");
        assertThat(ids).isNotEmpty();
        // Should produce the merged "Hello" token (one of our defined merges)
        String decoded = tokenizer.decode(ids);
        assertThat(decoded).isEqualTo("Hello");
    }

    @Test void encodeDecodeRoundTrips() {
        String[] texts = {"Hello", "world", "a", "test"};
        for (String text : texts) {
            int[] ids = tokenizer.encode(text);
            String decoded = tokenizer.decode(ids);
            assertThat(decoded).as("round-trip for '%s'", text).isEqualTo(text);
        }
    }

    @Test void emptyStringProducesEmptyArray() {
        assertThat(tokenizer.encode("")).isEmpty();
        assertThat(tokenizer.encode(null)).isEmpty();
    }

    @Test void singleCharacterEncodes() {
        int[] ids = tokenizer.encode("a");
        assertThat(ids).hasSize(1);
        assertThat(tokenizer.decode(ids)).isEqualTo("a");
    }

    @Test void unknownBytesStillEncode() {
        // All bytes have base-level tokens, so even unusual chars should encode
        int[] ids = tokenizer.encode("\t");
        assertThat(ids).isNotEmpty();
        assertThat(tokenizer.decode(ids)).isEqualTo("\t");
    }

    @Test void spacesEncodeCorrectly() {
        int[] ids = tokenizer.encode(" ");
        assertThat(ids).hasSize(1);
        assertThat(tokenizer.decode(ids)).isEqualTo(" ");
    }

    @Test void mergesApplyGreedily() {
        // "Hell" should be merged: H+e → He, l+l → ll, He+ll → Hell
        int[] ids = tokenizer.encode("Hell");
        // Should be fewer tokens than 4 individual chars
        assertThat(ids.length).isLessThan(4);
        assertThat(tokenizer.decode(ids)).isEqualTo("Hell");
    }

    @Test void noSpecialTokensAdded() {
        // CosyVoice3 uses add_special_tokens=False
        int[] ids = tokenizer.encode("test");
        String decoded = tokenizer.decode(ids);
        assertThat(decoded).isEqualTo("test");
        // No BOS/EOS tokens — the decoded text is exactly the input
    }

    @Test void implementsTtsTokenizer() {
        assertThat(tokenizer).isInstanceOf(TtsTokenizer.class);
    }

    @Test void loadThrowsOnMissingVocab(@TempDir Path emptyDir) {
        assertThatThrownBy(() -> CosyVoice3Tokenizer.load(emptyDir))
                .isInstanceOf(IOException.class);
    }

    // --- Helpers ---

    private static char[] buildByteToUnicode() {
        var table = new char[256];
        int n = 0;
        for (int b = 0; b < 256; b++) {
            if ((b >= 0x21 && b <= 0x7E) || (b >= 0xA1 && b <= 0xAC) || (b >= 0xAE && b <= 0xFF)) {
                table[b] = (char) b;
            } else {
                table[b] = (char) (256 + n);
                n++;
            }
        }
        return table;
    }

    private static String toByteLevelChars(String text, char[] byteEncoder) {
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var sb = new StringBuilder(bytes.length);
        for (byte b : bytes) sb.append(byteEncoder[b & 0xFF]);
        return sb.toString();
    }

    private static String escapeJson(String s) {
        var sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20 || c > 0x7E) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
