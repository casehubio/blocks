package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.SynthesisOptions;
import io.casehub.blocks.speech.SynthesisResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Audio8TextToSpeechTest {

    @Test
    void synthesiseReturnsWavFormatWithEmptyPhonemes(@TempDir Path tempDir) throws Exception {
        var tts = createTestInstance(tempDir);
        SynthesisResult result = tts.synthesise("hello", SynthesisOptions.defaults());

        assertThat(result.audioData()).isNotEmpty();
        assertThat(result.audioFormat()).isEqualTo("wav");
        assertThat(result.phonemes()).isEmpty();
    }

    @Test
    void synthesiseUsesRegisteredVoice(@TempDir Path tempDir) throws Exception {
        var tts = createTestInstance(tempDir);
        Path wavFile = tempDir.resolve("ref.wav");
        java.nio.file.Files.write(wavFile, WavWriter.encode(new float[100], 22050, 1));

        String voiceId = tts.registerVoice(wavFile);
        SynthesisResult result = tts.synthesise("hello",
                new SynthesisOptions(voiceId, null, "wav", false));

        assertThat(result.audioData()).isNotEmpty();
    }

    @Test
    void registerVoiceReturnsId(@TempDir Path tempDir) throws Exception {
        var tts = createTestInstance(tempDir);
        Path wavFile = tempDir.resolve("ref.wav");
        java.nio.file.Files.write(wavFile, WavWriter.encode(new float[100], 22050, 1));

        String id = tts.registerVoice(wavFile);
        assertThat(id).isNotNull().isNotEmpty();
        assertThat(tts.registeredVoices()).contains(id);
    }

    @Test
    void releaseVoiceRemovesRegistration(@TempDir Path tempDir) throws Exception {
        var tts = createTestInstance(tempDir);
        Path wavFile = tempDir.resolve("ref.wav");
        java.nio.file.Files.write(wavFile, WavWriter.encode(new float[100], 22050, 1));

        String id = tts.registerVoice(wavFile);
        tts.releaseVoice(id);
        assertThat(tts.registeredVoices()).doesNotContain(id);
    }

    @Test
    void closeReleasesResources(@TempDir Path tempDir) throws Exception {
        var tts = createTestInstance(tempDir);
        Path wavFile = tempDir.resolve("ref.wav");
        java.nio.file.Files.write(wavFile, WavWriter.encode(new float[100], 22050, 1));

        tts.registerVoice(wavFile);
        tts.close();
        assertThat(tts.registeredVoices()).isEmpty();
    }

    @Test
    void synthesiseWithNullVoiceUsesDefault(@TempDir Path tempDir) throws Exception {
        var tts = createTestInstance(tempDir);
        SynthesisResult result = tts.synthesise("hello", SynthesisOptions.defaults());

        assertThat(result).isNotNull();
        assertThat(result.audioData()).isNotEmpty();
    }

    @Test
    void synthesiseStreamingEmitsChunksProgressively(@TempDir Path tempDir) throws Exception {
        int numCodebooks = 3;
        int hop = 256;
        int totalFrames = 36;
        int chunkFrames = 12;
        int contextFrames = 24;
        int guardFrames = 1;

        Path tokPath = tempDir.resolve("tokenizer.json");
        java.nio.file.Files.writeString(tokPath, buildMinimalTokenizer());
        var tokenizer = Audio8Tokenizer.load(tokPath);
        var config    = Audio8Config.defaults(tempDir);

        Audio8TextToSpeech.FrameGenerator frameGen = (text, refText, voiceCodes, cfg, tok, consumer) -> {
            for (int i = 0; i < totalFrames; i++) {
                int[] frame = new int[numCodebooks];
                java.util.Arrays.fill(frame, i);
                consumer.accept(frame);
            }
        };

        Audio8TextToSpeech.Decoder decoder = frames -> new float[frames.length * hop];

        var streamingConfig = new Audio8TextToSpeech.StreamingConfig(hop, contextFrames, guardFrames, chunkFrames);

        var tts = new Audio8TextToSpeech(tokenizer, config,
                                         (text, refText, voiceCodes, cfg, tok) -> new int[0][],
                                         decoder, 22050, new VoiceRegistry(audio -> new int[]{1}),
                                         null, "", frameGen, streamingConfig);

        var chunks = new java.util.ArrayList<byte[]>();
        var seqs   = new java.util.ArrayList<Integer>();
        tts.synthesiseStreaming("hello", io.casehub.blocks.speech.SynthesisOptions.defaults(),
                                (wav, seq) -> {
                                    chunks.add(wav);
                                    seqs.add(seq);
                                });

        assertThat(chunks).isNotEmpty();
        assertThat(seqs).isSorted();
        for (byte[] wav : chunks) {
            assertThat(wav[0]).isEqualTo((byte) 'R');
            assertThat(wav[1]).isEqualTo((byte) 'I');
        }
    }

    @Test
    void synthesiseStreamingThrowsWhenNotAvailable(@TempDir Path tempDir) throws Exception {
        var tts = createTestInstance(tempDir);
        assertThatThrownBy(() -> tts.synthesiseStreaming("hello",
                                                         io.casehub.blocks.speech.SynthesisOptions.defaults(), (wav, seq) -> {}))
                .isInstanceOf(UnsupportedOperationException.class);
    }


    /**
     * Creates a test Audio8TextToSpeech with stub internals — no real ONNX sessions.
     * The tokenizer produces dummy IDs, DualARLoop returns canned codec frames,
     * and CodecDecoder returns synthetic audio.
     */
    private Audio8TextToSpeech createTestInstance(Path tempDir) throws Exception {
        // Stub tokenizer
        Path tokPath = tempDir.resolve("tokenizer.json");
        java.nio.file.Files.writeString(tokPath, buildMinimalTokenizer());
        var tokenizer = Audio8Tokenizer.load(tokPath);

        // Stub config
        var config = Audio8Config.defaults(tempDir);

        return new Audio8TextToSpeech(tokenizer, config,
                (text, refText, voiceCodes, cfg, tok) -> new int[][]{{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}},
                (frames) -> new float[]{0.1f, 0.2f, 0.3f},
                44100,
                new VoiceRegistry(audio -> new int[]{1, 2, 3}));
    }

    private String buildMinimalTokenizer() {
        var sb = new StringBuilder("{");
        char[] byteToUnicode = Audio8TokenizerTest.gpt2ByteToUnicode();
        for (int b = 0; b < 256; b++) {
            if (b > 0) sb.append(", ");
            char c = byteToUnicode[b];
            String key = switch (c) {
                case '"' -> "\\\"";
                case '\\' -> "\\\\";
                default -> String.valueOf(c);
            };
            sb.append("\"").append(key).append("\": ").append(b);
        }
        sb.append("}");
        return """
                {
                  "version": "1.0",
                  "added_tokens": [],
                  "normalizer": null,
                  "pre_tokenizer": {"type": "ByteLevel", "add_prefix_space": false, "trim_offsets": true, "use_regex": false},
                  "decoder": {"type": "ByteLevel"},
                  "model": {"type": "BPE", "vocab": %s, "merges": [], "byte_fallback": false}
                }
                """.formatted(sb.toString());
    }
}
