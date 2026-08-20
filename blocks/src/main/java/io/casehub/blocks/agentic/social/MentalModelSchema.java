package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MentalModelSchema {

    static final String SUBJECT_ID = "subject_id";
    static final String BELIEFS_JSON = "beliefs_json";
    static final String DESIRES_JSON = "desires_json";
    static final String INTENTIONS_JSON = "intentions_json";
    static final String LAST_SIGNAL = "last_signal";
    static final String LAST_INFERENCE = "last_inference";

    private MentalModelSchema() {}

    static Map<String, FeatureValue> toFeatures(MentalModelSnapshot snapshot) {
        var features = new LinkedHashMap<String, FeatureValue>();
        features.put(SUBJECT_ID, FeatureValue.string(snapshot.subjectId()));
        features.put(BELIEFS_JSON, FeatureValue.string(serializeStates(snapshot.beliefs())));
        features.put(DESIRES_JSON, FeatureValue.string(serializeStates(snapshot.desires())));
        features.put(INTENTIONS_JSON, FeatureValue.string(serializeStates(snapshot.intentions())));
        features.put(LAST_SIGNAL, FeatureValue.string(snapshot.lastSignal().toString()));
        if (snapshot.lastInference() != null) {
            features.put(LAST_INFERENCE, FeatureValue.string(snapshot.lastInference().toString()));
        }
        return Map.copyOf(features);
    }

    static String toSummary(MentalModelSnapshot snapshot) {
        return "Mental model for " + snapshot.subjectId()
                + " [beliefs=" + snapshot.beliefs().size()
                + ", desires=" + snapshot.desires().size()
                + ", intentions=" + snapshot.intentions().size() + "]";
    }

    static MentalModelSnapshot fromCase(ScoredCbrCase<CbrCase> scored,
                                        String agentId, String tenantId) {
        var features = scored.cbrCase().features();
        var storedAt = scored.storedAt() != null ? scored.storedAt() : Instant.now();

        return new MentalModelSnapshot(
                agentId,
                stringVal(features, SUBJECT_ID, ""),
                tenantId,
                deserializeStates(stringVal(features, BELIEFS_JSON, "[]")),
                deserializeStates(stringVal(features, DESIRES_JSON, "[]")),
                deserializeStates(stringVal(features, INTENTIONS_JSON, "[]")),
                parseInstant(stringVal(features, LAST_SIGNAL, null), storedAt),
                parseInstant(stringVal(features, LAST_INFERENCE, null), null),
                storedAt);
    }

    static String serializeStates(List<AttributedState> states) {
        if (states.isEmpty()) return "[]";
        var sb = new StringBuilder("[");
        for (int i = 0; i < states.size(); i++) {
            if (i > 0) sb.append(",");
            var s = states.get(i);
            sb.append("{\"key\":\"").append(escapeJson(s.key()))
                    .append("\",\"description\":\"").append(escapeJson(s.description()))
                    .append("\",\"confidence\":").append(s.confidence())
                    .append(",\"entrenchment\":").append(s.entrenchment())
                    .append(",\"lastReinforced\":\"").append(s.lastReinforced())
                    .append("\",\"dimension\":\"").append(s.dimension().name())
                    .append("\"}");
        }
        sb.append("]");
        return sb.toString();
    }

    static List<AttributedState> deserializeStates(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.strip())) {
            return List.of();
        }
        var result = new ArrayList<AttributedState>();
        json = json.strip();
        if (json.startsWith("[")) json = json.substring(1);
        if (json.endsWith("]")) json = json.substring(0, json.length() - 1);

        int depth = 0;
        int start = -1;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    var obj = json.substring(start, i + 1);
                    var state = parseStateObject(obj);
                    if (state != null) result.add(state);
                    start = -1;
                }
            }
        }
        return List.copyOf(result);
    }

    private static @Nullable AttributedState parseStateObject(String obj) {
        try {
            var key = extractField(obj, "key");
            var description = extractField(obj, "description");
            var confidenceStr = extractField(obj, "confidence");
            var entrenchmentStr = extractField(obj, "entrenchment");
            var lastReinforcedStr = extractField(obj, "lastReinforced");
            var dimensionStr = extractField(obj, "dimension");

            if (key == null || description == null || confidenceStr == null
                    || entrenchmentStr == null || lastReinforcedStr == null || dimensionStr == null) {
                return null;
            }
            return new AttributedState(key, description,
                    Double.parseDouble(confidenceStr),
                    Integer.parseInt(entrenchmentStr),
                    Instant.parse(lastReinforcedStr),
                    BdiDimension.valueOf(dimensionStr));
        } catch (Exception e) {
            return null;
        }
    }

    private static @Nullable String extractField(String json, String field) {
        var needle = "\"" + field + "\":";
        int idx = json.indexOf(needle);
        if (idx < 0) return null;
        int valStart = idx + needle.length();
        while (valStart < json.length() && json.charAt(valStart) == ' ') valStart++;
        if (valStart >= json.length()) return null;

        if (json.charAt(valStart) == '"') {
            valStart++;
            var sb = new StringBuilder();
            for (int i = valStart; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '\\' && i + 1 < json.length()) {
                    sb.append(json.charAt(++i));
                } else if (c == '"') {
                    break;
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        } else {
            int end = valStart;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
            return json.substring(valStart, end).strip();
        }
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String stringVal(Map<String, FeatureValue> features, String key,
                                    @Nullable String defaultVal) {
        var val = features.get(key);
        if (val instanceof FeatureValue.StringVal sv) {
            return sv.value();
        }
        return defaultVal;
    }

    private static @Nullable Instant parseInstant(@Nullable String s, @Nullable Instant defaultVal) {
        if (s == null || s.isBlank()) return defaultVal;
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            return defaultVal;
        }
    }
}
