package io.casehub.blocks.agentic.yaml.llm;

import io.casehub.blocks.agentic.social.CognitionMetrics;
import io.casehub.blocks.agentic.social.CognitionSnapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class ResultsWriter {

    static void writeConversation(ConversationRunner.ConversationResult result,
                                   String stageName, Path dir) throws IOException {
        Files.createDirectories(dir);
        var sb = new StringBuilder();
        sb.append("# ").append(stageName).append("\n\n");
        for (var turn : result.turns()) {
            sb.append(String.format("## Turn %d — %s%n",
                    turn.number(), turn.speakerName()));
            sb.append(String.format("[dialogue] %s%n%n", turn.dialogue()));
            result.metrics().stream()
                    .filter(m -> m.turnNumber() == turn.number())
                    .findFirst()
                    .ifPresent(m -> sb.append(m.summary()).append("\n"));
            sb.append("---\n\n");
        }
        sb.append("## Summary\n\n");
        sb.append("| Turn | Agent | Sections | Mood Δ | Drive Δ | BDI | Episodes | Goals |\n");
        sb.append("|------|-------|----------|--------|---------|-----|----------|-------|\n");
        for (var m : result.metrics()) {
            sb.append(m.toMarkdownRow()).append("\n");
        }
        Files.writeString(dir.resolve(stageName + "-conversation.md"),
                sb.toString());
    }

    static void writeDump(CognitionSnapshot snapshot, String stageName,
                           Path dir) throws IOException {
        Files.createDirectories(dir);
        var sb = new StringBuilder();
        sb.append("{\n");
        sb.append(String.format("  \"agent\": \"%s\",%n", snapshot.agentId()));
        sb.append(String.format("  \"turn\": %d,%n", snapshot.turnNumber()));
        if (snapshot.mood() != null) {
            sb.append(String.format("  \"mood\": { \"pleasure\": %.2f, \"arousal\": %.2f, \"dominance\": %.2f },%n",
                    snapshot.mood().pleasure(), snapshot.mood().arousal(),
                    snapshot.mood().dominance()));
        }
        if (snapshot.drives() != null) {
            sb.append("  \"drives\": {");
            var entries = snapshot.drives().drives().entrySet().stream().toList();
            for (int i = 0; i < entries.size(); i++) {
                var e = entries.get(i);
                sb.append(String.format(" \"%s\": %.2f", e.getKey(), e.getValue().intensity()));
                if (i < entries.size() - 1) sb.append(",");
            }
            sb.append(" },\n");
        }
        if (!snapshot.mentalModels().isEmpty()) {
            sb.append("  \"mentalModels\": {\n");
            for (var entry : snapshot.mentalModels().entrySet()) {
                sb.append(String.format("    \"%s\": { \"beliefs\": %d, \"desires\": %d, \"intentions\": %d }%n",
                        entry.getKey(),
                        entry.getValue().beliefs().size(),
                        entry.getValue().desires().size(),
                        entry.getValue().intentions().size()));
            }
            sb.append("  },\n");
        }
        if (!snapshot.goalProposals().isEmpty()) {
            sb.append("  \"goals\": [\n");
            for (var goal : snapshot.goalProposals()) {
                sb.append(String.format("    { \"name\": \"%s\", \"axis\": \"%s\", \"intensity\": %.2f }%n",
                        goal.goalName(), goal.axis(), goal.driveIntensity()));
            }
            sb.append("  ],\n");
        }
        sb.append(String.format("  \"capturedAt\": \"%s\"%n", snapshot.capturedAt()));
        sb.append("}\n");
        Files.writeString(dir.resolve(stageName + "-dump.json"), sb.toString());
    }

    static void writeMetrics(List<CognitionMetrics> metrics,
                              String stageName, Path dir) throws IOException {
        Files.createDirectories(dir);
        var sb = new StringBuilder();
        sb.append("# ").append(stageName).append(" — Metrics\n\n");
        sb.append("| Turn | Agent | Sections | Mood Δ | Drive Δ | BDI | Episodes | Goals |\n");
        sb.append("|------|-------|----------|--------|---------|-----|----------|-------|\n");
        for (var m : metrics) {
            sb.append(m.toMarkdownRow()).append("\n");
        }
        Files.writeString(dir.resolve(stageName + "-metrics.md"), sb.toString());
    }
}
