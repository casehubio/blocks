package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.SynthesisOptions;
import io.casehub.blocks.speech.SynthesisResult;
import io.casehub.blocks.speech.TextToSpeechService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TtsPipelineTest {

    @Test void synthesiseDelegatesToStages() {
        var manifest = testManifest();
        TtsTokenizer tokenizer = text -> new int[]{1, 2, 3};
        TtsGenerator generator = (tokens, voice, config) ->
                new GeneratorOutput.SpeechTokenOutput(new int[]{100, 200});
        TtsDecoder decoder = (output, voice) -> new float[]{0.5f, -0.5f, 0.3f};
        var registry = new VoiceRegistry((audio, transcript) ->
                new VoiceData.CodecVoiceData(new int[]{1}));

        var pipeline = new TtsPipeline(manifest, tokenizer, generator, decoder,
                registry, 24000);

        SynthesisResult result = pipeline.synthesise("hello", SynthesisOptions.defaults());
        assertThat(result.audioData()).isNotEmpty();
        assertThat(result.audioFormat()).isEqualTo("wav");
        assertThat(result.phonemes()).isEmpty();
    }

    @Test void implementsTextToSpeechService() {
        var manifest = testManifest();
        TtsTokenizer tokenizer = text -> new int[]{1};
        TtsGenerator generator = (tokens, voice, config) ->
                new GeneratorOutput.SpeechTokenOutput(new int[]{1});
        TtsDecoder decoder = (output, voice) -> new float[]{0.1f};
        var registry = new VoiceRegistry((audio, transcript) ->
                new VoiceData.CodecVoiceData(new int[]{1}));

        TextToSpeechService svc = new TtsPipeline(manifest, tokenizer, generator,
                decoder, registry, 24000);
        assertThat(svc).isInstanceOf(TextToSpeechService.class);
    }

    @Test void resolveVoiceUsesRegisteredVoice(@TempDir Path tempDir) throws Exception {
        var manifest = testManifest();
        var capturedVoice = new VoiceData[1];
        TtsTokenizer tokenizer = text -> new int[]{1};
        TtsGenerator generator = (tokens, voice, config) -> {
            capturedVoice[0] = voice;
            return new GeneratorOutput.SpeechTokenOutput(new int[]{1});
        };
        TtsDecoder decoder = (output, voice) -> new float[]{0.1f};
        var registry = new VoiceRegistry((audio, transcript) ->
                new VoiceData.CodecVoiceData(new int[]{42}));

        var pipeline = new TtsPipeline(manifest, tokenizer, generator, decoder,
                registry, 24000);

        Path wav = tempDir.resolve("ref.wav");
        java.nio.file.Files.write(wav, WavWriter.encode(new float[50], 22050, 1));
        String voiceId = pipeline.registerVoice(wav);

        pipeline.synthesise("hi", new SynthesisOptions(voiceId, null, "wav", false));

        assertThat(capturedVoice[0]).isInstanceOf(VoiceData.CodecVoiceData.class);
        assertThat(((VoiceData.CodecVoiceData) capturedVoice[0]).codecTokens())
                .containsExactly(42);
    }

    @Test void resolveVoiceUsesDefaultWhenNoVoiceSpecified() {
        var defaultVoice = new VoiceData.CodecVoiceData(new int[]{99});
        var manifest = testManifest();
        var capturedVoice = new VoiceData[1];
        TtsTokenizer tokenizer = text -> new int[]{1};
        TtsGenerator generator = (tokens, voice, config) -> {
            capturedVoice[0] = voice;
            return new GeneratorOutput.SpeechTokenOutput(new int[]{1});
        };
        TtsDecoder decoder = (output, voice) -> new float[]{0.1f};
        var registry = new VoiceRegistry((audio, transcript) ->
                new VoiceData.CodecVoiceData(new int[]{1}), defaultVoice);

        var pipeline = new TtsPipeline(manifest, tokenizer, generator, decoder,
                registry, 24000);
        pipeline.synthesise("hi", SynthesisOptions.defaults());

        assertThat(capturedVoice[0]).isSameAs(defaultVoice);
    }

    @Test void sessionCountReflectsOwnedResources() {
        var manifest = testManifest();
        TtsTokenizer tokenizer = text -> new int[]{1};
        TtsGenerator generator = (tokens, voice, config) ->
                new GeneratorOutput.SpeechTokenOutput(new int[]{1});
        TtsDecoder decoder = (output, voice) -> new float[]{0.1f};
        var registry = new VoiceRegistry((audio, transcript) ->
                new VoiceData.CodecVoiceData(new int[]{1}));

        AutoCloseable r1 = () -> {};
        AutoCloseable r2 = () -> {};
        var pipeline = new TtsPipeline(manifest, tokenizer, generator, decoder,
                registry, 24000, r1, r2);

        assertThat(pipeline.sessionCount()).isEqualTo(2);
    }

    @Test void modelNameReturnsManifestName() {
        var manifest = testManifest();
        TtsTokenizer tokenizer = text -> new int[]{1};
        TtsGenerator generator = (tokens, voice, config) ->
                new GeneratorOutput.SpeechTokenOutput(new int[]{1});
        TtsDecoder decoder = (output, voice) -> new float[]{0.1f};
        var registry = new VoiceRegistry((audio, transcript) ->
                new VoiceData.CodecVoiceData(new int[]{1}));

        var pipeline = new TtsPipeline(manifest, tokenizer, generator, decoder,
                registry, 24000);
        assertThat(pipeline.modelName()).isEqualTo("test-model");
    }

    @Test void closeReleasesOwnedResources() {
        var manifest = testManifest();
        boolean[] closed = {false, false};
        TtsTokenizer tokenizer = text -> new int[]{1};
        TtsGenerator generator = (tokens, voice, config) ->
                new GeneratorOutput.SpeechTokenOutput(new int[]{1});
        TtsDecoder decoder = (output, voice) -> new float[]{0.1f};
        var registry = new VoiceRegistry((audio, transcript) ->
                new VoiceData.CodecVoiceData(new int[]{1}));

        AutoCloseable r1 = () -> closed[0] = true;
        AutoCloseable r2 = () -> closed[1] = true;
        var pipeline = new TtsPipeline(manifest, tokenizer, generator, decoder,
                registry, 24000, r1, r2);

        pipeline.close();
        assertThat(closed[0]).isTrue();
        assertThat(closed[1]).isTrue();
    }

    private static CosyVoice3Manifest testManifest() {
        var header = new PipelineHeader("test-model", 24000,
                Map.of("generator", List.of("model.onnx")),
                null, Map.of());
        return new CosyVoice3Manifest(header, 896, 6561, 6561, 6562, 6563,
                24, 64, 2, 10, 80, 16, 4, 192, "tokenizer", Map.of());
    }
}
