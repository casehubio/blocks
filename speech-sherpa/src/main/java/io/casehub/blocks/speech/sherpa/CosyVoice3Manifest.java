package io.casehub.blocks.speech.sherpa;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CosyVoice3Manifest(
        PipelineHeader header,
        int hiddenDim,
        int speechVocabSize,
        int sosId,
        int eosId,
        int taskId,
        int numLlmLayers,
        int kvHeadDim,
        int tokenMelRatio,
        int flowSteps,
        int melBins,
        int hiftNFft,
        int hiftHopLength,
        int speakerEmbedDim,
        String tokenizerDir,
        Map<String, String> defaultPrompts
) implements TtsPipelineManifest {

    static CosyVoice3Manifest load(Path manifestFile) {
        try {
            String json = Files.readString(manifestFile);
            return parse(json);
        } catch (IOException e) {
            throw new SherpaException("Failed to read manifest: " + manifestFile, e);
        }
    }

    static CosyVoice3Manifest parse(String json) {
        var gson = new Gson();
        var root = gson.fromJson(json, JsonObject.class);

        var headerObj = root.getAsJsonObject("header");
        String name = headerObj.get("name").getAsString();
        int sampleRate = headerObj.get("sampleRate").getAsInt();

        Map<String, List<String>> stageModels = new LinkedHashMap<>();
        var stageModelsObj = headerObj.getAsJsonObject("stageModels");
        for (var entry : stageModelsObj.entrySet()) {
            var files = entry.getValue().getAsJsonArray();
            var list = new java.util.ArrayList<String>();
            files.forEach(f -> list.add(f.getAsString()));
            stageModels.put(entry.getKey(), List.copyOf(list));
        }

        Map<String, String> metadata = new LinkedHashMap<>();
        if (headerObj.has("metadata")) {
            var metaObj = headerObj.getAsJsonObject("metadata");
            for (var entry : metaObj.entrySet()) {
                metadata.put(entry.getKey(), entry.getValue().getAsString());
            }
        }

        ExecutionProviderConfig provider = null;
        if (headerObj.has("provider") && !headerObj.get("provider").isJsonNull()) {
            var provObj = headerObj.getAsJsonObject("provider");
            Integer deviceId = provObj.has("deviceId") ? provObj.get("deviceId").getAsInt() : null;
            Map<String, String> overrides = new LinkedHashMap<>();
            if (provObj.has("stageOverrides")) {
                for (var e : provObj.getAsJsonObject("stageOverrides").entrySet()) {
                    overrides.put(e.getKey(), e.getValue().getAsString());
                }
            }
            provider = new ExecutionProviderConfig(
                    provObj.get("preferred").getAsString(), deviceId, Map.copyOf(overrides));
        }

        var header = new PipelineHeader(name, sampleRate,
                Map.copyOf(stageModels), provider, Map.copyOf(metadata));

        Map<String, String> defaultPrompts = new LinkedHashMap<>();
        if (root.has("defaultPrompts")) {
            for (var e : root.getAsJsonObject("defaultPrompts").entrySet()) {
                defaultPrompts.put(e.getKey(), e.getValue().getAsString());
            }
        }

        return new CosyVoice3Manifest(
                header,
                root.get("hiddenDim").getAsInt(),
                root.get("speechVocabSize").getAsInt(),
                root.get("sosId").getAsInt(),
                root.get("eosId").getAsInt(),
                root.get("taskId").getAsInt(),
                root.get("numLlmLayers").getAsInt(),
                root.get("kvHeadDim").getAsInt(),
                root.get("tokenMelRatio").getAsInt(),
                root.get("flowSteps").getAsInt(),
                root.get("melBins").getAsInt(),
                root.get("hiftNFft").getAsInt(),
                root.get("hiftHopLength").getAsInt(),
                root.get("speakerEmbedDim").getAsInt(),
                root.get("tokenizerDir").getAsString(),
                Map.copyOf(defaultPrompts)
        );
    }
}
