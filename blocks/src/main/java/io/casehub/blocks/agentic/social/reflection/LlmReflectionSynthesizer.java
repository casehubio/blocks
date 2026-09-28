package io.casehub.blocks.agentic.social.reflection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.experience.ExperienceAttributeKeys;
import io.casehub.neocortex.memory.reflection.ReflectionEvent;
import io.casehub.neocortex.memory.reflection.ReflectionQuery;
import io.casehub.neocortex.memory.reflection.ReflectionSynthesizer;
import io.casehub.neocortex.memory.reflection.runtime.Trajectory;
import io.casehub.neocortex.memory.reflection.runtime.TrajectoryGrouper;
import io.casehub.neocortex.memory.reflection.runtime.TrajectoryOutcome;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import jakarta.enterprise.inject.Alternative;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Alternative
@Priority(1)
@ApplicationScoped
public class LlmReflectionSynthesizer implements ReflectionSynthesizer {

    private static final Logger LOG = Logger.getLogger(LlmReflectionSynthesizer.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_EXISTING_REFLECTIONS = 20;

    private static final String SYSTEM_PROMPT = """
        You are a reflection agent. Analyse experience trajectories and extract \
        actionable heuristics as conditional rules.

        Respond with JSON:
        {
          "heuristics": [
            {
              "condition": "description of the situation/trigger",
              "action": "what to do or avoid",
              "source_cases": ["case-id-1"],
              "source_turns": ["turn-id-1", "turn-id-2"]
            }
          ]
        }

        Rules:
        - Extract 1-5 heuristics (fewer is better than vague)
        - Each heuristic must be a conditional rule: "when X, do/avoid Y"
        - Prioritise failure-derived heuristics — what went wrong and how to avoid it
        - Each heuristic must reference at least one source case and turn
        - Do not extract heuristics that are obvious or trivially true
        - Do not repeat existing insights listed at the end of the input
        - Respond with valid JSON only""";

    private final Instance<AgentProvider> agentProviderInstance;
    private final Instance<CaseMemoryStore> storeInstance;

    @Inject
    public LlmReflectionSynthesizer(Instance<AgentProvider> agentProviderInstance,
                                     Instance<CaseMemoryStore> storeInstance) {
        this.agentProviderInstance = agentProviderInstance;
        this.storeInstance = storeInstance;
    }

    @Override
    public List<ReflectionEvent> synthesize(String agentId, String tenantId,
                                             List<Memory> sources, int targetLevel) {
        if (agentProviderInstance.isUnsatisfied()) return List.of();
        if (sources.isEmpty()) return List.of();

        var trajectories = TrajectoryGrouper.group(sources);
        if (trajectories.isEmpty()) return List.of();

        var turnIndex = buildTurnIndex(sources);
        var trajectoryOutcomeIndex = buildTrajectoryOutcomeIndex(trajectories);
        var existingReflections = loadExistingReflections(agentId, tenantId);

        String userPrompt = buildUserPrompt(trajectories, existingReflections);

        try {
            String llmResponse = invokeLlm(userPrompt);
            if (llmResponse == null) return List.of();
            return parseResponse(llmResponse, agentId, tenantId, targetLevel,
                turnIndex, trajectoryOutcomeIndex);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "LLM reflection synthesis failed", e);
            return List.of();
        }
    }

    private String invokeLlm(String userPrompt) {
        var provider = agentProviderInstance.get();
        var events = provider.invoke(AgentSessionConfig.of(SYSTEM_PROMPT, userPrompt))
            .collect().asList()
            .await().atMost(Duration.ofMinutes(2));

        boolean hasError = events.stream()
            .filter(AgentEvent.InvocationComplete.class::isInstance)
            .map(AgentEvent.InvocationComplete.class::cast)
            .anyMatch(AgentEvent.InvocationComplete::isError);
        if (hasError) {
            LOG.warning("LLM reflection invocation completed with error flag");
            return null;
        }

        String text = events.stream()
            .filter(AgentEvent.TextDelta.class::isInstance)
            .map(AgentEvent.TextDelta.class::cast)
            .map(AgentEvent.TextDelta::text)
            .collect(Collectors.joining());

        return text.isEmpty() ? null : text;
    }

    private Map<String, Set<String>> buildTurnIndex(List<Memory> sources) {
        Map<String, Set<String>> index = new HashMap<>();
        for (var mem : sources) {
            String turnId = mem.attributes().get(ExperienceAttributeKeys.TURN_ID);
            if (turnId != null) {
                index.computeIfAbsent(turnId, k -> new HashSet<>()).add(mem.memoryId());
            }
        }
        return index;
    }

