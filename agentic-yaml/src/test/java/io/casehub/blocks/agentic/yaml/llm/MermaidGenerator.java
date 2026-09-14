package io.casehub.blocks.agentic.yaml.llm;

import io.casehub.blocks.agentic.social.CognitionSnapshot;

class MermaidGenerator {

    static String generate(CognitionSnapshot snapshot) {
        var sb = new StringBuilder();
        sb.append("graph TD\n");
        var agentNode = sanitizeId(snapshot.agentId());
        sb.append(String.format("    %s[\"%s\"]%n", agentNode, snapshot.agentId()));

        for (var entry : snapshot.mentalModels().entrySet()) {
            var subjectId = entry.getKey();
            var subjectNode = sanitizeId(subjectId);
            var model = entry.getValue();
            sb.append(String.format("    %s[\"%s\"]%n", subjectNode, subjectId));

            for (var belief : model.beliefs()) {
                var beliefNode = sanitizeId("b_" + belief.key());
                sb.append(String.format("    %s -->|believes| %s[\"%s (%.1f)\"]%n",
                        agentNode, beliefNode,
                        truncate(belief.description(), 40),
                        belief.confidence()));
            }
            for (var desire : model.desires()) {
                var desireNode = sanitizeId("d_" + desire.key());
                sb.append(String.format("    %s -.->|desires| %s[\"%s (%.1f)\"]%n",
                        agentNode, desireNode,
                        truncate(desire.description(), 40),
                        desire.confidence()));
            }
        }

        if (snapshot.narrative() != null) {
            for (var theme : snapshot.narrative().themes()) {
                var themeNode = sanitizeId("theme_" + theme.label());
                sb.append(String.format("    %s -.->|theme| %s[\"%s (%.1f)\"]%n",
                        agentNode, themeNode, theme.label(), theme.salience()));
            }
        }

        for (var goal : snapshot.goalProposals()) {
            var goalNode = sanitizeId("goal_" + goal.goalName());
            sb.append(String.format("    %s ==>|goal| %s[\"%s (%.1f)\"]%n",
                    agentNode, goalNode,
                    goal.goalName(), goal.driveIntensity()));
        }

        if (snapshot.drives() != null) {
            sb.append(String.format("    %s_drives[\"drives: %s %.2f\"]%n",
                    agentNode,
                    snapshot.drives().dominantDrive(),
                    snapshot.drives().compositeMotivation()));
            sb.append(String.format("    %s --- %s_drives%n",
                    agentNode, agentNode));
        }

        return sb.toString();
    }

    private static String sanitizeId(String input) {
        return input.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private static String truncate(String text, int maxLen) {
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
}
