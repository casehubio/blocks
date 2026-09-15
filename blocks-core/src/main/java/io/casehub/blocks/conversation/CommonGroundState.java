package io.casehub.blocks.conversation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record CommonGroundState(
        Map<String, GroundedFact> establishedFacts,
        Map<String, GroundedFact> pendingClaims,
        Map<String, GroundedFact> disputedPoints) {
    public CommonGroundState {
        establishedFacts = Collections.unmodifiableMap(new LinkedHashMap<>(establishedFacts));
        pendingClaims = Collections.unmodifiableMap(new LinkedHashMap<>(pendingClaims));
        disputedPoints = Collections.unmodifiableMap(new LinkedHashMap<>(disputedPoints));
    }
}
