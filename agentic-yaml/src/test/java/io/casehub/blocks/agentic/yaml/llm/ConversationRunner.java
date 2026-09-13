package io.casehub.blocks.agentic.yaml.llm;

import io.casehub.blocks.agentic.yaml.compiler.CompiledWorld;
import io.casehub.blocks.speech.PromptContext;
import io.casehub.blocks.speech.PromptSection;
import io.casehub.blocks.summarisation.observation.affordance.AffordanceRenderer;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionInit;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ConversationRunner {

    private final AgentProvider agentProvider;
    private final CognitionStack cognition;
    private final CompiledWorld world;
    private final List<AgentDescriptor> descriptors;
    private final int maxTurns;
    private final String tenantId;

    ConversationRunner(AgentProvider agentProvider, CognitionStack cognition,
                       CompiledWorld world, List<AgentDescriptor> descriptors,
                       int maxTurns, String tenantId) {
        this.agentProvider = agentProvider;
        this.cognition = cognition;
        this.world = world;
        this.descriptors = descriptors;
        this.maxTurns = maxTurns;
        this.tenantId = tenantId;
    }

    public static Builder builder() { return new Builder(); }

    public ConversationResult run() {
        var renderer = new AffordanceRenderer();
        var history = new ArrayList<Turn>();
        var start = Instant.now();
        var speakers = descriptors.stream()
                .filter(d -> !d.name().equals("historian-narrator"))
                .toList();

        Map<String, AgentSession> sessions = new LinkedHashMap<>();
        try {
            for (var speaker : speakers) {
                var systemPrompt = buildSystemPrompt(speaker, renderer);
                var init = AgentSessionInit.of(systemPrompt);
                sessions.put(speaker.name(), agentProvider.openSession(init));
            }

            for (int turn = 0; turn < maxTurns; turn++) {
                var speaker = speakers.get(turn % speakers.size());
                var agentId = speaker.name();

                cognition.tick(agentId, tenantId, speaker);

                var userPrompt = buildUserPrompt(history, agentId);
                var session = sessions.get(agentId);

                String response = session.query(userPrompt)
                        .filter(e -> e instanceof AgentEvent.TextDelta)
                        .map(e -> ((AgentEvent.TextDelta) e).text())
                        .collect().with(Collectors.joining())
                        .await().atMost(Duration.ofSeconds(300));

                var turnRecord = new Turn(turn + 1, agentId, speaker.name(), response);
                history.add(turnRecord);

                printTurn(turnRecord);
                printCognitionState(agentId);
            }
        } finally {
            sessions.values().forEach(AgentSession::close);
        }

        return new ConversationResult(history, Duration.between(start, Instant.now()));
    }

    private String buildSystemPrompt(AgentDescriptor speaker, AffordanceRenderer renderer) {
        var sb = new StringBuilder();
        sb.append(speaker.briefing()).append("\n\n");

        var context = new PromptContext(speaker.name(), tenantId, null);
        for (PromptSection section : cognition.promptSections()) {
            var contribution = section.contribute(context);
            if (contribution != null && !contribution.isBlank()) {
                sb.append(contribution).append("\n\n");
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

    private String buildUserPrompt(List<Turn> history, String currentAgent) {
        if (history.isEmpty()) {
            return "The conversation begins. Introduce yourself and share what fascinates you most about the other person's work.";
        }
        var sb = new StringBuilder();
        var lastTurn = history.get(history.size() - 1);
        sb.append(lastTurn.speakerName()).append(" said:\n\n");
        sb.append(lastTurn.dialogue());
        sb.append("\n\nRespond in character.");
        return sb.toString();
    }

    private void printTurn(Turn turn) {
        System.out.printf("%n=== Turn %d — %s ===%n", turn.number(), turn.speakerName());
        System.out.printf("[dialogue] %s%n", turn.dialogue());
    }

    private void printCognitionState(String agentId) {
        cognition.mood().currentMood(agentId, tenantId).ifPresent(mood ->
                System.out.printf("[mood] pleasure: %.2f  arousal: %.2f  dominance: %.2f%n",
                        mood.pleasure(), mood.arousal(), mood.dominance()));

        cognition.drives().currentDrives(agentId, tenantId).ifPresent(drives ->
                System.out.printf("[drives] dominant: %s  composite: %.2f%n",
                        drives.dominantDrive(), drives.compositeMotivation()));
    }

    public record Turn(int number, String agentId, String speakerName, String dialogue) {}

    public record ConversationResult(List<Turn> turns, Duration elapsed) {
        public int turnCount() { return turns.size(); }
    }

    public static final class Builder {
        private AgentProvider agentProvider;
        private CognitionStack cognition;
        private CompiledWorld world;
        private List<AgentDescriptor> descriptors;
        private int maxTurns = 6;
        private String tenantId = "showcase";

        public Builder agentProvider(AgentProvider p) { this.agentProvider = p; return this; }
        public Builder cognition(CognitionStack c) { this.cognition = c; return this; }
        public Builder world(CompiledWorld w) { this.world = w; return this; }
        public Builder descriptors(List<AgentDescriptor> d) { this.descriptors = d; return this; }
        public Builder maxTurns(int n) { this.maxTurns = n; return this; }
        public Builder tenantId(String t) { this.tenantId = t; return this; }

        public ConversationRunner build() {
            if (agentProvider == null) throw new IllegalStateException("agentProvider required");
            if (cognition == null) throw new IllegalStateException("cognition required");
            if (descriptors == null || descriptors.isEmpty()) throw new IllegalStateException("descriptors required");
            return new ConversationRunner(agentProvider, cognition, world, descriptors, maxTurns, tenantId);
        }
    }
}
