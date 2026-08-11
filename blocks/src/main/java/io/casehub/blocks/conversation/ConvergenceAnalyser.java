package io.casehub.blocks.conversation;

import io.casehub.qhorus.api.message.MessageType;

import java.util.*;

public final class ConvergenceAnalyser {

    private ConvergenceAnalyser() {}

    public static ConvergenceSignal analyse(ConversationState state,
                                            CommonGroundState commonGround,
                                            ConvergencePolicy policy,
                                            int recentWindow) {
        ConvergenceContext context = buildContext(state, commonGround, recentWindow);
        return policy.evaluate(state, commonGround, context);
    }

    static ConvergenceContext buildContext(ConversationState state,
                                           CommonGroundState commonGround,
                                           int recentWindow) {
        int totalPoints = commonGround.establishedFacts().size()
                        + commonGround.pendingClaims().size()
                        + commonGround.disputedPoints().size();

        List<ThreadEntry> allEntries = flattenAndSort(state);
        int recentStart = Math.max(0, allEntries.size() - recentWindow);
        List<ThreadEntry> recent = allEntries.subList(recentStart, allEntries.size());

        double recentSimilarity = computeSimilarity(recent);
        double messageLengthTrend = computeLengthTrend(allEntries, recent);

        int maxRound = allEntries.isEmpty() ? 0
                : allEntries.stream().mapToInt(ThreadEntry::round).max().orElse(0);
        int lastNewPointRound = lastNewPointRound(state);
        int lastStatusChangeRound = lastStatusChangeRound(state, commonGround);

        var recentTypeCounts = new LinkedHashMap<MessageType, Integer>();
        for (ThreadEntry te : recent) {
            if (te.messageType() != null) {
                recentTypeCounts.merge(te.messageType(), 1, Integer::sum);
            }
        }

        return new ConvergenceContext(
                totalPoints,
                commonGround.establishedFacts().size(),
                commonGround.pendingClaims().size(),
                commonGround.disputedPoints().size(),
                recentSimilarity,
                messageLengthTrend,
                maxRound - lastNewPointRound,
                maxRound - lastStatusChangeRound,
                recentTypeCounts);
    }

    private static List<ThreadEntry> flattenAndSort(ConversationState state) {
        var entries = new ArrayList<ThreadEntry>();
        for (ConversationPoint point : state.points().values()) {
            entries.addAll(point.thread());
        }
        entries.sort(Comparator.comparingInt(ThreadEntry::round)
                .thenComparing(e -> e.createdAt() != null ? e.createdAt() : java.time.Instant.EPOCH));
        return entries;
    }

    private static double computeSimilarity(List<ThreadEntry> recent) {
        if (recent.size() < 2) { return 0.0; }
        double sum = 0;
        int count = 0;
        for (int i = 1; i < recent.size(); i++) {
            sum += jaccardSimilarity(
                    tokenize(recent.get(i - 1).content()),
                    tokenize(recent.get(i).content()));
            count++;
        }
        return count > 0 ? sum / count : 0.0;
    }

    private static double computeLengthTrend(List<ThreadEntry> all, List<ThreadEntry> recent) {
        if (all.isEmpty() || recent.isEmpty()) { return 1.0; }
        double overallAvg = all.stream()
                .mapToInt(e -> e.content() != null ? e.content().length() : 0)
                .average().orElse(1.0);
        double recentAvg = recent.stream()
                .mapToInt(e -> e.content() != null ? e.content().length() : 0)
                .average().orElse(1.0);
        return overallAvg > 0 ? recentAvg / overallAvg : 1.0;
    }

    private static int lastNewPointRound(ConversationState state) {
        int last = 0;
        for (ConversationPoint point : state.points().values()) {
            if (!point.thread().isEmpty()) {
                int firstRound = point.thread().get(0).round();
                if (firstRound > last) { last = firstRound; }
            }
        }
        return last;
    }

    private static int lastStatusChangeRound(ConversationState state,
                                              CommonGroundState commonGround) {
        int last = 0;
        for (ConversationPoint point : state.points().values()) {
            if (commonGround.establishedFacts().containsKey(point.id())
                    || commonGround.disputedPoints().containsKey(point.id())) {
                for (ThreadEntry te : point.thread()) {
                    if (te.round() > last) { last = te.round(); }
                }
            }
        }
        return last;
    }

    static double jaccardSimilarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) { return 1.0; }
        if (a.isEmpty() || b.isEmpty()) { return 0.0; }
        long intersection = a.stream().filter(b::contains).count();
        var union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection / union.size();
    }

    static Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) { return Set.of(); }
        var tokens = new HashSet<String>();
        for (String token : text.split("\\s+")) {
            String cleaned = token.toLowerCase().replaceAll("[^a-z0-9]", "");
            if (!cleaned.isEmpty()) { tokens.add(cleaned); }
        }
        return tokens;
    }
}
