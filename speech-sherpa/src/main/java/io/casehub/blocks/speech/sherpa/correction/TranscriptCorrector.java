package io.casehub.blocks.speech.sherpa.correction;

import io.casehub.blocks.speech.CorrectionStrategy;
import io.casehub.blocks.speech.CorrectionStrategy.Candidate;
import io.casehub.blocks.speech.CorrectionStrategy.CorrectionContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class TranscriptCorrector {


    private static final double            CONTEXT_CHALLENGE_THRESHOLD = 10.0;
    private final List<CorrectionStrategy> strategies;
    private final @Nullable NgramModel ngramModel;
    private final Set<String> dictionary;

    public TranscriptCorrector(List<CorrectionStrategy> strategies,
                               @Nullable NgramModel ngramModel,
                               Set<String> dictionary) {
        this.strategies = List.copyOf(strategies);
        this.ngramModel = ngramModel;
        this.dictionary = ConcurrentHashMap.newKeySet();
        this.dictionary.addAll(dictionary.stream().map(String::toLowerCase).toList());
    }

    public String correct(String text) {
        if (text.isEmpty()) {
            return text;
        }
        String[] words  = text.split("\\s+");
        var      result = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {result.append(' ');}
            String  word  = words[i];
            String  lower = word.toLowerCase().replaceAll("[^a-z']", "");
            if (lower.isEmpty()) { result.append(word); continue; }
            boolean known = dictionary.contains(lower);

            if (known && ngramModel == null) {
                result.append(word);
                continue;
            }

            String prev          = i > 0 ? words[i - 1].toLowerCase() : "";
            String next          = i + 1 < words.length ? words[i + 1].toLowerCase() : "";
            var    ctx           = new CorrectionContext(prev, next, Collections.unmodifiableSet(dictionary));
            var    allCandidates = new ArrayList<Candidate>();
            for (var strategy : strategies) {
                try {
                    allCandidates.addAll(strategy.candidates(lower, ctx));
                } catch (Exception ignored) {
                }
            }
            if (allCandidates.isEmpty()) {
                result.append(word);
                continue;
            }

            if (known && ngramModel != null) {
                double currentScore = ngramModel.score(prev, lower, next);
                String best         = rankAndSelect(allCandidates, prev, next);
                if (best != null && !best.equals(lower)) {
                    double bestScore = ngramModel.score(prev, best, next);
                    if (bestScore > currentScore + CONTEXT_CHALLENGE_THRESHOLD) {
                        result.append(best);
                        continue;
                    }
                }
                result.append(word);
                continue;
            }

            String best = rankAndSelect(allCandidates, prev, next);
            result.append(best != null ? best : word);
        }
        return result.toString();}

    public void addVocabulary(String... words) {
        for (String w : words) {
            dictionary.add(w.toLowerCase());
        }
    }

    private @Nullable String rankAndSelect(List<Candidate> candidates, String prev, String next) {
        if (ngramModel == null) {
            return candidates.stream()
                    .max(Comparator.comparingDouble(Candidate::confidence))
                    .map(Candidate::word).orElse(null);
        }
        return candidates.stream()
                .max(Comparator.comparingDouble(c -> ngramModel.score(prev, c.word(), next)))
                .map(Candidate::word).orElse(null);
    }
}
