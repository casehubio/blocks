package io.casehub.blocks.agentic.yaml.llm;

import io.casehub.blocks.agentic.social.CognitionMetrics;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class ConversationEvaluator {

    private final AgentProvider judge;

    ConversationEvaluator(AgentProvider judge) {
        this.judge = judge;
    }

    record ComparisonResult(
            Map<String, Integer> baselineScores,
            Map<String, Integer> cognitionScores,
            String winner,
            String assessment
    ) {}

    ComparisonResult compare(
            ConversationRunner.ConversationResult baseline,
            ConversationRunner.ConversationResult withCognition,
            List<CognitionMetrics> cognitionMetrics) {

        var prompt = buildJudgePrompt(baseline, withCognition);
        var systemPrompt = """
                You are a conversation quality judge. You evaluate two multi-turn \
                conversations between historical figures (Leonardo da Vinci and Nikola Tesla) \
                on five dimensions.
                
                Score each conversation 1-10 on:
                - groundedness: responses reference specific knowledge, not generic prose
                - adaptiveness: dialogue evolves based on what was said
                - character_consistency: speakers maintain distinct voices and perspectives
                - depth: conversation explores ideas in depth vs surface-level
                - memory_utilisation: later turns reference or build on earlier statements
                
                Respond ONLY with JSON:
                {"baseline":{"groundedness":N,"adaptiveness":N,"character_consistency":N,"depth":N,"memory_utilisation":N},\
                "cognition":{"groundedness":N,"adaptiveness":N,"character_consistency":N,"depth":N,"memory_utilisation":N},\
                "winner":"baseline or cognition or tie",\
                "assessment":"2-3 sentence comparison"}""";

        var config = AgentSessionConfig.of(systemPrompt, prompt,
                Duration.ofSeconds(120));
        var response = judge.invoke(config)
                .filter(e -> e instanceof AgentEvent.TextDelta)
                .map(e -> ((AgentEvent.TextDelta) e).text())
                .collect().with(Collectors.joining())
                .await().atMost(Duration.ofSeconds(120));

        return parseResponse(response);
    }

    private String buildJudgePrompt(
            ConversationRunner.ConversationResult a,
            ConversationRunner.ConversationResult b) {
        var sb = new StringBuilder();
        sb.append("## Conversation A (Baseline — no cognitive state)\n\n");
        for (var t : a.turns()) {
            sb.append(t.speakerName()).append(": ")
                    .append(t.dialogue()).append("\n\n");
        }
        sb.append("## Conversation B (With cognitive state injection)\n\n");
        for (var t : b.turns()) {
            sb.append(t.speakerName()).append(": ")
                    .append(t.dialogue()).append("\n\n");
        }
        sb.append("Score both conversations on all 5 dimensions.");
        return sb.toString();
    }

    private ComparisonResult parseResponse(String response) {
        var jsonStart = response.indexOf('{');
        var jsonEnd = response.lastIndexOf('}');
        if (jsonStart < 0 || jsonEnd < 0) {
            return new ComparisonResult(Map.of(), Map.of(),
                    "unknown", response);
        }
        var json = response.substring(jsonStart, jsonEnd + 1);
        try {
            var baselineScores = new LinkedHashMap<String, Integer>();
            var cognitionScores = new LinkedHashMap<String, Integer>();
            var winner = extractString(json, "winner");
            var assessment = extractString(json, "assessment");

            for (var dim : List.of("groundedness", "adaptiveness",
                    "character_consistency", "depth", "memory_utilisation")) {
                baselineScores.put(dim, extractScore(json, "baseline", dim));
                cognitionScores.put(dim, extractScore(json, "cognition", dim));
            }
            return new ComparisonResult(baselineScores, cognitionScores,
                    winner, assessment);
        } catch (Exception e) {
            return new ComparisonResult(Map.of(), Map.of(),
                    "parse-error", response);
        }
    }

    private static String extractString(String json, String key) {
        var pattern = "\"" + key + "\"\\s*:\\s*\"";
        var matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (!matcher.find()) return "";
        int start = matcher.end();
        int end = json.indexOf('"', start);
        return end > start ? json.substring(start, end) : "";
    }

    private static int extractScore(String json, String section, String dim) {
        var sectionStart = json.indexOf("\"" + section + "\"");
        if (sectionStart < 0) return 0;
        var rest = json.substring(sectionStart);
        var pattern = "\"" + dim + "\"\\s*:\\s*(\\d+)";
        var matcher = java.util.regex.Pattern.compile(pattern).matcher(rest);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }
}
