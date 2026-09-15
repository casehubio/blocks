package io.casehub.blocks.conversation;

import io.casehub.qhorus.api.message.MessageType;

import java.util.Comparator;
import java.util.Set;

public final class ConvergencePolicies {

    private ConvergencePolicies() {}

    private static final Comparator<ConvergenceSignal> TIEBREAKER =
            Comparator.comparingDouble(ConvergenceSignal::confidence)
                    .thenComparingInt(s -> severity(s.state()));

    public static ConvergencePolicy structural(double similarityThreshold, int staleRounds) {
        return (state, commonGround, context) -> {
            if (context.totalPoints() == 0) {
                return new ConvergenceSignal(ConvergenceState.PROGRESSING, 0.0, "no points yet");
            }

            double establishedRatio = (double) context.establishedCount() / context.totalPoints();

            if (context.recentSimilarity() >= similarityThreshold) {
                return new ConvergenceSignal(ConvergenceState.DEADLOCK,
                        context.recentSimilarity(),
                        "content similarity " + String.format("%.2f", context.recentSimilarity())
                                + " exceeds threshold");
            }

            if (context.roundsSinceNewPoint() >= staleRounds) {
                return new ConvergenceSignal(ConvergenceState.DIMINISHING_RETURNS,
                        Math.min(1.0, (double) context.roundsSinceNewPoint() / (staleRounds * 2)),
                        "no new points for " + context.roundsSinceNewPoint() + " rounds");
            }

            int statusCount = context.recentMessageTypeCounts().getOrDefault(MessageType.STATUS, 0);
            int substantiveCount = context.recentMessageTypeCounts().getOrDefault(MessageType.RESPONSE, 0)
                    + context.recentMessageTypeCounts().getOrDefault(MessageType.COMMAND, 0)
                    + context.recentMessageTypeCounts().getOrDefault(MessageType.QUERY, 0);
            if (substantiveCount > 0 && statusCount > substantiveCount * 2) {
                return new ConvergenceSignal(ConvergenceState.DIMINISHING_RETURNS,
                        0.6, "STATUS messages dominate — ratio " + statusCount + ":" + substantiveCount);
            }

            if (establishedRatio >= 0.9) {
                return new ConvergenceSignal(ConvergenceState.CONSENSUS,
                        establishedRatio,
                        context.establishedCount() + "/" + context.totalPoints() + " points established");
            }

            if (establishedRatio >= 0.5 && context.roundsSinceStatusChange() <= 2) {
                return new ConvergenceSignal(ConvergenceState.CONVERGING,
                        establishedRatio,
                        "common ground growing — " + context.pendingCount() + " pending, "
                                + context.disputedCount() + " disputed");
            }

            return new ConvergenceSignal(ConvergenceState.PROGRESSING, 0.0, "conversation active");
        };
    }

    public static ConvergencePolicy commonGroundRatio(double consensusThreshold,
                                                       double deadlockDisputeRatio) {
        return (state, commonGround, context) -> {
            if (context.totalPoints() == 0) {
                return new ConvergenceSignal(ConvergenceState.PROGRESSING, 0.0, "no points yet");
            }
            double establishedRatio = (double) context.establishedCount() / context.totalPoints();
            double disputedRatio = (double) context.disputedCount() / context.totalPoints();

            if (establishedRatio >= consensusThreshold) {
                return new ConvergenceSignal(ConvergenceState.CONSENSUS,
                        establishedRatio,
                        context.establishedCount() + "/" + context.totalPoints() + " established");
            }
            if (disputedRatio >= deadlockDisputeRatio) {
                return new ConvergenceSignal(ConvergenceState.DEADLOCK,
                        disputedRatio,
                        context.disputedCount() + "/" + context.totalPoints() + " disputed");
            }
            return new ConvergenceSignal(ConvergenceState.PROGRESSING, 0.0, "conversation active");
        };
    }

    public static ConvergencePolicy composite(ConvergencePolicy... policies) {
        return (state, commonGround, context) -> {
            ConvergenceSignal best = null;
            for (ConvergencePolicy policy : policies) {
                ConvergenceSignal signal = policy.evaluate(state, commonGround, context);
                if (best == null || TIEBREAKER.compare(signal, best) > 0) {
                    best = signal;
                }
            }
            return best != null ? best
                    : new ConvergenceSignal(ConvergenceState.PROGRESSING, 0.0, "no policies");
        };
    }

    private static int severity(ConvergenceState state) {
        return switch (state) {
            case DEADLOCK -> 5;
            case DIMINISHING_RETURNS -> 4;
            case CONVERGING -> 3;
            case PROGRESSING -> 2;
            case CONSENSUS -> 1;
        };
    }
}
