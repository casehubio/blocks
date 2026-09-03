package io.casehub.blocks.speech.sherpa;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.casehub.blocks.speech.SpeakerEmbedding;
import io.casehub.blocks.speech.VoiceprintStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

public class FileVoiceprintStore implements VoiceprintStore {

    private static final Gson GSON = new GsonBuilder().create();
    private final Path directory;

    public FileVoiceprintStore(Path directory) {
        this.directory = directory;
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create voiceprint directory: " + directory, e);
        }
    }

    @Override
    public void save(String name, SpeakerEmbedding embedding) {
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", name);
            obj.addProperty("dimensions", embedding.dimensions());
            JsonArray vec = new JsonArray();
            for (float v : embedding.vector()) vec.add(v);
            obj.add("vector", vec);
            Path tmp = directory.resolve(name + ".json.tmp");
            Path target = directory.resolve(name + ".json");
            Files.writeString(tmp, GSON.toJson(obj));
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save voiceprint: " + name, e);
        }
    }

    @Override
    public Map<String, SpeakerEmbedding> loadAll() {
        Map<String, SpeakerEmbedding> result = new HashMap<>();
        try (var stream = Files.list(directory)) {
            stream.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                try {
                    JsonObject obj = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
                    String name = obj.get("name").getAsString();
                    int dims = obj.get("dimensions").getAsInt();
                    JsonArray vec = obj.getAsJsonArray("vector");
                    float[] vector = new float[vec.size()];
                    for (int i = 0; i < vec.size(); i++) vector[i] = vec.get(i).getAsFloat();
                    result.put(name, new SpeakerEmbedding(vector, dims));
                } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
        return result;
    }

    @Override
    public void delete(String name) {
        try {
            Files.deleteIfExists(directory.resolve(name + ".json"));
        } catch (IOException ignored) {}
    }
}
