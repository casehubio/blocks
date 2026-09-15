package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class StrategyProfileSchema {

    static final String AGENT_ID = "agent_id";
    static final String VERBOSITY = "verbosity";
    static final String FORMALITY = "formality";
    static final String INITIATIVE = "initiative";
    static final String DIRECTNESS = "directness";
    static final String QUESTION_RATE = "questionRate";
    static final String EVIDENCE_COUNT = "evidence_count";
    static final String GUIDELINES_PREFIX = "guidelines: ";
    static final String GUIDELINES_SEPARATOR = "\n";
    static final List<String> DIMENSION_KEYS = List.of(
            VERBOSITY, FORMALITY, INITIATIVE, DIRECTNESS, QUESTION_RATE);
    private static final List<String> NON_DIMENSION_KEYS = List.of(
            AGENT_ID, EVIDENCE_COUNT);

    private StrategyProfileSchema() {}

    static Map<String, FeatureValue> toFeatures(StrategyProfile profile) {
        var features = new LinkedHashMap<String, FeatureValue>();
        features.put(AGENT_ID, FeatureValue.string(profile.agentId()));
        for (var entry : profile.dimensions().entrySet()) {
            features.put(entry.getKey(), FeatureValue.number(entry.getValue()));
        }
        features.put(EVIDENCE_COUNT, FeatureValue.number(profile.evidenceCount()));
        return Map.copyOf(features);
    }

    static String toSummary(StrategyProfile profile) {
        if (profile.guidelines().isEmpty()) {
            return "Strategy profile for " + profile.agentId() + " [no guidelines]";
        }
        return GUIDELINES_PREFIX + String.join(GUIDELINES_SEPARATOR, profile.guidelines());
    }

    static StrategyProfile fromCase(ScoredCbrCase<CbrCase> scored,
                                     String agentId, String tenantId) {
        var features = scored.cbrCase().features();
        var storedAt = scored.storedAt() != null ? scored.storedAt() : Instant.now();

        var dimensions = new LinkedHashMap<String, Double>();
        for (String dim : DIMENSION_KEYS) {
            dimensions.put(dim, numberVal(features, dim, 0.5));
        }
        for (var entry : features.entrySet()) {
            if (!dimensions.containsKey(entry.getKey())
                    && entry.getValue() instanceof FeatureValue.NumberVal nv
                    && !NON_DIMENSION_KEYS.contains(entry.getKey())) {
                dimensions.put(entry.getKey(), nv.value());
            }
        }

        var guidelines = new ArrayList<String>();
        String problem = scored.cbrCase().problem();
        if (problem != null && problem.startsWith(GUIDELINES_PREFIX)) {
            String raw = problem.substring(GUIDELINES_PREFIX.length());
            for (String line : raw.split(GUIDELINES_SEPARATOR)) {
                if (!line.isBlank()) guidelines.add(line.trim());
            }
        }

        return new StrategyProfile(agentId, tenantId, Map.copyOf(dimensions),
                List.copyOf(guidelines), storedAt,
                (int) numberVal(features, EVIDENCE_COUNT, 0));
    }

    private static double numberVal(Map<String, FeatureValue> features,
                                     String key, double defaultVal) {
        var val = features.get(key);
        if (val instanceof FeatureValue.NumberVal nv) return nv.value();
        return defaultVal;
    }
}
