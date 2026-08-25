package io.casehub.blocks.agentic.social.narrative;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class NarrativeStateSchema {

    static final String SCOPE_ID = "scope_id";
    static final String SCOPE = "scope";
    static final String SYNTHESISED_AT = "synthesised_at";
    static final String REFLECTION_COUNT = "reflection_count";
    static final String FRAGMENTS_JSON = "fragments_json";

    private NarrativeStateSchema() {}

    static Map<String, FeatureValue> toFeatures(NarrativeState state) {
        var features = new LinkedHashMap<String, FeatureValue>();
        features.put(SCOPE_ID, FeatureValue.string(state.scopeId()));
        features.put(SCOPE, FeatureValue.string(state.scope().name()));
        features.put(SYNTHESISED_AT, FeatureValue.string(state.synthesisedAt().toString()));
        features.put(REFLECTION_COUNT, FeatureValue.number(state.reflectionCountAtSynthesis()));
        features.put(FRAGMENTS_JSON, FeatureValue.string(serializeFragments(state.fragments())));
        return Map.copyOf(features);
    }

    static String toSummary(NarrativeState state) {
        return "Narrative for " + state.scopeId()
                + " [" + state.scope()
                + ", episodes=" + state.episodes().size()
                + ", themes=" + state.themes().size() + "]";
    }

    static NarrativeState fromCase(ScoredCbrCase<CbrCase> scored, String scopeId, String tenantId) {
        var features = scored.cbrCase().features();
        return new NarrativeState(
                scopeId,
                tenantId,
                parseScope(stringVal(features, SCOPE, "INDIVIDUAL")),
                deserializeFragments(stringVal(features, FRAGMENTS_JSON, "[]")),
                parseInstant(stringVal(features, SYNTHESISED_AT, null),
                        scored.storedAt() != null ? scored.storedAt() : Instant.EPOCH),
                (int) numberVal(features, REFLECTION_COUNT, 0));
    }

    // --- Fragment serialization ---

    static String serializeFragments(List<NarrativeFragment> fragments) {
        if (fragments.isEmpty()) return "[]";
        var sb = new StringBuilder("[");
        for (int i = 0; i < fragments.size(); i++) {
            if (i > 0) sb.append(",");
            switch (fragments.get(i)) {
                case IndividualEpisode e -> serializeEpisode(sb, e);
                case GroupEpisode g -> serializeGroupEpisode(sb, g);
                case DerivedTheme t -> serializeTheme(sb, t);
            }
        }
        sb.append("]");
        return sb.toString();
    }

    static List<NarrativeFragment> deserializeFragments(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.strip())) {
            return List.of();
        }
        var result = new ArrayList<NarrativeFragment>();
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
                    var fragment = parseFragment(obj);
                    if (fragment != null) result.add(fragment);
                    start = -1;
                }
            }
        }
        return List.copyOf(result);
    }

    // --- Private serialization helpers ---

    private static void serializeEpisode(StringBuilder sb, IndividualEpisode e) {
        sb.append("{\"type\":\"episode\"");
        appendCommonFields(sb, e);
        sb.append(",\"description\":\"").append(escapeJson(e.description())).append("\"");
        sb.append(",\"emotionalValence\":").append(e.emotionalValence());
        appendStringList(sb, "sourceReflectionIds", e.sourceReflectionIds());
        sb.append("}");
    }

    private static void serializeGroupEpisode(StringBuilder sb, GroupEpisode g) {
        sb.append("{\"type\":\"group_episode\"");
        appendCommonFields(sb, g);
        sb.append(",\"description\":\"").append(escapeJson(g.description())).append("\"");
        sb.append(",\"emotionalValence\":").append(g.emotionalValence());
        appendStringSet(sb, "membership", g.membershipAtTime());
        appendStringMap(sb, "roles", g.roleAttributions());
        sb.append(",\"consensusLevel\":").append(g.consensusLevel());
        sb.append("}");
    }

    private static void serializeTheme(StringBuilder sb, DerivedTheme t) {
        sb.append("{\"type\":\"theme\"");
        appendCommonFields(sb, t);
        sb.append(",\"label\":\"").append(escapeJson(t.label())).append("\"");
        sb.append(",\"salience\":").append(t.salience());
        appendAxisWeights(sb, t.axisModulationWeights());
        appendStringList(sb, "supportingIds", t.supportingFragmentIds());
        sb.append("}");
    }

    private static void appendCommonFields(StringBuilder sb, NarrativeFragment f) {
        sb.append(",\"id\":\"").append(escapeJson(f.id())).append("\"");
        sb.append(",\"from\":\"").append(f.from()).append("\"");
        if (f.to() != null) {
            sb.append(",\"to\":\"").append(f.to()).append("\"");
        }
        appendStringList(sb, "tags", f.thematicTags());
    }

    private static void appendStringList(StringBuilder sb, String field, List<String> items) {
        sb.append(",\"").append(field).append("\":[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escapeJson(items.get(i))).append("\"");
        }
        sb.append("]");
    }

    private static void appendStringSet(StringBuilder sb, String field, Set<String> items) {
        sb.append(",\"").append(field).append("\":[");
        var iter = items.iterator();
        boolean first = true;
        while (iter.hasNext()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escapeJson(iter.next())).append("\"");
            first = false;
        }
        sb.append("]");
    }

    private static void appendStringMap(StringBuilder sb, String field, Map<String, String> map) {
        sb.append(",\"").append(field).append("\":{");
        var iter = map.entrySet().iterator();
        boolean first = true;
        while (iter.hasNext()) {
            if (!first) sb.append(",");
            var entry = iter.next();
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":\"")
                    .append(escapeJson(entry.getValue())).append("\"");
            first = false;
        }
        sb.append("}");
    }

    private static void appendAxisWeights(StringBuilder sb, Map<DriveAxis, Double> weights) {
        sb.append(",\"weights\":{");
        var iter = weights.entrySet().iterator();
        boolean first = true;
        while (iter.hasNext()) {
            if (!first) sb.append(",");
            var entry = iter.next();
            sb.append("\"").append(entry.getKey().name()).append("\":").append(entry.getValue());
            first = false;
        }
        sb.append("}");
    }

    // --- Private deserialization helpers ---

    private static @Nullable NarrativeFragment parseFragment(String obj) {
        try {
            var type = extractField(obj, "type");
            if (type == null) return null;
            return switch (type) {
                case "episode" -> parseEpisode(obj);
                case "group_episode" -> parseGroupEpisode(obj);
                case "theme" -> parseTheme(obj);
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    private static IndividualEpisode parseEpisode(String obj) {
        return new IndividualEpisode(
                extractField(obj, "id"),
                Instant.parse(extractField(obj, "from")),
                parseNullableInstant(extractField(obj, "to")),
                extractStringList(obj, "tags"),
                extractField(obj, "description"),
                Double.parseDouble(extractField(obj, "emotionalValence")),
                extractStringList(obj, "sourceReflectionIds"));
    }

    private static GroupEpisode parseGroupEpisode(String obj) {
        return new GroupEpisode(
                extractField(obj, "id"),
                Instant.parse(extractField(obj, "from")),
                parseNullableInstant(extractField(obj, "to")),
                extractStringList(obj, "tags"),
                extractField(obj, "description"),
                Double.parseDouble(extractField(obj, "emotionalValence")),
                new HashSet<>(extractStringList(obj, "membership")),
                extractStringMap(obj, "roles"),
                Double.parseDouble(extractField(obj, "consensusLevel")));
    }

    private static DerivedTheme parseTheme(String obj) {
        return new DerivedTheme(
                extractField(obj, "id"),
                Instant.parse(extractField(obj, "from")),
                parseNullableInstant(extractField(obj, "to")),
                extractStringList(obj, "tags"),
                extractField(obj, "label"),
                Double.parseDouble(extractField(obj, "salience")),
                extractAxisWeights(obj),
                extractStringList(obj, "supportingIds"));
    }

    private static List<String> extractStringList(String json, String field) {
        var needle = "\"" + field + "\":[";
        int idx = json.indexOf(needle);
        if (idx < 0) return List.of();
        int start = idx + needle.length();
        int depth = 1;
        int end = start;
        for (; end < json.length() && depth > 0; end++) {
            if (json.charAt(end) == '[') depth++;
            else if (json.charAt(end) == ']') depth--;
        }
        var content = json.substring(start, end - 1).strip();
        if (content.isEmpty()) return List.of();
        var result = new ArrayList<String>();
        int pos = 0;
        while (pos < content.length()) {
            int qStart = content.indexOf('"', pos);
            if (qStart < 0) break;
            var sb = new StringBuilder();
            for (int i = qStart + 1; i < content.length(); i++) {
                char c = content.charAt(i);
                if (c == '\\' && i + 1 < content.length()) {
                    sb.append(content.charAt(++i));
                } else if (c == '"') {
                    pos = i + 1;
                    break;
                } else {
                    sb.append(c);
                }
            }
            result.add(sb.toString());
        }
        return List.copyOf(result);
    }

    private static Map<String, String> extractStringMap(String json, String field) {
        var needle = "\"" + field + "\":{";
        int idx = json.indexOf(needle);
        if (idx < 0) return Map.of();
        int start = idx + needle.length();
        int depth = 1;
        int end = start;
        for (; end < json.length() && depth > 0; end++) {
            if (json.charAt(end) == '{') depth++;
            else if (json.charAt(end) == '}') depth--;
        }
        var content = json.substring(start, end - 1).strip();
        if (content.isEmpty()) return Map.of();
        var result = new LinkedHashMap<String, String>();
        int pos = 0;
        while (pos < content.length()) {
            int keyStart = content.indexOf('"', pos);
            if (keyStart < 0) break;
            int keyEnd = content.indexOf('"', keyStart + 1);
            if (keyEnd < 0) break;
            var key = content.substring(keyStart + 1, keyEnd);
            int valStart = content.indexOf('"', keyEnd + 1);
            if (valStart < 0) break;
            var sb = new StringBuilder();
            int i = valStart + 1;
            for (; i < content.length(); i++) {
                char c = content.charAt(i);
                if (c == '\\' && i + 1 < content.length()) {
                    sb.append(content.charAt(++i));
                } else if (c == '"') {
                    break;
                } else {
                    sb.append(c);
                }
            }
            result.put(key, sb.toString());
            pos = i + 1;
        }
        return Map.copyOf(result);
    }

    private static Map<DriveAxis, Double> extractAxisWeights(String json) {
        var needle = "\"weights\":{";
        int idx = json.indexOf(needle);
        if (idx < 0) return Map.of();
        int start = idx + needle.length();
        int depth = 1;
        int end = start;
        for (; end < json.length() && depth > 0; end++) {
            if (json.charAt(end) == '{') depth++;
            else if (json.charAt(end) == '}') depth--;
        }
        var content = json.substring(start, end - 1).strip();
        if (content.isEmpty()) return Map.of();
        var result = new EnumMap<DriveAxis, Double>(DriveAxis.class);
        int pos = 0;
        while (pos < content.length()) {
            int keyStart = content.indexOf('"', pos);
            if (keyStart < 0) break;
            int keyEnd = content.indexOf('"', keyStart + 1);
            if (keyEnd < 0) break;
            var key = content.substring(keyStart + 1, keyEnd);
            int colonIdx = content.indexOf(':', keyEnd);
            if (colonIdx < 0) break;
            int valEnd = colonIdx + 1;
            while (valEnd < content.length() && content.charAt(valEnd) != ',' && content.charAt(valEnd) != '}') {
                valEnd++;
            }
            var valStr = content.substring(colonIdx + 1, valEnd).strip();
            result.put(DriveAxis.valueOf(key), Double.parseDouble(valStr));
            pos = valEnd + 1;
        }
        return Map.copyOf(result);
    }

    static @Nullable String extractField(String json, String field) {
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
        } else if (json.charAt(valStart) == '[' || json.charAt(valStart) == '{') {
            return null;
        } else {
            int end = valStart;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
            return json.substring(valStart, end).strip();
        }
    }

    private static @Nullable Instant parseNullableInstant(@Nullable String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static Instant parseInstant(@Nullable String s, Instant defaultVal) {
        if (s == null || s.isBlank()) return defaultVal;
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            return defaultVal;
        }
    }

    private static NarrativeScope parseScope(String s) {
        try {
            return NarrativeScope.valueOf(s);
        } catch (Exception e) {
            return NarrativeScope.INDIVIDUAL;
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

    private static double numberVal(Map<String, FeatureValue> features, String key, double defaultVal) {
        var val = features.get(key);
        if (val instanceof FeatureValue.NumberVal nv) {
            return nv.value();
        }
        return defaultVal;
    }
}
