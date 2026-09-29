package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.speech.PromptContext;
import io.casehub.blocks.speech.PromptSection;
import io.casehub.neocortex.cognitive.index.EntityKnowledge;
import io.casehub.neocortex.memory.MemoryDomain;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class EntityKnowledgePromptSection implements PromptSection {

    private static final int MAX_MEMORIES_PER_DOMAIN = 3;
    private static final int MAX_EDGES               = 5;

    private final List<EntityKnowledge> entities;

    public EntityKnowledgePromptSection(List<EntityKnowledge> entities) {
        this.entities = entities;
    }

    @Override
    public @Nullable String contribute(PromptContext context) {
        if (entities.isEmpty()) {return null;}
        var sb = new StringBuilder("Entity knowledge:");
        for (var ek : entities) {
            renderEntity(sb, ek);
        }
        return sb.toString();
    }

    private void renderEntity(StringBuilder sb, EntityKnowledge ek) {
        sb.append("\n\nAbout ").append(ek.node().name());
        var kind = ek.node().subgraphType();
        if (kind != null) {sb.append(" (").append(kind.toLowerCase()).append(")");}
        sb.append(":");

        if (!ek.edges().isEmpty()) {
            sb.append("\n  Relationships:");
            ek.edges().stream().limit(MAX_EDGES)
              .forEach(e -> sb.append("\n    ")
                              .append(e.edgeType().toLowerCase())
                              .append(" → ").append(e.targetNodeId()));
        }

        for (var entry : ek.memories().entrySet()) {
            if (!entry.getValue().isEmpty()) {
                sb.append("\n  ").append(domainLabel(entry.getKey())).append(":");
                entry.getValue().stream().limit(MAX_MEMORIES_PER_DOMAIN)
                     .forEach(m -> sb.append("\n    - ").append(m.text()));
            }
        }

        if (ek.trajectory() != null) {
            var t = ek.trajectory();
            sb.append("\n  Emotional trajectory: ")
              .append(t.trend().name().toLowerCase())
              .append(" (rate: ").append(String.format("%.2f", t.rateOfChange())).append(")");
        }

        if (!ek.unresolvedRefs().isEmpty()) {
            sb.append("\n  Unresolved references:");
            ek.unresolvedRefs().forEach(ref -> {
                var label = ref.qualifier() != null ? ref.qualifier() : ref.id();
                sb.append("\n    - ").append(label).append(" (not yet known)");
            });
        }
    }

    private static String domainLabel(MemoryDomain domain) {
        var name = domain.name();
        return switch (name) {
            case "experience" -> "You remember";
            case "belief" -> "You believe";
            case "observation" -> "You observed";
            case "relationship" -> "Your relationship";
            case "reflection" -> "Your reflections";
            case "norm" -> "Social norms";
            default -> name.substring(0, 1).toUpperCase() + name.substring(1);
        };
    }
}
