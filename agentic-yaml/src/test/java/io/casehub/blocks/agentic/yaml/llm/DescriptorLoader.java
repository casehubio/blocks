package io.casehub.blocks.agentic.yaml.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.eidos.api.AgentCapability;
import io.casehub.eidos.api.AgentConstraint;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentDisposition;
import io.casehub.eidos.api.AgentGoal;
import io.casehub.eidos.api.ConstraintSeverity;
import io.casehub.eidos.api.DispositionValue;
import io.casehub.eidos.api.GoalPriority;
import io.casehub.eidos.api.Visibility;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DescriptorLoader {

    @SuppressWarnings("unchecked")
    public static List<AgentDescriptor> load(String scenario) {
        String path = "/examples/" + scenario + "/descriptors.yaml";
        try (InputStream is = DescriptorLoader.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalArgumentException("Descriptors not found: " + path);
            }
            var mapper = new ObjectMapper(new YAMLFactory());
            List<Map<String, Object>> rawList = mapper.readValue(is, List.class);
            var result = new ArrayList<AgentDescriptor>();
            for (var raw : rawList) {
                result.add(buildDescriptor(raw));
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + path, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static AgentDescriptor buildDescriptor(Map<String, Object> raw) {
        var name = (String) raw.get("name");
        var briefing = raw.get("briefing") != null ? raw.get("briefing").toString().strip() : "";
        var slot = (String) raw.getOrDefault("slot", null);
        var slotVocab = (String) raw.getOrDefault("slotVocabulary", null);
        var dispVocab = (String) raw.getOrDefault("dispositionVocabulary", null);

        var capabilities = new ArrayList<AgentCapability>();
        if (raw.get("capabilities") instanceof List<?> capList) {
            for (var capRaw : capList) {
                if (capRaw instanceof Map<?, ?> capMap) {
                    var capName = (String) capMap.get("name");
                    var tags = capMap.get("tags") instanceof List<?> t
                            ? t.stream().map(Object::toString).toList() : List.<String>of();
                    capabilities.add(new AgentCapability(capName, null, null, null,
                            null, null, null, Set.of(), List.of(), List.of(), tags,
                            Map.of(), Set.of()));
                }
            }
        }

        AgentDisposition disposition = null;
        if (raw.get("disposition") instanceof Map<?, ?> dispMap) {
            disposition = AgentDisposition.builder()
                    .socialOrient(dispValue(dispMap, "socialOrient"))
                    .ruleFollowing(dispValue(dispMap, "ruleFollowing"))
                    .riskAppetite(dispValue(dispMap, "riskAppetite"))
                    .autonomy(dispValue(dispMap, "autonomy"))
                    .conflictMode(dispValue(dispMap, "conflictMode"))
                    .delegation(Boolean.TRUE.equals(dispMap.get("delegation")))
                    .build();
        }

        var goals = new ArrayList<AgentGoal>();
        if (raw.get("goals") instanceof List<?> goalList) {
            for (var goalRaw : goalList) {
                if (goalRaw instanceof Map<?, ?>) {
                    @SuppressWarnings("unchecked") var goalMap = (Map<String, Object>) goalRaw;
                    goals.add(new AgentGoal(
                            (String) goalMap.get("name"),
                            (String) goalMap.get("description"),
                            GoalPriority.valueOf(((String) goalMap.getOrDefault("priority", "SECONDARY")).toUpperCase()),
                            Visibility.valueOf(((String) goalMap.getOrDefault("visibility", "PUBLIC")).toUpperCase()),
                            List.of(), Map.of()));
                }
            }
        }

        var constraints = new ArrayList<AgentConstraint>();
        if (raw.get("constraints") instanceof List<?> conList) {
            for (var conRaw : conList) {
                if (conRaw instanceof Map<?, ?>) {
                    @SuppressWarnings("unchecked") var conMap = (Map<String, Object>) conRaw;
                    constraints.add(new AgentConstraint(
                            (String) conMap.get("name"),
                            (String) conMap.get("description"),
                            Visibility.valueOf(((String) conMap.getOrDefault("visibility", "PUBLIC")).toUpperCase()),
                            ConstraintSeverity.valueOf(((String) conMap.getOrDefault("severity", "SOFT")).toUpperCase())));
                }
            }
        }

        return new AgentDescriptor(name, name, null, null, null, null, null,
                null, slotVocab, dispVocab, null, Map.of(), slot,
                capabilities, disposition, null, null, "showcase", briefing,
                List.of(), goals, constraints);
    }

    private static List<DispositionValue> dispValue(Map<?, ?> map, String key) {
        var val = map.get(key);
        return val != null ? List.of(DispositionValue.of(val.toString())) : List.of();
    }
}
