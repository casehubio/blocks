package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

final class UserProfileSchema {

    static final String SUBJECT_ID = "subject_id";
    static final String RELATIONSHIP_STAGE = "relationship_stage";
    static final String FAMILIARITY_SCORE = "familiarity_score";
    static final String TOTAL_INTERACTIONS = "total_interactions";
    static final String POSITIVE_SIGNALS = "positive_signals";
    static final String NEGATIVE_SIGNALS = "negative_signals";
    static final String NEUTRAL_SIGNALS = "neutral_signals";
    static final String COMMUNICATION_STYLE = "communication_style";
    static final String TOPICS_OF_INTEREST = "topics_of_interest";
    static final String PREFERENCES = "preferences";

    private UserProfileSchema() {}

    static Map<String, FeatureValue> toFeatures(UserProfile profile) {
        var features = new LinkedHashMap<String, FeatureValue>();
        features.put(SUBJECT_ID, FeatureValue.string(profile.subjectId()));
        features.put(RELATIONSHIP_STAGE, FeatureValue.string(profile.relationshipStage()));
        features.put(FAMILIARITY_SCORE, FeatureValue.number(profile.familiarityScore()));
        features.put(TOTAL_INTERACTIONS, FeatureValue.number(profile.totalInteractions()));
        features.put(POSITIVE_SIGNALS, FeatureValue.number(profile.positiveSignals()));
        features.put(NEGATIVE_SIGNALS, FeatureValue.number(profile.negativeSignals()));
        features.put(NEUTRAL_SIGNALS, FeatureValue.number(profile.neutralSignals()));
        if (profile.communicationStyle() != null) {
            features.put(COMMUNICATION_STYLE, FeatureValue.string(profile.communicationStyle()));
        }
        if (profile.topicsOfInterest() != null) {
            features.put(TOPICS_OF_INTEREST, FeatureValue.string(profile.topicsOfInterest()));
        }
        if (profile.preferences() != null) {
            features.put(PREFERENCES, FeatureValue.string(profile.preferences()));
        }
        return Map.copyOf(features);
    }

    static String toSummary(UserProfile profile) {
        return "Profile for " + profile.subjectId()
                + " [" + profile.relationshipStage()
                + ", familiarity=" + String.format("%.2f", profile.familiarityScore())
                + ", interactions=" + profile.totalInteractions() + "]";
    }

    static UserProfile fromCase(ScoredCbrCase<CbrCase> scored, String agentId, String tenantId) {
        var features = scored.cbrCase().features();
        var storedAt = scored.storedAt() != null ? scored.storedAt() : Instant.now();

        return new UserProfile(
                agentId,
                stringVal(features, SUBJECT_ID, ""),
                tenantId,
                stringVal(features, RELATIONSHIP_STAGE, "stranger"),
                numberVal(features, FAMILIARITY_SCORE, 0.0),
                (int) numberVal(features, TOTAL_INTERACTIONS, 0),
                (int) numberVal(features, POSITIVE_SIGNALS, 0),
                (int) numberVal(features, NEGATIVE_SIGNALS, 0),
                (int) numberVal(features, NEUTRAL_SIGNALS, 0),
                storedAt,
                storedAt,
                null,
                stringVal(features, COMMUNICATION_STYLE, null),
                stringVal(features, TOPICS_OF_INTEREST, null),
                stringVal(features, PREFERENCES, null),
                null,
                Map.of());
    }

    private static String stringVal(Map<String, FeatureValue> features, String key, String defaultVal) {
        var val = features.get(key);
        if (val instanceof FeatureValue.StringVal sv) {
            return sv.value();
        }
        return defaultVal;
    }

    private static double numberVal(Map<String, FeatureValue> features, String key, double defaultVal) {
        var val = features.get(key);
        if (val instanceof FeatureValue.NumberVal nv) {
            return nv.value();
        }
        return defaultVal;
    }
}
