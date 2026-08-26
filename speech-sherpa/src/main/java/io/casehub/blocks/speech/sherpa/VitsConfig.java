package io.casehub.blocks.speech.sherpa;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class VitsConfig {

    private static final System.Logger LOG = System.getLogger("casehub-speech");

    private final Path modelPath;
    private final int sampleRate;
    private final String espeakVoice;
    private final float noiseScale;
    private final float lengthScale;
    private final float noiseScaleW;
    private final int numThreads;
    private final String provider;
    private final Map<String, List<Integer>> phonemeIdMap;
    private final Map<Integer, String> reverseMap;
    private final List<String> sortedKeys;

    private VitsConfig(Path modelPath, int sampleRate, String espeakVoice,
                       float noiseScale, float lengthScale, float noiseScaleW,
                       int numThreads, String provider,
                       Map<String, List<Integer>> phonemeIdMap) {
        this.modelPath = Objects.requireNonNull(modelPath);
        this.sampleRate = sampleRate;
        this.espeakVoice = Objects.requireNonNull(espeakVoice);
        this.noiseScale = noiseScale;
        this.lengthScale = lengthScale;
        this.noiseScaleW = noiseScaleW;
        this.numThreads = numThreads;
        this.provider = Objects.requireNonNull(provider);
        this.phonemeIdMap = Map.copyOf(phonemeIdMap);

        this.reverseMap = new HashMap<>();
        for (var entry : phonemeIdMap.entrySet()) {
            List<Integer> ids = entry.getValue();
            if (!ids.isEmpty()) {
                reverseMap.put(ids.getFirst(), entry.getKey());
            }
        }

        this.sortedKeys = phonemeIdMap.keySet().stream()
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .toList();
    }

    public static VitsConfig fromModelDir(Path modelDir) {
        return fromModelDir(modelDir, 1, "cpu");
    }

    public static VitsConfig fromModelDir(Path modelDir, int numThreads, String provider) {
        Path configJson = findConfigJson(modelDir);
        Path modelOnnx = findOnnxModel(modelDir);

        Gson gson = new Gson();
        JsonObject root;
        try (Reader reader = Files.newBufferedReader(configJson)) {
            root = gson.fromJson(reader, JsonObject.class);
        } catch (IOException e) {
            throw new SherpaException("Failed to read model config: " + configJson, e);
        }

        int sampleRate = root.getAsJsonObject("audio").get("sample_rate").getAsInt();
        String espeakVoice = root.getAsJsonObject("espeak").get("voice").getAsString();

        JsonObject inference = root.getAsJsonObject("inference");
        float noiseScale = inference.get("noise_scale").getAsFloat();
        float lengthScale = inference.get("length_scale").getAsFloat();
        float noiseScaleW = inference.get("noise_w").getAsFloat();

        Map<String, List<Integer>> phonemeIdMap = gson.fromJson(
                root.get("phoneme_id_map"),
                new TypeToken<Map<String, List<Integer>>>() {}.getType());

        return new VitsConfig(modelOnnx, sampleRate, espeakVoice,
                noiseScale, lengthScale, noiseScaleW,
                numThreads, provider, phonemeIdMap);
    }

    public List<Integer> tokenize(String ipa) {
        List<Integer> raw = new ArrayList<>();

        List<Integer> bosIds = phonemeIdMap.getOrDefault("^", List.of());
        List<Integer> eosIds = phonemeIdMap.getOrDefault("$", List.of());

        raw.addAll(bosIds);

        int pos = 0;
        while (pos < ipa.length()) {
            boolean matched = false;
            for (String key : sortedKeys) {
                if (key.equals("^") || key.equals("$") || key.equals("_")) {
                    continue;
                }
                if (ipa.startsWith(key, pos)) {
                    raw.addAll(phonemeIdMap.get(key));
                    pos += key.length();
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                LOG.log(System.Logger.Level.WARNING,
                        "Unknown IPA character at position {0}: ''{1}''",
                        pos, ipa.charAt(pos));
                pos++;
            }
        }

        raw.addAll(eosIds);

        return intersperse(raw, padId());
    }

    public List<Integer> phonemeToIds(String phoneme) {
        return phonemeIdMap.getOrDefault(phoneme, List.of());
    }

    public String idToPhoneme(int id) {
        return reverseMap.get(id);
    }

    public Path modelPath() { return modelPath; }
    public int sampleRate() { return sampleRate; }
    public String espeakVoice() { return espeakVoice; }
    public float noiseScale() { return noiseScale; }
    public float lengthScale() { return lengthScale; }
    public float noiseScaleW() { return noiseScaleW; }
    public int numThreads() { return numThreads; }
    public String provider() { return provider; }

    int padId() {
        List<Integer> ids = phonemeIdMap.getOrDefault("_", List.of(0));
        return ids.isEmpty() ? 0 : ids.getFirst();
    }

    int bosId() {
        List<Integer> ids = phonemeIdMap.getOrDefault("^", List.of(1));
        return ids.isEmpty() ? 1 : ids.getFirst();
    }

    int eosId() {
        List<Integer> ids = phonemeIdMap.getOrDefault("$", List.of(2));
        return ids.isEmpty() ? 2 : ids.getFirst();
    }

    private static List<Integer> intersperse(List<Integer> tokens, int padId) {
        List<Integer> result = new ArrayList<>(2 * tokens.size() + 1);
        result.add(padId);
        for (int token : tokens) {
            result.add(token);
            result.add(padId);
        }
        return result;
    }

    private static Path findConfigJson(Path modelDir) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modelDir, "*.onnx.json")) {
            for (Path p : stream) {
                return p;
            }
        } catch (IOException e) {
            throw new SherpaException("Failed to scan model directory: " + modelDir, e);
        }
        throw new SherpaException("No .onnx.json config found in " + modelDir);
    }

    private static Path findOnnxModel(Path modelDir) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modelDir, "*.onnx")) {
            for (Path p : stream) {
                if (!p.getFileName().toString().endsWith(".onnx.json")) {
                    return p;
                }
            }
        } catch (IOException e) {
            throw new SherpaException("Failed to scan model directory: " + modelDir, e);
        }
        throw new SherpaException("No .onnx model found in " + modelDir);
    }
}
