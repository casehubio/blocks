package io.casehub.blocks.speech.demo;

import io.casehub.blocks.speech.CleanupConfig;
import io.casehub.blocks.speech.TextToSpeechService;
import io.casehub.blocks.speech.sherpa.VitsTextToSpeech;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class SpeechProducers {

    private static final System.Logger LOG           = System.getLogger("speech-demo");
    private static final String        DEFAULT_MODEL = "claude-haiku-4-5@20251001";

    @Produces
    @ApplicationScoped
    TextToSpeechService tts() {
        return VitsTextToSpeech.withDefaults();
    }

    @Produces
    @jakarta.inject.Singleton
    io.casehub.blocks.speech.ws.TtsModelRegistry ttsRegistry() {
        var models = new java.util.LinkedHashMap<String, io.casehub.blocks.speech.TextToSpeechService>();

        // Shared lip-sync aligner — enriches any TTS engine that returns empty phonemes
        io.casehub.blocks.speech.PhonemeAligner aligner = null;
        try {
            aligner = io.casehub.blocks.speech.sherpa.EspeakPhonemeAligner.withDefaults();
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "EspeakPhonemeAligner unavailable — lip-sync disabled: " + e.getMessage());
        }

        // VITS models — native phoneme timing, no wrapping needed
        models.put("lessac-medium", VitsTextToSpeech.withDefaults());
        models.put("lessac-high", VitsTextToSpeech.withDefaults("vits-piper-en_US-lessac-high"));
        models.put("amy", VitsTextToSpeech.withDefaults("vits-piper-en_US-amy-medium"));
        models.put("ryan", VitsTextToSpeech.withDefaults("vits-piper-en_US-ryan-high"));
        models.put("jenny", VitsTextToSpeech.withDefaults("vits-piper-en_GB-jenny_dioco-medium"));

        // SherpaOnnx models — wrap with LipSyncEnricher for lip-sync
        models.put("sherpa:lessac-medium", wrapIfAvailable(io.casehub.blocks.speech.sherpa.SherpaOnnxTextToSpeech.withDefaults(), aligner));
        models.put("sherpa:lessac-high", wrapIfAvailable(io.casehub.blocks.speech.sherpa.SherpaOnnxTextToSpeech.withDefaults("vits-piper-en_US-lessac-high"), aligner));
        models.put("sherpa:amy", wrapIfAvailable(io.casehub.blocks.speech.sherpa.SherpaOnnxTextToSpeech.withDefaults("vits-piper-en_US-amy-medium"), aligner));
        models.put("sherpa:ryan", wrapIfAvailable(io.casehub.blocks.speech.sherpa.SherpaOnnxTextToSpeech.withDefaults("vits-piper-en_US-ryan-high"), aligner));
        models.put("sherpa:jenny", wrapIfAvailable(io.casehub.blocks.speech.sherpa.SherpaOnnxTextToSpeech.withDefaults("vits-piper-en_GB-jenny_dioco-medium"), aligner));

        // Kokoro models — wrap with LipSyncEnricher for lip-sync
        models.put("kokoro:af", wrapIfAvailable(io.casehub.blocks.speech.sherpa.KokoroTextToSpeech.withDefaults(0), aligner));
        models.put("kokoro:af_bella", wrapIfAvailable(io.casehub.blocks.speech.sherpa.KokoroTextToSpeech.withDefaults(1), aligner));
        models.put("kokoro:af_nicole", wrapIfAvailable(io.casehub.blocks.speech.sherpa.KokoroTextToSpeech.withDefaults(2), aligner));
        models.put("kokoro:af_sarah", wrapIfAvailable(io.casehub.blocks.speech.sherpa.KokoroTextToSpeech.withDefaults(3), aligner));
        models.put("kokoro:af_sky", wrapIfAvailable(io.casehub.blocks.speech.sherpa.KokoroTextToSpeech.withDefaults(4), aligner));
        models.put("kokoro:am_adam", wrapIfAvailable(io.casehub.blocks.speech.sherpa.KokoroTextToSpeech.withDefaults(5), aligner));
        models.put("kokoro:am_michael", wrapIfAvailable(io.casehub.blocks.speech.sherpa.KokoroTextToSpeech.withDefaults(6), aligner));
        models.put("kokoro:bf_emma", wrapIfAvailable(io.casehub.blocks.speech.sherpa.KokoroTextToSpeech.withDefaults(7), aligner));
        models.put("kokoro:bf_isabella", wrapIfAvailable(io.casehub.blocks.speech.sherpa.KokoroTextToSpeech.withDefaults(8), aligner));
        models.put("kokoro:bm_george", wrapIfAvailable(io.casehub.blocks.speech.sherpa.KokoroTextToSpeech.withDefaults(9), aligner));
        models.put("kokoro:bm_lewis", wrapIfAvailable(io.casehub.blocks.speech.sherpa.KokoroTextToSpeech.withDefaults(10), aligner));

        // Audio8 DualAR models — wrap with LipSyncEnricher for lip-sync
        try {
            models.put("audio8", wrapIfAvailable(io.casehub.blocks.speech.sherpa.Audio8TextToSpeech.withDefaults("0.1b"), aligner));
        } catch (Exception e) {
            LOG.log(System.Logger.Level.INFO, "Audio8 0.1b not available: " + e.getMessage());
        }
        try {
            models.put("audio8:0.6b", wrapIfAvailable(io.casehub.blocks.speech.sherpa.Audio8TextToSpeech.withDefaults("0.6b"), aligner));
        } catch (Exception e) {
            LOG.log(System.Logger.Level.INFO, "Audio8 0.6b not available: " + e.getMessage());
        }

        // CosyVoice3 pipeline — requires ORT 1.18.0 for FP16 models
        try {
            var cosyDir = java.nio.file.Path.of(System.getProperty("user.home"), ".casehub", "models", "cosyvoice3");
            var cosyPipeline = io.casehub.blocks.speech.sherpa.TtsPipeline.fromModelDir(cosyDir);
            models.put("cosyvoice3", wrapIfAvailable(cosyPipeline, aligner));
        } catch (Throwable e) {
            LOG.log(System.Logger.Level.WARNING, "CosyVoice3 not available: " + e, e);
        }

        return new io.casehub.blocks.speech.ws.TtsModelRegistry(java.util.Collections.unmodifiableMap(models));}

    private static io.casehub.blocks.speech.TextToSpeechService wrapIfAvailable(
            io.casehub.blocks.speech.TextToSpeechService delegate,
            io.casehub.blocks.speech.PhonemeAligner aligner) {
        if (aligner == null) {
            return delegate;
        }
        return io.casehub.blocks.speech.LipSyncEnricher.wrap(delegate, aligner);
    }


    @Produces
    @ApplicationScoped
    io.casehub.platform.agent.AgentProvider agentProvider() {
        String region    = System.getenv("CLOUD_ML_REGION");
        String projectId = System.getenv("ANTHROPIC_VERTEX_PROJECT_ID");
        if (region == null || projectId == null) {
            throw new IllegalStateException(
                    "CLOUD_ML_REGION and ANTHROPIC_VERTEX_PROJECT_ID required for Vertex AI");
        }
        var httpClient = java.net.http.HttpClient.newHttpClient();
        var gson       = new com.google.gson.Gson();

        return new io.casehub.platform.agent.AgentProvider() {
            @Override
            public io.smallrye.mutiny.Multi<io.casehub.platform.agent.AgentEvent> invoke(
                    io.casehub.platform.agent.AgentSessionConfig config) {
                return io.smallrye.mutiny.Multi.createFrom().item(() -> {
                    String modelId = config.model() != null ? config.model() : DEFAULT_MODEL;
                    String text = callVertex(httpClient, gson, region, projectId, modelId,
                                             config.systemPrompt(), config.userPrompt());
                    return (io.casehub.platform.agent.AgentEvent)
                                   new io.casehub.platform.agent.AgentEvent.TextDelta(text);
                });
            }

            @Override
            public io.casehub.platform.agent.AgentSession openSession(
                    io.casehub.platform.agent.AgentSessionInit init) {
                throw new UnsupportedOperationException("Use invoke() for avatar");
            }
        };
    }

    private static String callVertex(java.net.http.HttpClient httpClient, com.google.gson.Gson gson,
                                     String region, String projectId, String modelId,
                                     String systemPrompt, String userPrompt) {
        try {
            String token = getAccessToken();
            String url = "https://" + region + "-aiplatform.googleapis.com/v1/projects/"
                         + projectId + "/locations/" + region
                         + "/publishers/anthropic/models/" + modelId + ":rawPredict";

            var body = new com.google.gson.JsonObject();
            body.addProperty("anthropic_version", "vertex-2023-10-16");
            body.addProperty("max_tokens", 80);
            body.addProperty("system", systemPrompt);
            var messages = new com.google.gson.JsonArray();
            var msg      = new com.google.gson.JsonObject();
            msg.addProperty("role", "user");
            msg.addProperty("content", userPrompt);
            messages.add(msg);
            body.add("messages", messages);

            var request = java.net.http.HttpRequest.newBuilder()
                                                   .uri(java.net.URI.create(url))
                                                   .header("Authorization", "Bearer " + token)
                                                   .header("Content-Type", "application/json")
                                                   .POST(java.net.http.HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                                                   .build();

            var response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Vertex API error " + response.statusCode() + ": " + response.body());
            }

            var responseJson = com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject();
            return responseJson.getAsJsonArray("content")
                               .get(0).getAsJsonObject()
                               .get("text").getAsString();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Vertex AI call failed: " + e.getMessage(), e);
        }
    }

    private static String getAccessToken() {
        try {
            var process = new ProcessBuilder("/Users/mdproctor/google-cloud-sdk/bin/gcloud", "auth", "print-access-token")
                                  .redirectErrorStream(true).start();
            String token = new String(process.getInputStream().readAllBytes()).trim();
            process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            if (token.isEmpty() || process.exitValue() != 0) {
                throw new RuntimeException("gcloud auth failed: " + token);
            }
            return token;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get access token: " + e.getMessage(), e);
        }
    }

    @Produces
    @ApplicationScoped
    io.casehub.blocks.speech.StreamingSpeechToTextService stt() {
        try {
            if (io.casehub.blocks.speech.sherpa.WhisperLibrary.isAvailable()) {
                LOG.log(System.Logger.Level.INFO, "Using WhisperSpeechToText");
                return io.casehub.blocks.speech.sherpa.WhisperSpeechToText.withDefaults();
            }
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "Whisper init failed, falling back: " + e.getMessage());
        }
        LOG.log(System.Logger.Level.INFO, "Using Zipformer streaming STT");
        return io.casehub.blocks.speech.sherpa.SherpaOnnxStreamingSpeechToText.withDefaults();}

    @Produces
    @jakarta.inject.Singleton
    CleanupConfig cleanupConfig() {
        var filters = new java.util.ArrayList<io.casehub.blocks.speech.TextFilter>();
        filters.add(new io.casehub.blocks.speech.sherpa.FillerRemovalFilter());
        filters.add(new io.casehub.blocks.speech.sherpa.CasingFilter());
        try {
            filters.add(io.casehub.blocks.speech.sherpa.PunctuationFilter.withDefaults());
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "PunctuationFilter unavailable: " + e.getMessage());
        }
        try {
            filters.add(io.casehub.blocks.speech.sherpa.GectorFilter.withDefaults());
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "GECToR unavailable: " + e.getMessage());
        }
        return CleanupConfig.of(filters.toArray(io.casehub.blocks.speech.TextFilter[]::new));}

    @Produces
    @jakarta.inject.Singleton
    io.casehub.blocks.speech.sherpa.correction.ConversationVocabulary conversationVocabulary() {
        return new io.casehub.blocks.speech.sherpa.correction.ConversationVocabulary();
    }

    @Produces
    @jakarta.inject.Singleton
    io.casehub.blocks.speech.sherpa.correction.TranscriptCorrector transcriptCorrector(
            io.casehub.blocks.speech.sherpa.correction.ConversationVocabulary vocabulary) {
        try {
            var symSpell = io.casehub.blocks.speech.sherpa.correction.SymSpellIndex.fromResource(
                    "frequency_dictionary_en_82_765.txt");
            var phonetic = io.casehub.blocks.speech.sherpa.correction.PhoneticIndex.fromSymSpellIndex(symSpell);
            var ngram = io.casehub.blocks.speech.sherpa.correction.NgramModel.fromResource(
                    "frequency_bigramdictionary_en_243_342.txt");

            LOG.log(System.Logger.Level.INFO, "TranscriptCorrector loaded — {0} words, {1} bigrams",
                    symSpell.dictionary().size(), "243K");
            return new io.casehub.blocks.speech.sherpa.correction.TranscriptCorrector(
                    java.util.List.of(
                            new io.casehub.blocks.speech.sherpa.correction.SymSpellStrategy(symSpell),
                            new io.casehub.blocks.speech.sherpa.correction.PhoneticStrategy(phonetic)),
                    ngram, symSpell.dictionary());
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "TranscriptCorrector unavailable: " + e.getMessage());
            return new io.casehub.blocks.speech.sherpa.correction.TranscriptCorrector(
                    java.util.List.of(), null, java.util.Set.of());
        }}

    @Produces
    @jakarta.inject.Singleton
    io.casehub.blocks.speech.ws.CorrectionHooks correctionHooks(
            io.casehub.blocks.speech.sherpa.correction.TranscriptCorrector corrector,
            io.casehub.blocks.speech.sherpa.correction.ConversationVocabulary vocabulary) {
        return new io.casehub.blocks.speech.ws.CorrectionHooks(
                corrector::correct,
                response -> {
                    vocabulary.addFromText(response);
                    corrector.addVocabulary(vocabulary.terms().toArray(String[]::new));
                },
                vocabulary::asPromptHint);
    }


}
