package io.casehub.blocks.speech;

import java.util.List;
import java.util.Set;

@FunctionalInterface
public interface CorrectionStrategy {
    List<Candidate> candidates(String word, CorrectionContext context);

    record Candidate(String word, double confidence, String source) {}

    record CorrectionContext(String previousWord, String nextWord, Set<String> conversationVocabulary) {}
}
