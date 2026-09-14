package io.casehub.blocks.agentic.yaml.llm;

import io.casehub.blocks.agentic.social.CognitionMetrics;
import io.casehub.blocks.agentic.social.CognitionSnapshot;
import io.casehub.blocks.agentic.yaml.compiler.CompiledWorld;
import io.casehub.blocks.speech.PromptContext;
import io.casehub.blocks.speech.PromptSection;
import io.casehub.blocks.summarisation.observation.affordance.AffordanceRenderer;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ConversationRunner {

    private final AgentProvider agentProvider;
    private final CognitionStack cognition;
    private final CompiledWorld world;
    private final List<AgentDescriptor> descriptors;
    private final int maxTurns;
    private final String tenantId;
    private final boolean includeCognition;

    ConversationRunner(AgentProvider agentProvider, CognitionStack cognition,
                       CompiledWorld world, List<AgentDescriptor> descriptors,
                       int maxTurns, String tenantId, boolean includeCognition) {
        this.agentProvider = agentProvider;
        this.cognition = cognition;
        this.world = world;
        this.descriptors = descriptors;
        this.maxTurns = maxTurns;
        this.tenantId = tenantId;
        this.includeCognition = includeCognition;
    }

    public static Builder builder() { return new Builder(); }

    public ConversationResult run() {
        var renderer = new AffordanceRenderer();
        var history = new ArrayList<Turn>();
        var metrics = new ArrayList<CognitionMetrics>();
        var start = Instant.now();
        var speakers = descriptors.stream()
                .filter(d -> !d.name().equals("historian-narrator"))
                .toList();
        var subjectIds = speakers.stream()
                .map(AgentDescriptor::name)
                .collect(Collectors.toSet());

        for (int turn = 0; turn < maxTurns; turn++) {
            var speaker = speakers.get(turn % speakers.size());
            var agentId = speaker.name();
            var otherSpeakers = speakers.stream()
                    .map(AgentDescriptor::name)
                    .filter(n -> !n.equals(agentId))
                    .collect(Collectors.toSet());
            var subjectId = otherSpeakers.stream().findFirst().orElse(null);

            var before = cognition.snapshot(agentId, tenantId, turn, subjectIds);
            cognition.tick(agentId, tenantId, speaker, otherSpeakers);

            var systemPrompt = buildSystemPrompt(speaker, renderer, subjectId);
            var userPrompt = buildUserPrompt(history);

            System.out.printf("%n--- SYSTEM PROMPT (turn %d, %s) ---%n%s%n--- END SYSTEM PROMPT ---%n",
                    turn + 1, agentId, systemPrompt);

            var config = AgentSessionConfig.of(systemPrompt, userPrompt,
                    Duration.ofSeconds(300));
            String response = agentProvider.invoke(config)
                    .filter(e -> e instanceof AgentEvent.TextDelta)
                    .map(e -> ((AgentEvent.TextDelta) e).text())
                    .collect().with(Collectors.joining())
                    .await().atMost(Duration.ofSeconds(300));

            var turnRecord = new Turn(turn + 1, agentId, speaker.name(), response);
            history.add(turnRecord);

            if (!history.isEmpty() && subjectId != null) {
                var lastMessage = history.size() > 1
                        ? history.get(history.size() - 2).dialogue() : "";
                cognition.core().recordInteraction(agentId, tenantId,
                        subjectId, lastMessage, response);
            }

            cognition.updateNarrative(agentId, tenantId, history);

            var after = cognition.snapshot(agentId, tenantId, turn + 1, subjectIds);
            var delta = after.diffFrom(before);

            var sectionContent = new LinkedHashMap<String, String>();
            if (includeCognition) {
                var ctx = new PromptContext(agentId, tenantId, subjectId);
                for (var section : cognition.promptSections()) {
                    var text = section.contribute(ctx);
                    if (text != null && !text.isBlank()) {
                        sectionContent.put(
                                section.getClass().getSimpleName(), text);
                    }
                }
            }

            var turnMetrics = new CognitionMetrics(turn + 1, agentId,
                    sectionContent.size(), sectionContent, delta, after);
            metrics.add(turnMetrics);

            printTurn(turnRecord);
            System.out.print(turnMetrics.summary());
        }

        var finalSnapshot = metrics.isEmpty() ? null
                : metrics.getLast().snapshotAfter();
        return new ConversationResult(history, metrics, finalSnapshot,
                Duration.between(start, Instant.now()));
    }

    private String buildSystemPrompt(AgentDescriptor speaker,
                                      AffordanceRenderer renderer,
                                      String subjectId) {
        var sb = new StringBuilder();
        sb.append(speaker.briefing()).append("\n\n");

        if (includeCognition) {
            var context = new PromptContext(speaker.name(), tenantId, subjectId);
            for (PromptSection section : cognition.promptSections()) {
                var contribution = section.contribute(context);
                if (contribution != null && !contribution.isBlank()) {
                    sb.append(contribution).append("\n\n");
                }
            }
        }

        if (world != null) {
            var sections = world.pipeline().apply(world.sections(), Set.of());
            var worldText = renderer.renderObservation(sections);
            if (!worldText.isBlank()) {
                sb.append("## Current Surroundings\n\n").append(worldText).append("\n\n");
            }
            sb.append(renderer.renderActionVocabulary("Available actions:", world.actions()));
        }

        sb.append("\n\nRespond in character. Keep responses to 2-3 paragraphs.");
        return sb.toString();
    }

    private String buildUserPrompt(List<Turn> history) {
        if (history.isEmpty()) {
            return "The conversation begins. Introduce yourself and share what fascinates you most about the other person's work.";
        }
        var sb = new StringBuilder();
        for (var turn : history) {
            sb.append(turn.speakerName()).append(": ");
            sb.append(turn.dialogue()).append("\n\n");
        }
        sb.append("Respond in character.");
        return sb.toString();
    }

    private void printTurn(Turn turn) {
        System.out.printf("%n=== Turn %d — %s ===%n", turn.number(), turn.speakerName());
        System.out.printf("[dialogue] %s%n", turn.dialogue());
    }

    public record Turn(int number, String agentId, String speakerName, String dialogue) {}

    public record ConversationResult(List<Turn> turns, List<CognitionMetrics> metrics,
                                      CognitionSnapshot finalSnapshot, Duration elapsed) {
        public int turnCount() { return turns.size(); }
    }

    public static final class Builder {
        private AgentProvider agentProvider;
        private CognitionStack cognition;
        private CompiledWorld world;
        private List<AgentDescriptor> descriptors;
        private int maxTurns = 6;
        private String tenantId = "showcase";
        private boolean includeCognition = true;

        public Builder agentProvider(AgentProvider p) { this.agentProvider = p; return this; }
        public Builder cognition(CognitionStack c) { this.cognition = c; return this; }
        public Builder world(CompiledWorld w) { this.world = w; return this; }
        public Builder descriptors(List<AgentDescriptor> d) { this.descriptors = d; return this; }
        public Builder maxTurns(int n) { this.maxTurns = n; return this; }
        public Builder tenantId(String t) { this.tenantId = t; return this; }
        public Builder includeCognition(boolean b) { this.includeCognition = b; return this; }

        public ConversationRunner build() {
            if (agentProvider == null) throw new IllegalStateException("agentProvider required");
            if (cognition == null) throw new IllegalStateException("cognition required");
            if (descriptors == null || descriptors.isEmpty()) throw new IllegalStateException("descriptors required");
            return new ConversationRunner(agentProvider, cognition, world, descriptors, maxTurns, tenantId, includeCognition);
        }
    }
}
