package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.agentic.social.need.NeedTier;
import io.casehub.blocks.speech.PromptContext;
import io.casehub.blocks.speech.PromptSection;
import io.casehub.neocortex.mindmap.MindMapStore;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class NeedsPyramidPromptSection implements PromptSection {

    private final MindMapStore mindMapStore;
    private final Map<String, Set<NeedTier>> tierMapping;

    public NeedsPyramidPromptSection(MindMapStore mindMapStore, Map<String, Set<NeedTier>> tierMapping) {
        this.mindMapStore = mindMapStore;
        this.tierMapping = tierMapping;
    }

    @Override
    public @Nullable String contribute(PromptContext context) {
        var subgraphs = mindMapStore.listSubgraphs(context.tenantId());

        var driveTypes = new HashSet<String>();
        var satisfactionByTier = new EnumMap<NeedTier, Double>(NeedTier.class);

        for (var sg : subgraphs) {
            if (!"cognitive".equals(sg.type())) continue;
            for (var node : mindMapStore.nodesIn(sg.id(), context.tenantId())) {
                if (!context.agentId().equals(node.properties().get("agent-id"))) continue;

                var kind = node.properties().get("cognitiveKind");
                if ("drive-intensity".equals(kind)) {
                    var dt = node.properties().get("drive-type");
                    if (dt != null) driveTypes.add(dt);
                } else if ("need-satisfaction".equals(kind)) {
                    var tierName = node.properties().get("tier");
                    var satStr = node.properties().get("satisfaction");
                    if (tierName != null && satStr != null) {
                        try {
                            satisfactionByTier.put(NeedTier.valueOf(tierName), Double.parseDouble(satStr));
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
        }

        if (driveTypes.isEmpty()) return null;

        var reachableTiers = new HashSet<NeedTier>();
        for (var dt : driveTypes) {
            var tiers = tierMapping.get(dt);
            if (tiers != null) reachableTiers.addAll(tiers);
        }
        if (reachableTiers.isEmpty()) return null;

        var sb = new StringBuilder("## Inner Needs\n\n");
        for (NeedTier tier : NeedTier.values()) {
            if (!reachableTiers.contains(tier)) continue;
            double satisfaction = satisfactionByTier.getOrDefault(tier, 0.5);
            sb.append(renderTier(tier, satisfaction)).append('\n');
        }
        return sb.toString().stripTrailing();
    }

    private String renderTier(NeedTier tier, double satisfaction) {
        String noun = tierNoun(tier);
        String band = bandLabel(satisfaction);
        String note = contextNote(tier, satisfaction);

        return switch (band) {
            case "critically neglected" -> "Your " + noun + " feels critically neglected" +
                (note.isEmpty() ? "." : " — " + note + ".");
            case "neglected" -> "Your " + noun + " feels neglected" +
                (note.isEmpty() ? "." : " — " + note + ".");
            case "adequate" -> "Your " + noun + " feels adequate.";
            case "well-met" -> "Your " + noun + " feels well-met.";
            case "fulfilled" -> "Your " + noun + " feels fulfilled" +
                (note.isEmpty() ? "." : " — " + note + ".");
            default -> "Your " + noun + " feels adequate.";
        };
    }

    private static String tierNoun(NeedTier tier) {
        return switch (tier) {
            case SAFETY -> "sense of safety";
            case TASKS -> "task commitments";
            case SOCIAL -> "social bonds";
            case SELF_EXPRESSION -> "self-expression";
            case UNDERSTANDING -> "curiosity";
        };
    }

    private static String bandLabel(double satisfaction) {
        if (satisfaction < 0.2) return "critically neglected";
        if (satisfaction < 0.4) return "neglected";
        if (satisfaction < 0.6) return "adequate";
        if (satisfaction < 0.8) return "well-met";
        return "fulfilled";
    }

    private static String contextNote(NeedTier tier, double satisfaction) {
        if (satisfaction >= 0.4 && satisfaction < 0.8) return "";
        boolean positive = satisfaction >= 0.8;
        return switch (tier) {
            case SAFETY -> positive ? "you feel secure in your surroundings" : "recent events have left you uneasy";
            case TASKS -> positive ? "you're on top of your responsibilities" : "obligations are piling up";
            case SOCIAL -> positive ? "your relationships feel strong" : "you've been isolated lately";
            case SELF_EXPRESSION -> positive ? "you've been true to yourself lately" : "you haven't been true to yourself";
            case UNDERSTANDING -> positive ? "the world around you makes sense" : "there's much you don't understand yet";
        };
    }
}
