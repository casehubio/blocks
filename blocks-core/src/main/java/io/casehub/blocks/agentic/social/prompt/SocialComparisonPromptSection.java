package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.speech.PromptContext;
import io.casehub.blocks.speech.PromptSection;
import io.casehub.neocortex.cognitive.index.AgentPair;
import io.casehub.neocortex.cognitive.index.PadDimension;
import io.casehub.neocortex.cognitive.index.PerspectivalComparison;
import io.casehub.neocortex.cognitive.index.TrendAgreement;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public class SocialComparisonPromptSection implements PromptSection {

    private static final int MAX_PAIRS_PER_ENTITY = 5;
    private static final double DEFAULT_DISTANCE_THRESHOLD = 0.4;

    private final Map<String, PerspectivalComparison> comparisons;
    private final double distanceThreshold;

    public SocialComparisonPromptSection(Map<String, PerspectivalComparison> comparisons) {
        this(comparisons, DEFAULT_DISTANCE_THRESHOLD);
    }

    public SocialComparisonPromptSection(Map<String, PerspectivalComparison> comparisons,
                                          double distanceThreshold) {
        this.comparisons = comparisons;
        this.distanceThreshold = distanceThreshold;
    }

    @Override
    public @Nullable String contribute(PromptContext context) {
        if (comparisons.isEmpty()) return null;
        var sb = new StringBuilder();
        for (var entry : comparisons.entrySet()) {
            renderEntity(sb, entry.getValue());
        }
        return sb.isEmpty() ? null : sb.toString().strip();
    }

    private void renderEntity(StringBuilder sb, PerspectivalComparison comparison) {
        var distances = comparison.distances().distances();
        var rendered = 0;
        var entityBlock = new StringBuilder();

        for (var distEntry : distances.entrySet()) {
            if (rendered >= MAX_PAIRS_PER_ENTITY) break;
            var pair = distEntry.getKey();
            var distance = distEntry.getValue();
            if (distance <= distanceThreshold) continue;

            var agreement = comparison.trajectoryAlignment().agreements().get(pair);
            if (agreement == TrendAgreement.ALIGNED) continue;

            if (entityBlock.isEmpty()) {
                entityBlock.append("Social divergence — ").append(comparison.entityName()).append(":");
            }

            entityBlock.append("\n  ").append(pair.a().value())
                       .append(" ↔ ").append(pair.b().value())
                       .append(": distance ").append(String.format("%.2f", distance));

            var dominant = dominantDimension(comparison, pair);
            if (dominant != null) {
                var diff = comparison.dimensionDifferences().get(dominant.dim)
                                     .differences().get(pair);
                entityBlock.append("\n    Dominant difference: ")
                           .append(dominant.dim.name().toLowerCase())
                           .append(" — ").append(pair.a().value())
                           .append(diff > 0 ? " +" : " ")
                           .append(String.format("%.2f", diff))
                           .append(" vs ").append(pair.b().value());
            }

            if (agreement != null && agreement != TrendAgreement.INSUFFICIENT) {
                entityBlock.append("\n    Trajectory: ").append(agreement.name());
            }
            rendered++;
        }

        if (!entityBlock.isEmpty()) {
            if (!sb.isEmpty()) sb.append("\n\n");
            sb.append(entityBlock);
        }
    }

    private record DimDiff(PadDimension dim, double absDiff) {}

    private @Nullable DimDiff dominantDimension(PerspectivalComparison comparison,
                                                  AgentPair pair) {
        DimDiff max = null;
        for (var dimEntry : comparison.dimensionDifferences().entrySet()) {
            var diff = dimEntry.getValue().differences().get(pair);
            if (diff != null) {
                var abs = Math.abs(diff);
                if (max == null || abs > max.absDiff) {
                    max = new DimDiff(dimEntry.getKey(), abs);
                }
            }
        }
        return max;
    }
}
