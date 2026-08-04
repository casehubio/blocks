package io.casehub.blocks.conversation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.work.progress.ProgressInstance;
import io.casehub.work.progress.ProgressStatus;
import io.casehub.work.progress.StepDefinition;
import io.casehub.work.progress.StepStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DefaultProgressRenderer implements ProgressRenderer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Map<StepStatus, String> STEP_GLYPHS = Map.of(
            StepStatus.COMPLETED, "✓",
            StepStatus.ACTIVE, "⏳",
            StepStatus.SKIPPED, "⊘",
            StepStatus.FAILED, "✗",
            StepStatus.PENDING, "○"
    );

    @Override
    public String render(ProgressInstance progress) {
        String label = extractLabel(progress);
        if (progress.state() == null || progress.state().isMissingNode()) {
            return label + ": " + progress.status().name();
        }
        return switch (progress.shapeType()) {
            case "percentage" -> renderPercentage(label, progress);
            case "count" -> renderCount(label, progress);
            case "step" -> renderSteps(progress);
            default -> label + ": " + progress.status().name();
        };
    }

    private String renderPercentage(String label, ProgressInstance pi) {
        JsonNode valueNode = pi.state().get("value");
        if (valueNode == null || !valueNode.isInt()) {
            return label + ": " + pi.status().name();
        }
        return label + ": " + valueNode.intValue() + "%" + statusSuffix(pi.status());
    }

    private String renderCount(String label, ProgressInstance pi) {
        JsonNode currentNode = pi.state().get("current");
        JsonNode totalNode = pi.state().get("total");
        if (currentNode == null || !currentNode.isInt()
                || totalNode == null || !totalNode.isInt()) {
            return label + ": " + pi.status().name();
        }
        var sb = new StringBuilder();
        sb.append(label).append(": ")
          .append(currentNode.intValue()).append(" of ").append(totalNode.intValue());
        if (pi.definition() != null && pi.definition().has("unit")) {
            sb.append(" ").append(pi.definition().get("unit").asText());
        }
        sb.append(statusSuffix(pi.status()));
        return sb.toString();
    }

    private String renderSteps(ProgressInstance pi) {
        if (pi.definition() == null || pi.definition().isMissingNode()) {
            return extractLabel(pi) + ": " + pi.status().name();
        }
        List<StepDefinition> stepDefs;
        try {
            stepDefs = MAPPER.readerForListOf(StepDefinition.class)
                    .readValue(pi.definition());
        } catch (Exception e) {
            return extractLabel(pi) + ": " + pi.status().name();
        }
        JsonNode stepsState = pi.state().get("steps");
        if (stepsState == null || !stepsState.isObject()) {
            return extractLabel(pi) + ": " + pi.status().name();
        }
        var parts = new ArrayList<String>();
        for (StepDefinition def : stepDefs) {
            StepStatus status = StepStatus.PENDING;
            JsonNode stepNode = stepsState.get(def.name());
            if (stepNode != null && stepNode.has("status")) {
                try {
                    status = StepStatus.valueOf(
                            stepNode.get("status").asText().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {}
            }
            parts.add(def.name() + " " + STEP_GLYPHS.getOrDefault(status, "?"));
        }
        return String.join(" → ", parts);
    }

    private String extractLabel(ProgressInstance pi) {
        if (pi.definition() != null && pi.definition().has("label")) {
            return pi.definition().get("label").asText();
        }
        return pi.scopeId();
    }

    private String statusSuffix(ProgressStatus status) {
        return switch (status) {
            case COMPLETED -> " ✓";
            case FAILED -> " ✗";
            default -> "";
        };
    }
}
