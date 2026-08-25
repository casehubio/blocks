package io.casehub.blocks.agentic.social.emergence;

import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.FeatureVectorCbrCase;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class NormObservationSchema {

    static final String BEHAVIORAL_PATTERN = "behavioral_pattern";
    static final String CONVERSATION_ID = "conversation_id";
    static final String PATTERN_FOLLOWED = "pattern_followed";
    static final String OBSERVED_AT = "observed_at";
    static final String INVOLVED_AGENTS = "involved_agents";

    private NormObservationSchema() {}

    static Map<String, FeatureValue> toFeatures(NormObservation observation) {
        var features = new LinkedHashMap<String, FeatureValue>();
        features.put(BEHAVIORAL_PATTERN, FeatureValue.string(observation.behavioralPattern()));
        features.put(CONVERSATION_ID, FeatureValue.string(observation.conversationId()));
        features.put(PATTERN_FOLLOWED, FeatureValue.number(observation.patternFollowed() ? 1.0 : 0.0));
        features.put(OBSERVED_AT, FeatureValue.string(observation.observedAt().toString()));
        features.put(INVOLVED_AGENTS, FeatureValue.string(String.join(",", observation.involvedAgents())));
        return Map.copyOf(features);
    }

    static String toSummary(NormObservation observation) {
        return (observation.patternFollowed() ? "Followed" : "Violated")
                + ": " + observation.behavioralPattern();
    }

    static CbrCase toCbrCase(NormObservation observation) {
        return new FeatureVectorCbrCase(
                toSummary(observation), "-", null, null,
                toFeatures(observation), null, observation.observationId());
    }

    static NormObservation fromCase(ScoredCbrCase<CbrCase> scored, String tenantId) {
        var features = scored.cbrCase().features();
        var agents = stringVal(features, INVOLVED_AGENTS, "");
        Set<String> agentSet = new HashSet<>();
        if (!agents.isEmpty()) {
            for (var a : agents.split(",")) {
                if (!a.isBlank()) agentSet.add(a.strip());
            }
        }
        return new NormObservation(
                scored.cbrCase().producerAgentId() != null ? scored.cbrCase().producerAgentId() : scored.caseId(),
                tenantId,
                stringVal(features, BEHAVIORAL_PATTERN, ""),
                agentSet,
                stringVal(features, CONVERSATION_ID, ""),
                parseInstant(stringVal(features, OBSERVED_AT, null),
                        scored.storedAt() != null ? scored.storedAt() : Instant.EPOCH),
                numberVal(features, PATTERN_FOLLOWED, 0.0) >= 0.5);
    }

    private static String stringVal(Map<String, FeatureValue> features, String key,
                                    @Nullable String defaultVal) {
        var val = features.get(key);
        if (val instanceof FeatureValue.StringVal sv) return sv.value();
        return defaultVal;
    }

    private static double numberVal(Map<String, FeatureValue> features, String key, double defaultVal) {
        var val = features.get(key);
        if (val instanceof FeatureValue.NumberVal nv) return nv.value();
        return defaultVal;
    }

    private static Instant parseInstant(@Nullable String s, Instant defaultVal) {
        if (s == null || s.isBlank()) return defaultVal;
        try { return Instant.parse(s); } catch (Exception e) { return defaultVal; }
    }
}
