package io.casehub.blocks.agentic.decomposition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.blocks.agentic.AgentCardSupport;
import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.prompt.PromptVariantStore;
import io.casehub.blocks.prompt.SystemPromptCustomiser;
import io.casehub.blocks.prompt.VariantSelector;
import io.casehub.engine.plan.DagPlan;
import io.casehub.engine.plan.DecompositionContext;
import io.casehub.engine.plan.DecompositionStrategy;
import io.casehub.engine.plan.TaskNode;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LlmDecomposition<T> implements DecompositionStrategy<T> {

    private static final System.Logger LOG    = System.getLogger(LlmDecomposition.class.getName());
    private static final ObjectMapper  MAPPER = new ObjectMapper();

    private static final String FLAT_SYSTEM_PROMPT = """
                                                     You are a task planner. Given a goal, current state, and available agents, \
                                                     decompose the goal into a sequence of agent tasks.
                                                     
                                                     Respond with JSON only: [{"agent": "<name>", "task": "<description>", "rationale": "<why>"}]
                                                     
                                                     Each step should be a concrete action assigned to the agent best suited for it. \
                                                     Order matters — steps execute sequentially.""";

    private static final String RECURSIVE_SYSTEM_PROMPT = """
                                                          You are a task planner. Given a goal, current state, and available agents, \
                                                          decompose the goal into steps. Each step is either:
                                                          - A concrete task assigned to an agent
                                                          - A subtask that needs further decomposition
                                                          
                                                          Respond with JSON only. Each entry is one of:
                                                            {"agent": "<name>", "task": "<description>", "rationale": "<why>"}
                                                            {"subtask": "<name>", "description": "<what needs to be done>"}
                                                          
                                                          Use subtasks for complex parts that need multi-step planning. \
                                                          Use agent assignments for concrete actions an agent can execute directly. \
                                                          Order matters — steps execute sequentially.""";

    private final AgentProvider       agentProvider;
    private final Function<T, String> stateRenderer;
    private final int                 maxDepth;
    private final @Nullable SystemPromptCustomiser systemPromptCustomiser;
    private final @Nullable VariantSelector variantSelector;
    private final @Nullable PromptVariantStore variantStore;


    public LlmDecomposition(AgentProvider agentProvider, Function<T, String> stateRenderer,
                            int maxDepth, @Nullable SystemPromptCustomiser systemPromptCustomiser,
                            @Nullable VariantSelector variantSelector,
                            @Nullable PromptVariantStore variantStore) {
        if (maxDepth < 1) {
            throw new IllegalArgumentException("maxDepth must be >= 1, got " + maxDepth);
        }
        this.agentProvider          = agentProvider;
        this.stateRenderer          = stateRenderer;
        this.maxDepth               = maxDepth;
        this.systemPromptCustomiser = systemPromptCustomiser;
        this.variantSelector        = variantSelector;
        this.variantStore           = variantStore;
    }

    public LlmDecomposition(AgentProvider agentProvider, Function<T, String> stateRenderer,
                            int maxDepth) {
        this(agentProvider, stateRenderer, maxDepth, null, null, null);
    }

    public LlmDecomposition(AgentProvider agentProvider, Function<T, String> stateRenderer) {
        this(agentProvider, stateRenderer, 1);
    }

    public LlmDecomposition(AgentProvider agentProvider, int maxDepth) {
        this(agentProvider, Object::toString, maxDepth);
    }

    public LlmDecomposition(AgentProvider agentProvider) {
        this(agentProvider, Object::toString, 1);
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

    @Override
    public DagPlan<TaskNode.LeafTask<T>> decompose(TaskNode<T> compound,
                                                   DecompositionContext<T> context) {
        if (!(compound instanceof TaskNode.CompoundTask<T> goal)) {
            if (compound instanceof TaskNode.LeafTask<T> leaf) {
                return DagPlan.singleton(leaf);
            }
            throw new IllegalStateException("Unexpected TaskNode type: " + compound.getClass());
        }

        var agenticCtx = (AgenticDecompositionContext<T>) context;
        try {
            var systemPrompt = selectSystemPrompt(agenticCtx.depth());
            var userPrompt   = buildUserPrompt(goal, agenticCtx);
            var config       = AgentSessionConfig.of(systemPrompt, userPrompt);

            var text = agentProvider.invoke(config)
                                    .filter(e -> e instanceof AgentEvent.TextDelta)
                                    .map(e -> ((AgentEvent.TextDelta) e).text())
                                    .collect().with(Collectors.joining())
                                    .await().indefinitely();

            return resolveEntries(text, goal.name(), agenticCtx);
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING,
                    "LLM decomposition failed for ''{0}'' at depth {1}",
                    goal.name(), agenticCtx.depth());
            throw e;
        }
    }

    private String selectSystemPrompt(int currentDepth) {
        var base = currentDepth < maxDepth - 1 ? RECURSIVE_SYSTEM_PROMPT : FLAT_SYSTEM_PROMPT;
        if (systemPromptCustomiser != null) {
            return systemPromptCustomiser.customise(base, "llm-decomposition", "control");
        }
        return base;}

    private String buildUserPrompt(TaskNode.CompoundTask<T> goal,
                                   AgenticDecompositionContext<T> context) {
        var sb = new StringBuilder();

        if (context.parentGoal() != null) {
            sb.append("Parent goal: ").append(context.parentGoal()).append("\n");
        }
        if (context.siblingNames() != null && !context.siblingNames().isEmpty()) {
            sb.append("Sibling tasks: ").append(String.join(", ", context.siblingNames())).append("\n");
        }

        sb.append("Goal: ").append(goal.name()).append("\n");

        if (context.subtaskDescription() != null) {
            sb.append("Description: ").append(context.subtaskDescription()).append("\n");
        }

        sb.append("\n");

        if (context.staticFailureHint() != null) {
            sb.append("Note: ").append(context.staticFailureHint()).append("\n\n");
        }

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

        appendFewShotExamples(sb);

        return sb.toString();}

    private void appendFewShotExamples(StringBuilder sb) {
        if (variantStore == null || variantSelector == null) {return;}
        var slot    = "control";
        var variant = variantStore.getActive("llm-decomposition", slot);
        if (variant == null || variant.examples().isEmpty()) {return;}
        sb.append("\nSuccessful decomposition examples from similar past cases:\n");
        for (int i = 0; i < variant.examples().size(); i++) {
            var ex = variant.examples().get(i);
            sb.append("\n").append(i + 1).append(". ").append(ex.input())
              .append("\n   Plan: ").append(ex.output())
              .append("\n   Outcome: ").append(ex.outcome());
        }
        sb.append("\n");
    }


    private DagPlan<TaskNode.LeafTask<T>> resolveEntries(@Nullable String text, String taskName,
                                                         AgenticDecompositionContext<T> ctx) {
        if (text == null || text.isBlank()) {
            throw new IllegalStateException(
                    "LLM returned empty plan for '" + taskName + "' at depth " + ctx.depth());
        }

        var trimmed = stripCodeFence(text);

        try {
            var root = MAPPER.readTree(trimmed);
            if (!root.isArray()) {
                throw new IllegalStateException(
                        "LLM returned empty plan for '" + taskName + "' at depth " + ctx.depth());
            }

            var entryNames = collectEntryNames(root);
            var subPlans   = new ArrayList<DagPlan<TaskNode.LeafTask<T>>>();

            for (JsonNode node : root) {
                if (node.has("agent")) {
                    var leaf = resolveAgentEntry(node, taskName, ctx);
                    if (leaf != null) {
                        subPlans.add(DagPlan.singleton(leaf));
                    }
                } else if (node.has("subtask")) {
                    var subPlan = resolveSubtaskEntry(node, taskName, ctx, entryNames);
                    if (subPlan != null) {
                        subPlans.add(subPlan);
                    }
                }
            }

            if (subPlans.isEmpty()) {
                throw new IllegalStateException(
                        "LLM returned empty plan for '" + taskName + "' at depth " + ctx.depth());
            }

            return subPlans.size() == 1 ? subPlans.get(0) : DagPlan.sequentialMerge(subPlans);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING,
                    "Failed to parse LLM decomposition response for ''{0}'' at depth {1}",
                    taskName, ctx.depth());
            throw new IllegalStateException(
                    "LLM returned empty plan for '" + taskName + "' at depth " + ctx.depth(), e);
        }
    }

    private TaskNode.@Nullable LeafTask<T> resolveAgentEntry(JsonNode node, String taskName,
                                                             AgenticDecompositionContext<T> ctx) {
        var agentName = node.get("agent").asText();
        var task      = node.has("task") ? node.get("task").asText() : null;
        var rationale = node.has("rationale") ? node.get("rationale").asText() : null;

        if (task == null) {return null;}

        var agentRef = resolveAgent(agentName, ctx.agents());
        if (agentRef == null) {
            LOG.log(System.Logger.Level.WARNING,
                    "LLM named unknown agent ''{0}'' for ''{1}'' at depth {2} — skipping step",
                    agentName, taskName, ctx.depth());
            return null;
        }

        return new PlannedTask<>(UUID.randomUUID().toString(), Instant.now(),
                                 task, agentRef, rationale);
    }

    private @Nullable DagPlan<TaskNode.LeafTask<T>> resolveSubtaskEntry(
            JsonNode node, String taskName, AgenticDecompositionContext<T> ctx,
            List<String> siblingNames) {
        var subtaskName = node.get("subtask").asText();
        var description = node.has("description") ? node.get("description").asText() : null;

        if (ctx.depth() + 1 >= maxDepth) {
            LOG.log(System.Logger.Level.WARNING,
                    "Subtask ''{0}'' at depth {1} exceeds maxDepth {2} — skipping",
                    subtaskName, ctx.depth(), maxDepth);
            return null;
        }

        var subCompound = new TaskNode.CompoundTask<T>(UUID.randomUUID().toString(), subtaskName, List.of());
        var subCtx = new AgenticDecompositionContext<>(
                ctx.state(), ctx.agents(), ctx.depth() + 1, null,
                description, taskName, siblingNames, null,
                ctx.planningConstraints());

        return this.decompose(subCompound, subCtx);
    }

    private static List<String> collectEntryNames(JsonNode root) {
        var names = new ArrayList<String>();
        for (JsonNode node : root) {
            if (node.has("agent") && node.has("task")) {
                names.add(node.get("task").asText());
            } else if (node.has("subtask")) {
                names.add(node.get("subtask").asText());
            }
        }
        return List.copyOf(names);
    }

    private static String stripCodeFence(String text) {
        var trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end   = trimmed.lastIndexOf("```");
            if (start >= 0 && end > start) {
                return trimmed.substring(start + 1, end).trim();
            }
        }
        return trimmed;
    }
}
