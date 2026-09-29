package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.speech.PromptContext;
import io.casehub.blocks.speech.PromptSection;
import io.casehub.neocortex.cognitive.index.EntityKnowledge;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.platform.api.identity.PrincipalId;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class EntityKnowledgePromptSection implements PromptSection {

    private static final int MAX_MEMORIES_PER_DOMAIN = 3;
    private static final int MAX_EDGES = 5;
    private static final int MAX_COMPARISON_AGENTS = 3;

    private final List<EntityKnowledge> entities;
    private final Map<String, Map<PrincipalId, EntityKnowledge>> comparisons;

    public EntityKnowledgePromptSection(List<EntityKnowledge> entities,
                                        Map<String, Map<PrincipalId, EntityKnowledge>> comparisons) {
        this.entities = entities;
        this.comparisons = comparisons;
    }

    @Override
    public @Nullable String contribute(PromptContext context) {
        if (entities.isEmpty()) return null;
        var sb = new StringBuilder("Entity knowledge:");
        for (var ek : entities) {
            renderEntity(sb, ek);
            var cmp = comparisons.get(ek.node().id());
            if (cmp != null && !cmp.isEmpty()) renderComparison(sb, ek, cmp);
        }
        return sb.toString();
    }

    private void renderEntity(StringBuilder sb, EntityKnowledge ek) {
        sb.append("\n\nAbout ").append(ek.node().name());
        var kind = ek.node().subgraphType();
        if (kind != null) sb.append(" (").append(kind.toLowerCase()).append(")");
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

    private void renderComparison(StringBuilder sb, EntityKnowledge self,
                                   Map<PrincipalId, EntityKnowledge> others) {
        sb.append("\n  How others see ").append(self.node().name()).append(":");
        others.entrySet().stream().limit(MAX_COMPARISON_AGENTS)
            .forEach(entry -> {
                var agentId = entry.getKey().value();
                var otherEk = entry.getValue();
                var otherTrajectory = otherEk.trajectory();
                if (otherTrajectory != null && self.trajectory() != null
                    && otherTrajectory.trend() != self.trajectory().trend()) {
                    sb.append("\n    ").append(agentId).append(" sees trend as ")
                      .append(otherTrajectory.trend().name().toLowerCase())
                      .append(" (you see ").append(self.trajectory().trend().name().toLowerCase()).append(")");
                }
            });
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
