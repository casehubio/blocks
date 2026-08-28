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
        models.put("lessac-medium", VitsTextToSpeech.withDefaults());
        models.put("lessac-high", VitsTextToSpeech.withDefaults("vits-piper-en_US-lessac-high"));
        models.put("amy", VitsTextToSpeech.withDefaults("vits-piper-en_US-amy-medium"));
        models.put("ryan", VitsTextToSpeech.withDefaults("vits-piper-en_US-ryan-high"));
        models.put("jenny", VitsTextToSpeech.withDefaults("vits-piper-en_GB-jenny_dioco-medium"));
        models.put("sherpa:lessac-medium", io.casehub.blocks.speech.sherpa.SherpaOnnxTextToSpeech.withDefaults());
        models.put("sherpa:lessac-high", io.casehub.blocks.speech.sherpa.SherpaOnnxTextToSpeech.withDefaults("vits-piper-en_US-lessac-high"));
        models.put("sherpa:amy", io.casehub.blocks.speech.sherpa.SherpaOnnxTextToSpeech.withDefaults("vits-piper-en_US-amy-medium"));
        models.put("sherpa:ryan", io.casehub.blocks.speech.sherpa.SherpaOnnxTextToSpeech.withDefaults("vits-piper-en_US-ryan-high"));
        models.put("sherpa:jenny", io.casehub.blocks.speech.sherpa.SherpaOnnxTextToSpeech.withDefaults("vits-piper-en_GB-jenny_dioco-medium"));
        models.put("kokoro:af", io.casehub.blocks.speech.sherpa.KokoroTextToSpeech.withDefaults(0));
        models.put("kokoro:af_bella", io.casehub.blocks.speech.sherpa.KokoroTextToSpeech.withDefaults(1));
        models.put("kokoro:af_nicole", io.casehub.blocks.speech.sherpa.KokoroTextToSpeech.withDefaults(2));
        models.put("kokoro:af_sarah", io.casehub.blocks.speech.sherpa.KokoroTextToSpeech.withDefaults(3));
        models.put("kokoro:af_sky", io.casehub.blocks.speech.sherpa.KokoroTextToSpeech.withDefaults(4));
        models.put("kokoro:am_adam", io.casehub.blocks.speech.sherpa.KokoroTextToSpeech.withDefaults(5));
        models.put("kokoro:am_michael", io.casehub.blocks.speech.sherpa.KokoroTextToSpeech.withDefaults(6));
        models.put("kokoro:bf_emma", io.casehub.blocks.speech.sherpa.KokoroTextToSpeech.withDefaults(7));
        models.put("kokoro:bf_isabella", io.casehub.blocks.speech.sherpa.KokoroTextToSpeech.withDefaults(8));
        models.put("kokoro:bm_george", io.casehub.blocks.speech.sherpa.KokoroTextToSpeech.withDefaults(9));
        models.put("kokoro:bm_lewis", io.casehub.blocks.speech.sherpa.KokoroTextToSpeech.withDefaults(10));
        return new io.casehub.blocks.speech.ws.TtsModelRegistry(java.util.Collections.unmodifiableMap(models));}

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
