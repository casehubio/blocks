package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.SynthesisOptions;
import io.casehub.blocks.speech.SynthesisResult;
import io.casehub.blocks.speech.TextToSpeechService;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

public final class TtsPipeline implements TextToSpeechService, AutoCloseable {

    private final TtsPipelineManifest manifest;
    private final TtsTokenizer tokenizer;
    private final TtsGenerator generator;
    private final TtsDecoder decoder;
    private final VoiceRegistry voiceRegistry;
    private final int sampleRate;
    private final AutoCloseable[] ownedResources;

    TtsPipeline(TtsPipelineManifest manifest, TtsTokenizer tokenizer,
                TtsGenerator generator, TtsDecoder decoder,
                VoiceRegistry voiceRegistry, int sampleRate,
                AutoCloseable... ownedResources) {
        this.manifest = manifest;
        this.tokenizer = tokenizer;
        this.generator = generator;
        this.decoder = decoder;
        this.voiceRegistry = voiceRegistry;
        this.sampleRate = sampleRate;
        this.ownedResources = ownedResources;
    }

    public static TtsPipeline fromModelDir(Path modelDir) {
        var manifestFile = modelDir.resolve("pipeline_manifest.json");
        var parsed = CosyVoice3Manifest.load(manifestFile);
        return switch (parsed) {
            case CosyVoice3Manifest cv3 -> buildCosyVoice3(modelDir, cv3);
        };
    }

    private static TtsPipeline buildCosyVoice3(Path modelDir, CosyVoice3Manifest manifest) {
        throw new UnsupportedOperationException(
                "CosyVoice3 pipeline construction not yet implemented — see Batch 4, Task 10");
    }

    @Override
    public SynthesisResult synthesise(String text, SynthesisOptions options) {
        int[] tokens = tokenizer.encode(text);
        VoiceData voice = resolveVoice(options);
        GeneratorConfig config = generatorConfig();
        GeneratorOutput output = generator.generate(tokens, voice, config);
        float[] samples = decoder.decode(output, voice);
        byte[] wav = WavWriter.encode(samples, sampleRate, 1);
        return new SynthesisResult(wav, "wav", List.of());
    }

    public String registerVoice(Path referenceAudio) {
        return voiceRegistry.register(referenceAudio);
    }

    public String registerVoice(Path referenceAudio, String transcript) {
        return voiceRegistry.register(referenceAudio, transcript);
    }

    public void releaseVoice(String voiceId) {
        voiceRegistry.release(voiceId);
    }

    public int sessionCount() {
        return ownedResources.length;
    }

    public String modelName() {
        return manifest.header().name();
    }

    @Override
    public void close() {
        for (AutoCloseable r : ownedResources) {
            try {
                r.close();
            } catch (Exception ignored) {}
        }
        voiceRegistry.close();
    }

    private @Nullable VoiceData resolveVoice(SynthesisOptions options) {
        if (options.voice() != null && !options.voice().isEmpty()) {
            return voiceRegistry.get(options.voice());
        }
        return voiceRegistry.defaultVoice();
    }

    private GeneratorConfig generatorConfig() {
        return GeneratorConfig.defaults();
    }
}
