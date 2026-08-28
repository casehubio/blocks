package io.casehub.blocks.speech.sherpa.correction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class NgramModel {

    private final Map<Long, Float>   bigrams  = new HashMap<>();
    private final Map<String, Float> unigrams = new HashMap<>();
    private       float              totalCount;

    NgramModel() {}

    public double score(String prev, String candidate, String next) {
        double s = 0.0;
        if (!prev.isEmpty()) {
            s += bigramLogProb(prev, candidate);
        }
        if (!next.isEmpty()) {
            s += bigramLogProb(candidate, next);
        }
        if (prev.isEmpty() && next.isEmpty()) {
            s = unigramLogProb(candidate);
        }
        return s;
    }

    private double bigramLogProb(String w1, String w2) {
        Float freq = bigrams.get(bigramKey(w1, w2));
        if (freq != null) {
            return Math.log(freq / totalCount);
        }
        return unigramLogProb(w2) - 2.0;
    }

    private double unigramLogProb(String word) {
        Float freq = unigrams.get(word.toLowerCase());
        if (freq != null) {
            return Math.log(freq / totalCount);
        }
        return Math.log(1.0 / (totalCount + 1));
    }

    private static long bigramKey(String w1, String w2) {
        return (long) w1.toLowerCase().hashCode() * 31 + w2.toLowerCase().hashCode();
    }

    private void addBigram(String w1, String w2, float count) {
        String lw1 = w1.toLowerCase();
        String lw2 = w2.toLowerCase();
        bigrams.put(bigramKey(lw1, lw2), count);
        unigrams.merge(lw1, count, Float::sum);
        unigrams.merge(lw2, count, Float::sum);
        totalCount += count;
    }

    public static NgramModel fromResource(String resourceName) {
        var model = new NgramModel();
        try (var stream = NgramModel.class.getClassLoader().getResourceAsStream(resourceName)) {
            Objects.requireNonNull(stream, "Resource not found: " + resourceName);
            var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            reader.lines().forEach(line -> {
                String clean = !line.isEmpty() && line.charAt(0) == '﻿' ? line.substring(1) : line;
                if (clean.isBlank() || clean.startsWith("#")) {return;}
                String[] parts = clean.split("\\s+");
                if (parts.length >= 3) {
                    try {
                        model.addBigram(parts[0].trim(), parts[1].trim(), Float.parseFloat(parts[2].trim()));
                    } catch (NumberFormatException ignored) {}
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return model;
    }

    public static NgramModel fromFile(Path path) {
        var model = new NgramModel();
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            reader.lines().forEach(line -> {
                if (line.isBlank() || line.startsWith("#")) {return;}
                String[] parts = line.split("\\s+");
                if (parts.length >= 3) {
                    model.addBigram(parts[0].trim(), parts[1].trim(), Float.parseFloat(parts[2].trim()));
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return model;
    }
}

