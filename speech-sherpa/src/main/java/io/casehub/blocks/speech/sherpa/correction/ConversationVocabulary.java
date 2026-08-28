package io.casehub.blocks.speech.sherpa.correction;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ConversationVocabulary {

    private static final int MIN_TERM_LENGTH = 5;

    private static final Set<String> STOP_WORDS = Set.of(
            "there", "their", "these", "those", "which", "where", "would", "could",
            "should", "about", "after", "before", "being", "between", "under", "until",
            "while", "since", "during", "through", "against", "above", "below");

    private final Set<String> terms = ConcurrentHashMap.newKeySet();

    public void addFromText(String text) {
        if (text == null || text.isBlank()) return;
        String[] words = text.split("\\s+");
        for (String word : words) {
            String lower = word.toLowerCase().replaceAll("[^a-z']", "");
            if (lower.length() >= MIN_TERM_LENGTH && !STOP_WORDS.contains(lower)) {
                terms.add(lower);
            }
        }
    }

    public String asPromptHint() {
        if (terms.isEmpty()) return "";
        return String.join(" ", terms);
    }

    public Set<String> terms() {
        return Collections.unmodifiableSet(terms);
    }
}
