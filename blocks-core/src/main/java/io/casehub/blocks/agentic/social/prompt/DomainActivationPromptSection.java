package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.agentic.social.DomainActivationSnapshot;
import io.casehub.blocks.speech.PromptContext;
import io.casehub.blocks.speech.PromptSection;
import io.casehub.neocortex.cognitive.index.ConfidenceInterval;
import io.casehub.neocortex.cognitive.index.CorrelationStrength;
import io.casehub.neocortex.cognitive.index.DomainPair;
import io.casehub.neocortex.cognitive.index.TrendDirection;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;

public class DomainActivationPromptSection implements PromptSection {

    private static final int MAX_PAIRS = 5;
    private static final double SIGNIFICANCE_THRESHOLD = 0.05;

    private final DomainActivationSnapshot snapshot;

    public DomainActivationPromptSection(DomainActivationSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public @Nullable String contribute(@Nullable PromptContext context) {
        var renderablePairs = snapshot.pairwiseCorrelations().entrySet().stream()
                .filter(e -> e.getValue().strength().ordinal() <= CorrelationStrength.MODERATE.ordinal())
                .sorted(Comparator.comparingDouble(e -> -e.getValue().dtwSimilarity()))
                .limit(MAX_PAIRS)
                .toList();

        if (renderablePairs.isEmpty()) return null;

        var sb = new StringBuilder("Your emotional patterns across knowledge domains:");

        for (var entry : renderablePairs) {
            var pair = entry.getKey();
            var corr = entry.getValue();
            var nameA = snapshot.subgraphNames().getOrDefault(pair.subgraphIdA(), pair.subgraphIdA());
            var nameB = snapshot.subgraphNames().getOrDefault(pair.subgraphIdB(), pair.subgraphIdB());
            var strengthWord = corr.strength() == CorrelationStrength.STRONG ? "strongly" : "moderately";

            sb.append("\n- Your emotional states in \"").append(nameA).append("\" and \"")
              .append(nameB).append("\" are ").append(strengthWord)
              .append(" correlated — when one domain activates emotionally, the other tends to follow.");

            appendTrajectory(sb, pair);
            appendMoodCorrelation(sb, pair);
            appendEventImpact(sb, pair);
        }

        return sb.toString();
    }

    private void appendTrajectory(StringBuilder sb, DomainPair pair) {
        var sigA = snapshot.domainSignals().get(pair.subgraphIdA());
        var sigB = snapshot.domainSignals().get(pair.subgraphIdB());
        if (sigA == null || sigB == null) return;
        var tA = sigA.trajectory();
        var tB = sigB.trajectory();
        if (tA.sampleCount() < 3 || tB.sampleCount() < 3) return;
        if (tA.trend() == TrendDirection.STABLE && tB.trend() == TrendDirection.STABLE) return;

        var nameA = snapshot.subgraphNames().getOrDefault(pair.subgraphIdA(), pair.subgraphIdA());
        var nameB = snapshot.subgraphNames().getOrDefault(pair.subgraphIdB(), pair.subgraphIdB());
        sb.append(" Trajectory: ").append(nameA).append(" (").append(trendWord(tA.trend()))
          .append("), ").append(nameB).append(" (").append(trendWord(tB.trend())).append(").");
    }

    private void appendMoodCorrelation(StringBuilder sb, DomainPair pair) {
        var moodMap = snapshot.moodCorrelations().get(pair);
        if (moodMap == null) return;
        for (var moodEntry : moodMap.entrySet()) {
            var moodCorr = moodEntry.getValue();
            if (moodCorr.pValue() < SIGNIFICANCE_THRESHOLD
                && moodCorr.strength().ordinal() <= CorrelationStrength.MODERATE.ordinal()) {
                var domainName = snapshot.subgraphNames().getOrDefault(moodEntry.getKey(), moodEntry.getKey());
                sb.append(" Your mood is significantly associated with activity in ").append(domainName).append(".");
            }
        }
    }

    private void appendEventImpact(StringBuilder sb, DomainPair pair) {
        var impactMap = snapshot.experienceImpacts().get(pair);
        if (impactMap == null) return;
        for (var impactEntry : impactMap.entrySet()) {
            var impact = impactEntry.getValue();
            var domainName = snapshot.subgraphNames().getOrDefault(impactEntry.getKey(), impactEntry.getKey());
            for (var dimEntry : impact.meanDelta().entrySet()) {
                var dim = dimEntry.getKey();
                var delta = dimEntry.getValue();
                var ci = impact.confidenceInterval().get(dim);
                if (ci != null && !spansZero(ci) && Math.abs(delta) > 0.05) {
                    var direction = delta > 0 ? "increased" : "decreased";
                    sb.append(" Events in ").append(domainName)
                      .append(" are associated with ").append(direction)
                      .append(" ").append(dim.name().toLowerCase()).append(".");
                }
            }
        }
    }

    private static boolean spansZero(ConfidenceInterval ci) {
        return ci.lower() <= 0 && ci.upper() >= 0;
    }

    private static String trendWord(TrendDirection trend) {
        return switch (trend) {
            case IMPROVING -> "improving";
            case WORSENING -> "worsening";
            case STABLE -> "stable";
        };
    }
}
