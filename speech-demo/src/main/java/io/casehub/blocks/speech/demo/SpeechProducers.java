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
        return new io.casehub.blocks.speech.ws.TtsModelRegistry(java.util.Map.of(
                "lessac-medium", VitsTextToSpeech.withDefaults(),
                "lessac-high", VitsTextToSpeech.withDefaults("vits-piper-en_US-lessac-high"),
                "amy", VitsTextToSpeech.withDefaults("vits-piper-en_US-amy-medium"),
                "ryan", VitsTextToSpeech.withDefaults("vits-piper-en_US-ryan-high"),
                "jenny", VitsTextToSpeech.withDefaults("vits-piper-en_GB-jenny_dioco-medium")));
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
        return options -> {throw new UnsupportedOperationException("STT not configured — use text input");};
    }

    @Produces
    @jakarta.inject.Singleton
    CleanupConfig cleanupConfig() {
        return CleanupConfig.of();
    }
}
