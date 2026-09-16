package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.speech.PromptContext;
import io.casehub.blocks.speech.PromptSection;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;

public class CharacterDrivePromptSection implements PromptSection {

    private final MindMapStore mindMapStore;

    public CharacterDrivePromptSection(MindMapStore mindMapStore) {
        this.mindMapStore = mindMapStore;
    }

    @Override
    public @Nullable String contribute(PromptContext context) {
        var subgraphs = mindMapStore.listSubgraphs(context.tenantId());

        var driveNodes = subgraphs.stream()
            .filter(s -> "cognitive".equals(s.type()))
            .flatMap(s -> mindMapStore.nodesIn(s.id(), context.tenantId()).stream())
            .filter(n -> "drive-intensity".equals(n.properties().get("cognitiveKind")))
            .filter(n -> context.agentId().equals(n.properties().get("agent-id")))
            .sorted(Comparator.comparingDouble(
                (MindMapNode n) -> Double.parseDouble(
                    n.properties().getOrDefault("intensity", "0")))
                .reversed())
            .toList();

        if (driveNodes.isEmpty()) return null;

        var sb = new StringBuilder("## Character Motivations\n\n");
        for (var node : driveNodes) {
            var type = node.properties().get("drive-type");
            var intensity = Double.parseDouble(
                node.properties().getOrDefault("intensity", "0"));
            var description = node.properties().getOrDefault("description", "");
            sb.append(String.format("- %s (%.0f%%) — %s%n", type, intensity * 100, description));
        }
        return sb.toString().stripTrailing();
    }
}
