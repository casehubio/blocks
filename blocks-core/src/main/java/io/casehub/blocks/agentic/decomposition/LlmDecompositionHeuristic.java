package io.casehub.blocks.agentic.decomposition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.engine.plan.DecompositionContext;
import io.casehub.engine.plan.DecompositionMethod;
import io.casehub.engine.plan.TaskNode;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LlmDecompositionHeuristic<T> implements DecompositionHeuristic<T> {

    private static final System.Logger LOG = System.getLogger(LlmDecompositionHeuristic.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            You are evaluating decomposition methods for an HTN planner. \
            Given a task, its current state, and a list of eligible decomposition methods, \
            score each method 0.0-1.0 based on how well it fits the current state and goal.

            Respond with JSON only: [{"method": 1, "score": 0.8}, {"method": 2, "score": 0.4}]""";

    private final AgentProvider agentProvider;
    private final Function<T, String> stateRenderer;

    public LlmDecompositionHeuristic(AgentProvider agentProvider) {
        this(agentProvider, Object::toString);
    }

    public LlmDecompositionHeuristic(AgentProvider agentProvider, Function<T, String> stateRenderer) {
        this.agentProvider = Objects.requireNonNull(agentProvider, "agentProvider");
        this.stateRenderer = Objects.requireNonNull(stateRenderer, "stateRenderer");
    }

    @Override
    public List<ScoredMethod<T>> evaluate(TaskNode.CompoundTask<T> task,
                                          List<DecompositionMethod<T>> methods,
                                          DecompositionContext<T> context) {
        try {
            var prompt = buildPrompt(task, methods, context);
            var config = AgentSessionConfig.of(SYSTEM_PROMPT, prompt);

            var text = agentProvider.invoke(config)
                                    .filter(e -> e instanceof AgentEvent.TextDelta)
                                    .map(e -> ((AgentEvent.TextDelta) e).text())
                                    .collect().with(Collectors.joining())
                                    .await().indefinitely();

            return parseResponse(text, methods);
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING,
                    "LLM heuristic evaluation failed — falling back to equal scores", e);
            return methods.stream()
                          .map(m -> new ScoredMethod<>(m, 0.0))
                          .toList();
        }
    }

    private String buildPrompt(TaskNode.CompoundTask<T> task,
                                List<DecompositionMethod<T>> methods,
                                DecompositionContext<T> context) {
        var sb = new StringBuilder();
        sb.append("Task: \"").append(task.name()).append("\"\n\n");

        if (context.state() != null) {
            var stateStr = stateRenderer.apply(context.state());
            if (stateStr != null && !stateStr.isBlank()) {
                sb.append("Current state:\n").append(stateStr).append("\n\n");
            }
        }

        sb.append("Eligible methods:\n");
        for (int i = 0; i < methods.size(); i++) {
            sb.append(i + 1).append(". ").append(describeMethod(methods.get(i))).append("\n");
        }
        return sb.toString();
    }

    private String describeMethod(DecompositionMethod<T> method) {
        if (method.strategy() instanceof SequenceStrategy<T> seq) {
            return seq.children().stream()
                    .map(this::describeNode)
                    .collect(Collectors.joining(" → "));
        }
        return "(compound strategy)";
    }

    private String describeNode(TaskNode<T> node) {
        if (node instanceof TaskNode.CompoundTask<T> ct) return ct.name();
        if (node instanceof PlannedTask<T> pt) return pt.description();
        if (node instanceof PrimitiveTask<T> pt) return pt.description() != null ? pt.description() : "(primitive)";
        return "(task)";
    }

    private List<ScoredMethod<T>> parseResponse(String text, List<DecompositionMethod<T>> methods) {
        double[] scores = new double[methods.size()];

        if (text != null && !text.isBlank()) {
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
                if (root.isArray()) {
                    for (JsonNode node : root) {
                        int methodIdx = node.has("method") ? node.get("method").asInt() - 1 : -1;
                        double score = node.has("score") ? node.get("score").asDouble() : 0.0;
                        if (methodIdx >= 0 && methodIdx < scores.length) {
                            scores[methodIdx] = score;
                        }
                    }
                }
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING, "Failed to parse LLM heuristic response", e);
            }
        }

        var result = new ArrayList<ScoredMethod<T>>(methods.size());
        for (int i = 0; i < methods.size(); i++) {
            result.add(new ScoredMethod<>(methods.get(i), scores[i]));
        }
        return result;
    }
}
