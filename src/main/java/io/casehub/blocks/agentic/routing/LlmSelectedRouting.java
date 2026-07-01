package io.casehub.blocks.agentic.routing;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.smallrye.mutiny.Uni;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * LLM-driven agent selection — asks a language model to pick the best agent
 * from a list of candidates for a given task.
 *
 * <p>The type parameter {@code T} flows typed through the entire execution
 * pipeline. The {@code stateRenderer} converts {@code T} to {@code String}
 * only at the LLM API boundary, preserving type safety everywhere else.
 *
 * @param <T> the execution state type
 */
public class LlmSelectedRouting<T> implements RoutingStrategy<T> {

    private static final System.Logger LOG = System.getLogger(LlmSelectedRouting.class.getName());

    private static final String SYSTEM_PROMPT = """
            You are an agent router. Given a task description and a list of available agents, \
            select the single best agent to handle the task.

            Respond with JSON only: {"agent": "<agent-name>", "reason": "<one sentence>"}

            If the task is already complete and no agent is needed, respond: {"agent": "done", "reason": "<why>"}

            Select based on the agent's capabilities, briefing, and domain expertise. \
            Choose the agent whose skills most closely match the task requirements.""";

    private final AgentProvider agentProvider;
    private final Function<T, String> stateRenderer;

    /**
     * Creates an LLM-selected routing strategy.
     *
     * @param agentProvider the LLM provider for agent selection
     * @param stateRenderer converts typed state {@code T} to a String representation
     *                      for the LLM prompt — the only point where T is converted
     *                      to String
     */
    public LlmSelectedRouting(AgentProvider agentProvider, Function<T, String> stateRenderer) {
        this.agentProvider = agentProvider;
        this.stateRenderer = stateRenderer;
    }

    /**
     * Convenience constructor — defaults stateRenderer to {@code Object::toString}.
     */
    public LlmSelectedRouting(AgentProvider agentProvider) {
        this(agentProvider, Object::toString);
    }

    @Override
    public Uni<RoutingDecision> route(RoutingContext<T> context) {
        return Uni.createFrom().item(() -> {
            try {
                var userPrompt = buildUserPrompt(context);
                var config = AgentSessionConfig.of(SYSTEM_PROMPT, userPrompt);

                var text = agentProvider.invoke(config)
                        .filter(e -> e instanceof AgentEvent.TextDelta)
                        .map(e -> ((AgentEvent.TextDelta) e).text())
                        .collect().with(Collectors.joining())
                        .await().indefinitely();

                return parseSelection(text, context.candidates());
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING, "LLM routing failed", e);
                return new RoutingDecision.Unresolvable("LLM routing failed: " + e.getMessage());
            }
        });
    }

    private String buildUserPrompt(RoutingContext<T> context) {
        var sb = new StringBuilder();
        sb.append("Task: ").append(context.task()).append("\n\n");

        if (context.state() != null) {
            sb.append("Current state:\n").append(stateRenderer.apply(context.state())).append("\n\n");
        }

        sb.append("Available agents:\n");
        for (int i = 0; i < context.candidates().size(); i++) {
            sb.append(buildAgentCard(context.candidates().get(i), i)).append("\n");
        }
        return sb.toString();
    }

    private String buildAgentCard(RoutingCandidate candidate, int index) {
        var sb = new StringBuilder();
        var name = candidateName(candidate, index);
        sb.append("- Agent: \"").append(name).append("\"");

        if (candidate.ref() instanceof AgentRef.WorkerAgent w) {
            var desc = w.worker().description();
            if (desc != null && !desc.isBlank()) {
                sb.append("\n  Description: ").append(desc);
            }
        }

        var descriptor = candidate.descriptor();
        if (descriptor != null) {
            if (descriptor.briefing() != null && !descriptor.briefing().isBlank()) {
                sb.append("\n  Briefing: ").append(descriptor.briefing());
            }
            if (descriptor.capabilities() != null && !descriptor.capabilities().isEmpty()) {
                var caps = descriptor.capabilities().stream()
                        .map(c -> {
                            var capStr = c.name();
                            if (c.epistemicDomains() != null && !c.epistemicDomains().isEmpty()) {
                                capStr += " (domains: " + String.join(", ", c.epistemicDomains().keySet()) + ")";
                            }
                            return capStr;
                        })
                        .collect(Collectors.joining(", "));
                sb.append("\n  Capabilities: ").append(caps);
            }
            if (descriptor.slot() != null && !descriptor.slot().isBlank()) {
                sb.append("\n  Slot: ").append(descriptor.slot());
            }
        }
        return sb.toString();
    }

    static String candidateName(RoutingCandidate candidate, int index) {
        if (candidate.descriptor() != null && candidate.descriptor().name() != null) {
            return candidate.descriptor().name();
        }
        if (candidate.ref() instanceof AgentRef.WorkerAgent w) {
            return w.worker().name();
        }
        return candidate.ref().getClass().getSimpleName() + "-" + index;
    }

    private RoutingDecision parseSelection(String text, List<RoutingCandidate> candidates) {
        var agentName = extractAgentName(text);
        if (agentName == null) {
            return new RoutingDecision.Unresolvable("Could not parse agent name from LLM response: " + text);
        }
        if ("done".equalsIgnoreCase(agentName)) {
            return new RoutingDecision.Unresolvable("LLM indicated task is complete");
        }
        var reason = extractReason(text);
        for (int i = 0; i < candidates.size(); i++) {
            var name = candidateName(candidates.get(i), i);
            if (name.equals(agentName)) {
                return new RoutingDecision.Selected(List.of(candidates.get(i).ref()), reason);
            }
        }
        return new RoutingDecision.Unresolvable("LLM selected unknown agent: " + agentName);
    }

    private static String extractAgentName(String text) {
        if (text == null) return null;
        var trimmed = text.trim();
        int agentIdx = trimmed.indexOf("\"agent\"");
        if (agentIdx < 0) return null;
        int colonIdx = trimmed.indexOf(':', agentIdx);
        if (colonIdx < 0) return null;
        int firstQuote = trimmed.indexOf('"', colonIdx + 1);
        if (firstQuote < 0) return null;
        int secondQuote = trimmed.indexOf('"', firstQuote + 1);
        if (secondQuote < 0) return null;
        return trimmed.substring(firstQuote + 1, secondQuote);
    }

    static String extractReason(String text) {
        if (text == null) return null;
        var trimmed = text.trim();
        int reasonIdx = trimmed.indexOf("\"reason\"");
        if (reasonIdx < 0) return null;
        int colonIdx = trimmed.indexOf(':', reasonIdx);
        if (colonIdx < 0) return null;
        int firstQuote = trimmed.indexOf('"', colonIdx + 1);
        if (firstQuote < 0) return null;
        int secondQuote = trimmed.indexOf('"', firstQuote + 1);
        if (secondQuote < 0) return null;
        return trimmed.substring(firstQuote + 1, secondQuote);
    }
}
