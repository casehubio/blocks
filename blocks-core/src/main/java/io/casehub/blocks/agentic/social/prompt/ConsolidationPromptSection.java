package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.speech.PromptContext;
import io.casehub.blocks.speech.PromptSection;
import io.casehub.neocortex.mindmap.ConsolidationArtifact;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

public class ConsolidationPromptSection implements PromptSection {

    private static final int MAX_ITEMS_PER_TYPE = 5;
    private final List<ConsolidationArtifact> artifacts;

    public ConsolidationPromptSection(List<ConsolidationArtifact> artifacts) {
        this.artifacts = artifacts;
    }

    @Override
    public @Nullable String contribute(PromptContext context) {
        if (artifacts.isEmpty()) return null;
        var sb = new StringBuilder("Recent consolidation insights:");
        renderGraduated(sb);
        renderMerges(sb);
        renderCuriosity(sb);
        renderCommunity(sb);
        var result = sb.toString();
        return result.equals("Recent consolidation insights:") ? null : result;
    }

    private void renderGraduated(StringBuilder sb) {
        artifacts.stream()
            .filter(ConsolidationArtifact.GraduatedExperience.class::isInstance)
            .map(ConsolidationArtifact.GraduatedExperience.class::cast)
            .sorted(Comparator.comparingDouble(ConsolidationArtifact.GraduatedExperience::graduationScore).reversed())
            .limit(MAX_ITEMS_PER_TYPE)
            .forEach(e -> sb.append("\nConsolidated knowledge: ")
                .append(e.name()).append(" (kind: ").append(e.cognitiveKind())
                .append(", strength: ").append(String.format("%.2f", e.graduationScore())).append(")"));
    }

    private void renderMerges(StringBuilder sb) {
        artifacts.stream()
            .filter(ConsolidationArtifact.MergePerformed.class::isInstance)
            .map(ConsolidationArtifact.MergePerformed.class::cast)
            .sorted(Comparator.comparingDouble(ConsolidationArtifact.MergePerformed::score).reversed())
            .limit(MAX_ITEMS_PER_TYPE)
            .forEach(m -> sb.append("\nUnified concepts: ")
                .append(m.keepNodeId()).append(" absorbed ").append(m.removedNodeId())
                .append(" — ").append(m.reason()));
        artifacts.stream()
            .filter(ConsolidationArtifact.MergeFlagged.class::isInstance)
            .map(ConsolidationArtifact.MergeFlagged.class::cast)
            .sorted(Comparator.comparingDouble(ConsolidationArtifact.MergeFlagged::score).reversed())
            .limit(MAX_ITEMS_PER_TYPE)
            .forEach(m -> sb.append("\nPossible connection: ")
                .append(m.nodeId1()).append(" and ").append(m.nodeId2())
                .append(" seem related (").append(String.format("%.2f", m.score()))
                .append(") — ").append(m.reason()));
    }

    private void renderCuriosity(StringBuilder sb) {
        artifacts.stream()
            .filter(ConsolidationArtifact.CuriosityQuestion.class::isInstance)
            .map(ConsolidationArtifact.CuriosityQuestion.class::cast)
            .sorted(Comparator.comparingDouble(ConsolidationArtifact.CuriosityQuestion::score).reversed())
            .limit(MAX_ITEMS_PER_TYPE)
            .forEach(q -> sb.append("\nQuestion to explore: ")
                .append(q.question()).append(" — ").append(q.description()));
    }

    private void renderCommunity(StringBuilder sb) {
        artifacts.stream()
            .filter(ConsolidationArtifact.CommunitySummaryCreated.class::isInstance)
            .map(ConsolidationArtifact.CommunitySummaryCreated.class::cast)
            .sorted(Comparator.comparingInt(ConsolidationArtifact.CommunitySummaryCreated::memberCount).reversed())
            .limit(MAX_ITEMS_PER_TYPE)
            .forEach(c -> sb.append("\nCommunity theme: ")
                .append(c.title()).append(" (").append(c.memberCount()).append(" concepts)"));
    }
}