    private Map<String, TrajectoryOutcome> buildTrajectoryOutcomeIndex(List<Trajectory> trajectories) {
        Map<String, TrajectoryOutcome> index = new HashMap<>();
        for (var t : trajectories) {
            index.put(t.caseId(), t.outcome());
        }
        return index;
    }

    private List<String> loadExistingReflections(String agentId, String tenantId) {
        if (storeInstance == null || !storeInstance.isResolvable()) return List.of();
        try {
            var query = ReflectionQuery.forAgent(agentId, tenantId)
                .withLimit(MAX_EXISTING_REFLECTIONS);
            return storeInstance.get().query(query).stream()
                .map(Memory::text)
                .toList();
        } catch (Exception e) {
            LOG.log(Level.FINE, "Could not load existing reflections for dedup", e);
            return List.of();
        }
    }

    private String buildUserPrompt(List<Trajectory> trajectories,
                                    List<String> existingReflections) {
        var sb = new StringBuilder();
        sb.append("## Experience Trajectories\n\n");

        for (int i = 0; i < trajectories.size(); i++) {
            var t = trajectories.get(i);
            sb.append("### Trajectory ").append(i + 1)
              .append(" [case: ").append(t.caseId())
              .append(", classification: ").append(t.outcome().name())
              .append("]\n\n");

            for (var step : t.steps()) {
                for (var mem : step.events()) {
                    String eventType = mem.attributes().get(ExperienceAttributeKeys.EVENT_TYPE);
                    sb.append("- [").append(eventType != null ? eventType : "unknown")
                      .append("]");
                    if (step.turnId() != null) sb.append(" (turn: ").append(step.turnId()).append(")");
                    sb.append(" ").append(mem.text());

                    String status = mem.attributes().get(ExperienceAttributeKeys.OUTCOME_STATUS);
                    if (status != null) sb.append(" [status: ").append(status).append("]");

                    String result = mem.attributes().get(ExperienceAttributeKeys.RESULT);
                    if (result != null) sb.append(" [result: ").append(result).append("]");

                    sb.append("\n");
                }
            }
            sb.append("\n");
        }

        if (!existingReflections.isEmpty()) {
            sb.append("## Already Known Insights — Do Not Repeat\n\n");
            for (var insight : existingReflections) {
                sb.append("- ").append(insight).append("\n");
            }
        }

        return sb.toString();
    }

    List<ReflectionEvent> parseResponse(String json, String agentId, String tenantId,
                                         int targetLevel, Map<String, Set<String>> turnIndex,
                                         Map<String, TrajectoryOutcome> trajectoryOutcomeIndex) {
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            LOG.warning("Malformed JSON from LLM reflection synthesis: " + e.getMessage());
            return List.of();
        }

        JsonNode heuristics = root.get("heuristics");
        if (heuristics == null || !heuristics.isArray()) return List.of();

        List<ReflectionEvent> events = new ArrayList<>();
        for (var node : heuristics) {
            var event = parseHeuristic(node, agentId, tenantId, targetLevel,
                turnIndex, trajectoryOutcomeIndex);
            if (event != null) events.add(event);
        }
        return events;
    }

    private ReflectionEvent parseHeuristic(JsonNode node, String agentId, String tenantId,
                                            int targetLevel, Map<String, Set<String>> turnIndex,
                                            Map<String, TrajectoryOutcome> trajectoryOutcomeIndex) {
        JsonNode conditionNode = node.get("condition");
        JsonNode actionNode = node.get("action");
        if (conditionNode == null || actionNode == null) return null;

        String condition = conditionNode.asText();
        String action = actionNode.asText();
        if (condition.isBlank() || action.isBlank()) return null;

        Set<String> sourceMemoryIds = new HashSet<>();
        JsonNode sourceTurns = node.get("source_turns");
        if (sourceTurns != null && sourceTurns.isArray()) {
            for (var turnNode : sourceTurns) {
                String turnId = turnNode.asText();
                Set<String> memIds = turnIndex.get(turnId);
                if (memIds != null) sourceMemoryIds.addAll(memIds);
            }
        }

        if (sourceMemoryIds.isEmpty()) return null;

        String derivation = "neutral";
        JsonNode sourceCases = node.get("source_cases");
        if (sourceCases != null && sourceCases.isArray()) {
            for (var caseNode : sourceCases) {
                TrajectoryOutcome outcome = trajectoryOutcomeIndex.get(caseNode.asText());
                if (outcome == TrajectoryOutcome.FAILURE) { derivation = "failure"; break; }
                if (outcome == TrajectoryOutcome.SUCCESS) derivation = "success";
            }
        }

        String insight = "When " + condition + ", " + action;

        return new ReflectionEvent(agentId, tenantId, null, Instant.now(), insight,
            targetLevel, List.copyOf(sourceMemoryIds), null,
            Map.of("derivation", derivation));
    }
}
