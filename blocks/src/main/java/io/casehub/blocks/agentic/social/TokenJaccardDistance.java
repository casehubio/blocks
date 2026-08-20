package io.casehub.blocks.agentic.social;

import java.util.HashSet;
import java.util.Set;

final class TokenJaccardDistance {

    private TokenJaccardDistance() {}

    static double distance(String a, String b) {
        Set<String> tokensA = tokenize(a);
        Set<String> tokensB = tokenize(b);
        if (tokensA.isEmpty() && tokensB.isEmpty()) {
            return 0.0;
        }
        if (tokensA.isEmpty() || tokensB.isEmpty()) {
            return 1.0;
        }
        Set<String> intersection = new HashSet<>(tokensA);
        intersection.retainAll(tokensB);
        Set<String> union = new HashSet<>(tokensA);
        union.addAll(tokensB);
        return 1.0 - ((double) intersection.size() / union.size());
    }

    private static Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Set<String> tokens = new HashSet<>();
        for (String token : text.toLowerCase().split("\\s+")) {
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens;
    }
}
