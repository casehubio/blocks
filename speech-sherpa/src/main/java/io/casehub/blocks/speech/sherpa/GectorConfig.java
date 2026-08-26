package io.casehub.blocks.speech.sherpa;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record GectorConfig(
        Path modelPath,
        Path spModelPath,
        int maxIterations,
        float keepConfidence,
        float minErrorProb,
        int numThreads,
        String provider,
        List<String> tagVocabulary,
        int keepTagId,
        Map<String, Map<String, String>> verbDictionary
) {

    private static final String KEEP_TAG = "$KEEP";

    public static GectorConfig fromModelDir(Path modelDir) throws IOException {
        Path labelsPath = modelDir.resolve("labels.txt");
        List<String> tags = Collections.unmodifiableList(
                Files.readAllLines(labelsPath).stream()
                        .filter(line -> !line.isBlank())
                        .toList());

        int keepId = tags.indexOf(KEEP_TAG);
        if (keepId < 0) {
            throw new IOException("labels.txt missing " + KEEP_TAG + " tag");
        }

        Path verbPath = modelDir.resolve("verb-form-vocab.txt");
        Map<String, Map<String, String>> verbDict = Files.exists(verbPath)
                ? parseVerbDictionary(verbPath) : Map.of();

        return new GectorConfig(
                modelDir.resolve("model.onnx"),
                modelDir.resolve("spiece.model"),
                5,
                0.0f,
                0.0f,
                1,
                "cpu",
                tags,
                keepId,
                verbDict
        );
    }

    private static Map<String, Map<String, String>> parseVerbDictionary(Path path) throws IOException {
        var dict = new HashMap<String, Map<String, String>>();
        for (String line : Files.readAllLines(path)) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\t");
            if (parts.length >= 3) {
                dict.computeIfAbsent(parts[0], k -> new HashMap<>())
                        .put(parts[1], parts[2]);
            }
        }
        return Collections.unmodifiableMap(dict);
    }
}
