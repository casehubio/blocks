package io.casehub.blocks.agentic.social;

import java.util.Map;
import java.util.stream.Collectors;

public record CognitionMetrics(
        int turnNumber,
        String agentId,
        int promptSectionsContributed,
        Map<String, String> promptSectionContent,
        CognitionDelta delta,
        CognitionSnapshot snapshotAfter
) {
    public String summary() {
        var sb = new StringBuilder();
        sb.append(String.format("[sections] %d contributed%n",
                promptSectionsContributed));
        if (delta.mood() != null) {
            sb.append(String.format("[mood] P%+.2f A%+.2f D%+.2f%n",
                    delta.mood().pleasureDelta(),
                    delta.mood().arousalDelta(),
                    delta.mood().dominanceDelta()));
        }
        if (delta.drives() != null) {
            var driveStr = delta.drives().intensityDeltas().entrySet()
                    .stream()
                    .map(e -> String.format("%s:%+.2f",
                            e.getKey(), e.getValue()))
                    .collect(Collectors.joining(" "));
            sb.append(String.format("[drives] %s%n", driveStr));
        }
        for (var entry : delta.mentalModelDeltas().entrySet()) {
            var bdi = entry.getValue();
            sb.append(String.format("[mental-model] %s: +%dB +%dD +%dI%n",
                    entry.getKey(), bdi.newBeliefs(),
                    bdi.newDesires(), bdi.newIntentions()));
        }
        sb.append(String.format("[narrative] +%d episodes +%d themes%n",
                delta.newEpisodeCount(), delta.newThemeCount()));
        sb.append(String.format("[goals] %d new%n",
                delta.newGoals().size()));
        return sb.toString();
    }

    public String toMarkdownRow() {
        var moodStr = delta.mood() != null
                ? String.format("P%+.2f A%+.2f D%+.2f",
                        delta.mood().pleasureDelta(),
                        delta.mood().arousalDelta(),
                        delta.mood().dominanceDelta())
                : "—";
        var driveStr = delta.drives() != null
                ? String.format("%+.2f", delta.drives().compositeDelta())
                : "—";
        int totalBdi = delta.mentalModelDeltas().values().stream()
                .mapToInt(d -> d.newBeliefs() + d.newDesires()
                        + d.newIntentions())
                .sum();
        return String.format("| %d | %s | %d | %s | %s | %d | %d | %d |",
                turnNumber, agentId, promptSectionsContributed,
                moodStr, driveStr, totalBdi,
                delta.newEpisodeCount(), delta.newGoals().size());
    }
}
