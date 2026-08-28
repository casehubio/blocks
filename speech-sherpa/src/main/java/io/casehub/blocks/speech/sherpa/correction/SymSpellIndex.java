package io.casehub.blocks.speech.sherpa.correction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class SymSpellIndex {

    private static final int MAX_EDIT_DISTANCE = 2;

    private final Map<String, long[]> words = new HashMap<>();
    private final Map<String, Set<String>> deletes = new HashMap<>();

    public record SuggestItem(String word, int distance, long frequency) {}

    public List<SuggestItem> lookup(String input) {
        String lower = input.toLowerCase();
        var result = new ArrayList<SuggestItem>();

        long[] freq = words.get(lower);
        if (freq != null) {
            result.add(new SuggestItem(lower, 0, freq[0]));
        }

        Set<String> inputDeletes = edits(lower, MAX_EDIT_DISTANCE);
        for (String delete : inputDeletes) {
            Set<String> candidates = deletes.get(delete);
            if (candidates == null) continue;
            for (String candidate : candidates) {
                if (candidate.equals(lower)) continue;
                int dist = editDistance(lower, candidate, MAX_EDIT_DISTANCE);
                if (dist >= 0 && dist <= MAX_EDIT_DISTANCE) {
                    long[] cFreq = words.get(candidate);
                    if (cFreq != null) {
                        result.add(new SuggestItem(candidate, dist, cFreq[0]));
                    }
                }
            }
        }

        Set<String> directDeletes = deletes.get(lower);
        if (directDeletes != null) {
            for (String candidate : directDeletes) {
                if (candidate.equals(lower)) continue;
                if (result.stream().anyMatch(s -> s.word().equals(candidate))) continue;
                int dist = editDistance(lower, candidate, MAX_EDIT_DISTANCE);
                if (dist >= 0 && dist <= MAX_EDIT_DISTANCE) {
                    long[] cFreq = words.get(candidate);
                    if (cFreq != null) {
                        result.add(new SuggestItem(candidate, dist, cFreq[0]));
                    }
                }
            }
        }

        var seen = new HashSet<String>();
        var deduped = new ArrayList<SuggestItem>();
        for (var item : result) {
            if (seen.add(item.word())) {
                deduped.add(item);
            }
        }

        deduped.sort(Comparator.comparingInt(SuggestItem::distance)
                .thenComparing(Comparator.comparingLong(SuggestItem::frequency).reversed()));
        return Collections.unmodifiableList(deduped);
    }

    public void add(String word, long frequency) {
        String lower = word.toLowerCase();
        words.put(lower, new long[]{frequency});
        Set<String> edits = edits(lower, MAX_EDIT_DISTANCE);
        for (String delete : edits) {
            deletes.computeIfAbsent(delete, k -> new HashSet<>()).add(lower);
        }
    }

    public boolean contains(String word) {
        return words.containsKey(word.toLowerCase());
    }

    public Set<String> dictionary() {
        return Collections.unmodifiableSet(words.keySet());
    }

    public static SymSpellIndex fromResource(String resourceName) {
        var index = new SymSpellIndex();
        try (var stream = SymSpellIndex.class.getClassLoader().getResourceAsStream(resourceName)) {
            Objects.requireNonNull(stream, "Resource not found: " + resourceName);
            var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            reader.lines().forEach(line -> {
                String clean = !line.isEmpty() && line.charAt(0) == '﻿' ? line.substring(1) : line;
                if (clean.isBlank() || clean.startsWith("#")) return;
                String[] parts = clean.split("\\s+");
                if (parts.length >= 2) {
                    try {
                        index.add(parts[0].trim(), Long.parseLong(parts[1].trim()));
                    } catch (NumberFormatException ignored) {}
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return index;
    }

    public static SymSpellIndex fromFile(Path path) {
        var index = new SymSpellIndex();
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            reader.lines().forEach(line -> {
                String clean = !line.isEmpty() && line.charAt(0) == '﻿' ? line.substring(1) : line;
                if (clean.isBlank() || clean.startsWith("#")) return;
                String[] parts = clean.split("\\s+");
                if (parts.length >= 2) {
                    try {
                        index.add(parts[0].trim(), Long.parseLong(parts[1].trim()));
                    } catch (NumberFormatException ignored) {}
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return index;
    }

    private static Set<String> edits(String word, int maxDistance) {
        var result = new HashSet<String>();
        if (maxDistance <= 0 || word.isEmpty()) return result;
        edits(word, maxDistance, result);
        return result;
    }

    private static void edits(String word, int distance, Set<String> result) {
        for (int i = 0; i < word.length(); i++) {
            String delete = word.substring(0, i) + word.substring(i + 1);
            if (result.add(delete) && distance > 1) {
                edits(delete, distance - 1, result);
            }
        }
    }

    static int editDistance(String s, String t, int maxDistance) {
        int sLen = s.length();
        int tLen = t.length();
        if (Math.abs(sLen - tLen) > maxDistance) return -1;

        int[] prev = new int[tLen + 1];
        int[] curr = new int[tLen + 1];
        for (int j = 0; j <= tLen; j++) prev[j] = j;

        for (int i = 1; i <= sLen; i++) {
            curr[0] = i;
            int minInRow = curr[0];
            for (int j = 1; j <= tLen; j++) {
                int cost = s.charAt(i - 1) == t.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                minInRow = Math.min(minInRow, curr[j]);
            }
            if (minInRow > maxDistance) return -1;
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[tLen] <= maxDistance ? prev[tLen] : -1;
    }
}
