package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.SynthesisOptions;
import io.casehub.blocks.speech.SynthesisResult;
import io.casehub.blocks.speech.TextToSpeechService;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

public final class TtsPipeline implements TextToSpeechService, AutoCloseable {
    private static final System.Logger LOG = System.getLogger("casehub-speech");


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
        OnnxRuntimeLibrary ort     = OnnxRuntimeLibrary.load();
        int                threads = 4;

        var campplus              = ort.createSession(modelDir.resolve("campplus.onnx"), threads);
        var speechTokenizer       = ort.createSession(modelDir.resolve("speech_tokenizer_v3.onnx"), threads);
        var textEmbedding         = ort.createSession(modelDir.resolve("text_embedding_fp32.onnx"), threads);
        var speechEmbedding       = ort.createSession(modelDir.resolve("llm_speech_embedding_fp16.onnx"), threads);
        var llmInitial            = ort.createSession(modelDir.resolve("llm_backbone_initial_fp16.onnx"), threads);
        var llmDecode             = ort.createSession(modelDir.resolve("llm_backbone_decode_fp16.onnx"), threads);
        var llmDecoder            = ort.createSession(modelDir.resolve("llm_decoder_fp16.onnx"), threads);
        var flowTokenEmbedding    = ort.createSession(modelDir.resolve("flow_token_embedding_fp16.onnx"), threads);
        var flowPreLookahead      = ort.createSession(modelDir.resolve("flow_pre_lookahead_fp16.onnx"), threads);
        var flowSpeakerProjection = ort.createSession(modelDir.resolve("flow_speaker_projection_fp16.onnx"), threads);
        var flowEstimator         = ort.createSession(modelDir.resolve("flow.decoder.estimator.fp16.onnx"), threads);
        var hiftF0                = ort.createSession(modelDir.resolve("hift_f0_predictor_fp32.onnx"), threads);
        var hiftSource            = ort.createSession(modelDir.resolve("hift_source_generator_fp32.onnx"), threads);
        var hiftDecoder           = ort.createSession(modelDir.resolve("hift_decoder_fp32.onnx"), threads);

        CosyVoice3Tokenizer tokenizer;
        try {
            tokenizer = CosyVoice3Tokenizer.load(modelDir.resolve(manifest.tokenizerDir()));
        } catch (java.io.IOException e) {
            var sessions = new AutoCloseable[]{campplus, speechTokenizer, textEmbedding,
                                               speechEmbedding, llmInitial, llmDecode, llmDecoder,
                                               flowTokenEmbedding, flowPreLookahead, flowSpeakerProjection,
                                               flowEstimator, hiftF0, hiftSource, hiftDecoder};
            for (var s : sessions) {try {s.close();} catch (Exception ignored) {}}
            throw new SherpaException("Failed to load CosyVoice3 tokenizer", e);
        }

        var voiceEncoder = CosyVoice3VoiceEncoder.fromSessions(campplus, speechTokenizer, manifest);
        var generator = CosyVoice3Generator.fromSessions(textEmbedding, speechEmbedding,
                                                         llmInitial, llmDecode, llmDecoder, manifest, tokenizer);
        var decoder = CosyVoice3Decoder.fromSessions(flowTokenEmbedding, flowPreLookahead,
                                                     flowSpeakerProjection, flowEstimator, hiftF0, hiftSource, hiftDecoder, manifest);

        VoiceData defaultVoice = null;
        if (!manifest.defaultPrompts().isEmpty()) {
            var  entry      = manifest.defaultPrompts().entrySet().iterator().next();
            Path promptPath = modelDir.resolve("prompts").resolve(entry.getKey());
            if (java.nio.file.Files.exists(promptPath)) {
                try {
                    byte[] audioData = java.nio.file.Files.readAllBytes(promptPath);
                    defaultVoice = voiceEncoder.encode(audioData, entry.getValue());
                } catch (Exception e) {
                    LOG.log(System.Logger.Level.WARNING, "Failed to load default voice: {0}", e.getMessage());
                }
            }
        }

        var voiceRegistry = new VoiceRegistry(voiceEncoder, defaultVoice);
        var allSessions = new AutoCloseable[]{campplus, speechTokenizer, textEmbedding,
                                              speechEmbedding, llmInitial, llmDecode, llmDecoder,
                                              flowTokenEmbedding, flowPreLookahead, flowSpeakerProjection,
                                              flowEstimator, hiftF0, hiftSource, hiftDecoder};

        return new TtsPipeline(manifest, tokenizer, generator, decoder,
                               voiceRegistry, manifest.header().sampleRate(), allSessions);
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
