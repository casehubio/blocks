package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class Audio8TokenizerTest {

    private static final Path REAL_TOKENIZER = resolveRealTokenizer();

    @Nested
    class WithRealTokenizer {

        private static Audio8Tokenizer tokenizer;

        @BeforeAll
        static void loadTokenizer() throws Exception {
            assumeTrue(Files.exists(REAL_TOKENIZER), "Real tokenizer.json not available");
            tokenizer = Audio8Tokenizer.load(REAL_TOKENIZER);
        }

        @Test
        void encodesBasicText() {
            assertThat(tokenizer.encode("Hello world")).containsExactly(18974, 3358);
        }

        @Test
        void encodesSpecialTokenFollowedByText() {
            assertThat(tokenizer.encode("<|im_start|>system")).containsExactly(227, 13354);
        }

        @Test
        void encodesVoiceToken() {
            assertThat(tokenizer.encode("<|voice|>")).containsExactly(65536);
        }

        @Test
        void encodesPromptText() {
            assertThat(tokenizer.encode("convert the provided text to speech"))
                    .containsExactly(22558, 790, 4930, 2757, 831, 19822);
        }

        @Test
        void encodesTextWithNewline() {
            int[] result = tokenizer.encode("hello\nworld");
            assertThat(result).isNotEmpty();
            assertThat(tokenizer.decode(result)).isEqualTo("hello\nworld");
        }

        @Test
        void encodesTextWithPunctuation() {
            int[] result = tokenizer.encode("Hello, world!");
            assertThat(result).isNotEmpty();
            assertThat(tokenizer.decode(result)).isEqualTo("Hello, world!");
        }

        @Test
        void encodesTextWithNumbers() {
            int[] result = tokenizer.encode("test 123");
            assertThat(result).isNotEmpty();
            assertThat(tokenizer.decode(result)).isEqualTo("test 123");
        }

        @Test
        void decodesTokenIds() {
            assertThat(tokenizer.decode(new int[]{18974, 3358})).isEqualTo("Hello world");
        }

        @Test
        void roundTripEncodeDecode() {
            String text = "The quick brown fox jumps over the lazy dog.";
            assertThat(tokenizer.decode(tokenizer.encode(text))).isEqualTo(text);
        }

        @Test
        void roundTripWithSpecialTokens() {
            String text = "<|im_start|>system\nconvert text\n<|im_end|>";
            assertThat(tokenizer.decode(tokenizer.encode(text))).isEqualTo(text);
        }
    }

    @Test
    void encodesEmptyString(@TempDir Path tempDir) throws Exception {
        var tokenizer = Audio8Tokenizer.load(writeMinimalTokenizer(tempDir));
        assertThat(tokenizer.encode("")).isEmpty();
    }

    @Test
    void encodesNullAsEmpty(@TempDir Path tempDir) throws Exception {
        var tokenizer = Audio8Tokenizer.load(writeMinimalTokenizer(tempDir));
        assertThat(tokenizer.encode(null)).isEmpty();
    }

    @Test
    void appliesBpeMerges(@TempDir Path tempDir) throws Exception {
        var tokenizer = Audio8Tokenizer.load(writeBpeTestTokenizer(tempDir));
        // "hi" → bytes [0x68, 0x69] → byte-level chars ['h', 'i']
        // merge "h i" → "hi" (vocab id 258)
        assertThat(tokenizer.encode("hi")).containsExactly(258);
    }

    @Test
    void encodesUnmergedCharsIndividually(@TempDir Path tempDir) throws Exception {
        var tokenizer = Audio8Tokenizer.load(writeBpeTestTokenizer(tempDir));
// "ab" → bytes [0x61, 0x62] → byte-level chars ['a', 'b']
// no merge for "a b" → individual byte-level token IDs (byte value = vocab ID in fixture)
        assertThat(tokenizer.encode("ab")).containsExactly(0x61, 0x62);}

    @Test
    void matchesAddedTokens(@TempDir Path tempDir) throws Exception {
        var tokenizer = Audio8Tokenizer.load(writeBpeTestTokenizer(tempDir));
        // "<|test|>" is an added token (id 300)
        assertThat(tokenizer.encode("<|test|>")).containsExactly(300);
    }

    @Test
    void decodesReversesEncoding(@TempDir Path tempDir) throws Exception {
        var tokenizer = Audio8Tokenizer.load(writeBpeTestTokenizer(tempDir));
        assertThat(tokenizer.decode(new int[]{258})).isEqualTo("hi");
    }

    private static Path writeMinimalTokenizer(Path dir) throws IOException {
        Path path = dir.resolve("tokenizer.json");
        var vocab = buildByteVocab(0);
        Files.writeString(path, """
                {
                  "version": "1.0",
                  "added_tokens": [],
                  "normalizer": null,
                  "pre_tokenizer": {"type": "ByteLevel", "add_prefix_space": false, "trim_offsets": true, "use_regex": false},
                  "post_processor": null,
                  "decoder": {"type": "ByteLevel"},
                  "model": {
                    "type": "BPE",
                    "vocab": %s,
                    "merges": [],
                    "byte_fallback": false
                  }
                }
                """.formatted(vocab));
        return path;
    }

    private static Path writeBpeTestTokenizer(Path dir) throws IOException {
        Path path = dir.resolve("tokenizer.json");
        // Build vocab: 256 byte-level chars (IDs 0-255) + merged "hi" (ID 258) + special "<|test|>" (ID 300)
        var vocab = buildByteVocab(0);
        // Add merged token: "hi" at ID 258 (after 256 byte chars + 2 for merge expansion)
        var vocabWithMerge = vocab.substring(0, vocab.length() - 1)
                + ", \"hi\": 258}";
        Files.writeString(path, """
                {
                  "version": "1.0",
                  "added_tokens": [
                    {"id": 300, "content": "<|test|>", "single_word": false, "lstrip": false, "rstrip": false, "normalized": false, "special": true}
                  ],
                  "normalizer": null,
                  "pre_tokenizer": {"type": "ByteLevel", "add_prefix_space": false, "trim_offsets": true, "use_regex": false},
                  "post_processor": null,
                  "decoder": {"type": "ByteLevel"},
                  "model": {
                    "type": "BPE",
                    "vocab": %s,
                    "merges": ["h i"],
                    "byte_fallback": false
                  }
                }
                """.formatted(vocabWithMerge));
        return path;
    }

    /**
     * Builds a JSON vocab object with the 256 GPT-2 byte-level characters.
     * Each byte (0-255) maps to a printable Unicode character.
     */
    private static String buildByteVocab(int idOffset) {
        var sb = new StringBuilder("{");
        char[] byteToUnicode = gpt2ByteToUnicode();
        for (int b = 0; b < 256; b++) {
            if (b > 0) sb.append(", ");
            char c = byteToUnicode[b];
            String key = switch (c) {
                case '"' -> "\\\"";
                case '\\' -> "\\\\";
                default -> String.valueOf(c);
            };
            sb.append("\"").append(key).append("\": ").append(b + idOffset);
        }
        sb.append("}");
        return sb.toString();
    }

    /** GPT-2 bytes_to_unicode mapping — maps each byte to a printable Unicode character. */
    static char[] gpt2ByteToUnicode() {
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

    private static Path resolveRealTokenizer() {
        Path base = Path.of(System.getProperty("user.home"),
                ".cache", "huggingface", "hub",
                "models--Audio8--audio8-TTS-0.1B-ONNX-INT8");
        if (!Files.isDirectory(base)) return base.resolve("tokenizer.json");
        try (var stream = Files.walk(base)) {
            return stream
                    .filter(p -> p.endsWith("tokenizer/tokenizer.json"))
                    .findFirst()
                    .orElse(base.resolve("tokenizer.json"));
        } catch (IOException e) {
            return base.resolve("tokenizer.json");
        }
    }
}
