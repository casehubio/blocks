package io.casehub.blocks.agentic.decomposition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.blocks.agentic.AgentCardSupport;
import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.smallrye.mutiny.Uni;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LlmDecomposition<T> implements DecompositionStrategy<T> {

    private static final System.Logger LOG = System.getLogger(LlmDecomposition.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            You are a task planner. Given a goal, current state, and available agents, \
            decompose the goal into a sequence of agent tasks.

            Respond with JSON only: [{"agent": "<name>", "task": "<description>", "rationale": "<why>"}]

            Each step should be a concrete action assigned to the agent best suited for it. \
            Order matters — steps execute sequentially.""";

    private final AgentProvider agentProvider;
    private final Function<T, String> stateRenderer;

    public LlmDecomposition(AgentProvider agentProvider, Function<T, String> stateRenderer) {
        this.agentProvider = agentProvider;
        this.stateRenderer = stateRenderer;
    }

    public LlmDecomposition(AgentProvider agentProvider) {
        this(agentProvider, Object::toString);
    }

    @Override
    public Uni<List<TaskNode<T>>> decompose(TaskNode<T> compound,
                                            DecompositionContext<T> context) {
        if (!(compound instanceof TaskNode.CompoundTask<T> goal)) {
            return Uni.createFrom().item(List.of(compound));
        }

        return Uni.createFrom().item(() -> {
            try {
                var userPrompt = buildUserPrompt(goal, context);
                var config = AgentSessionConfig.of(SYSTEM_PROMPT, userPrompt);

                var text = agentProvider.invoke(config)
                        .filter(e -> e instanceof AgentEvent.TextDelta)
                        .map(e -> ((AgentEvent.TextDelta) e).text())
                        .collect().with(Collectors.joining())
                        .await().indefinitely();

                return parseResponse(text, context.agents());
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING, "LLM decomposition failed", e);
                return List.<TaskNode<T>>of();
            }
        });
    }

    private String buildUserPrompt(TaskNode.CompoundTask<T> goal,
                                   DecompositionContext<T> context) {
        var sb = new StringBuilder();
        sb.append("Goal: ").append(goal.name()).append("\n\n");

        if (context.state() != null) {
            var stateStr = stateRenderer.apply(context.state());
            if (stateStr != null && !stateStr.isBlank()) {
                sb.append("Current state:\n").append(stateStr).append("\n\n");
            }
        }

        sb.append("Available agents:\n");
        for (int i = 0; i < context.agents().size(); i++) {
            sb.append(AgentCardSupport.buildCard(context.agents().get(i), i)).append("\n");
        }
        return sb.toString();
    }

    private List<TaskNode<T>> parseResponse(@Nullable String text,
                                            List<RoutingCandidate> agents) {
        if (text == null || text.isBlank()) return List.of();

        var trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start >= 0 && end > start) {
                trimmed = trimmed.substring(start + 1, end).trim();
            }
        }

        try {
            var root = MAPPER.readTree(trimmed);
            if (!root.isArray()) return List.of();

            var result = new ArrayList<TaskNode<T>>();
            for (JsonNode node : root) {
                var agentName = node.has("agent") ? node.get("agent").asText() : null;
                var task = node.has("task") ? node.get("task").asText() : null;
                var rationale = node.has("rationale") ? node.get("rationale").asText() : null;

                if (agentName == null || task == null) continue;

                var agentRef = resolveAgent(agentName, agents);
                if (agentRef == null) {
                    LOG.log(System.Logger.Level.WARNING,
                            "LLM named unknown agent ''{0}'' — skipping step", agentName);
                    continue;
                }

                result.add(new TaskNode.PlannedTask<>(task, agentRef, rationale));
            }
            return result;
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "Failed to parse LLM decomposition response", e);
            return List.of();
        }
    }

    private static @Nullable AgentRef resolveAgent(String name,
                                                   List<RoutingCandidate> agents) {
        for (int i = 0; i < agents.size(); i++) {
            if (name.equals(AgentCardSupport.candidateName(agents.get(i), i))) {
                return agents.get(i).ref();
            }
        }
        for (int i = 0; i < agents.size(); i++) {
            var candidate = agents.get(i);
            if (candidate.ref() instanceof AgentRef.WorkerAgent w
                    && name.equals(w.worker().name())) {
                return candidate.ref();
            }
        }
        return null;
    }
}
