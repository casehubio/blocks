package io.casehub.blocks.agentic.social.goal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.eidos.api.AgentGoal;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.stream.Collectors;

public class LlmDriveGoalFormationStrategy implements DriveGoalFormationStrategy {

    private static final String SYSTEM_PROMPT = """
                                                You are a motivational drive analyst for an autonomous agent. Given a drive \
                                                evaluation summary — an intrinsic motivation signal along a specific axis — \
                                                propose a concrete, actionable goal that addresses the underlying need. \
                                                The goal must be specific to the evaluation context, distinct from existing \
                                                goals, and achievable within the agent's capabilities. \
                                                Respond with JSON only: {"goalName": "kebab-case-name", \
                                                "goalDescription": "one sentence describing the goal", \
                                                "formationReason": "brief rationale linking the drive signal to this goal"}. \
                                                If no meaningful goal can be formed from the signal, respond with \
                                                {"goalName": null, "goalDescription": null, "formationReason": null}.""";

    private static final ObjectMapper MAPPER          = new ObjectMapper();
    private static final Duration     DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final AgentProvider agentProvider;

    public LlmDriveGoalFormationStrategy(AgentProvider agentProvider) {
        this.agentProvider = agentProvider;
    }

    @Override
    public @Nullable DriveGoalProposal propose(DriveGoalFormationContext context) {
        String userPrompt = buildPrompt(context);
        var    config     = AgentSessionConfig.of(SYSTEM_PROMPT, userPrompt, DEFAULT_TIMEOUT);

        String response;
        try {
            response = agentProvider.invoke(config)
                                    .filter(e -> e instanceof AgentEvent.TextDelta)
                                    .map(e -> ((AgentEvent.TextDelta) e).text())
                                    .collect().with(Collectors.joining())
                                    .await().indefinitely();
        } catch (Exception e) {
            return null;
        }

        return parseResponse(response, context.axis(), context.intensity());
    }

    private String buildPrompt(DriveGoalFormationContext context) {
        var sb = new StringBuilder();
        sb.append("Drive axis: ").append(context.axis().name()).append('\n');
        sb.append("Intensity: ").append(String.format("%.2f", context.intensity())).append('\n');
        sb.append("Evaluation signal: ").append(context.trigger()).append('\n');
        sb.append("Remaining goal capacity: ").append(context.remainingCapacity()).append('\n');

        if (!context.existingGoals().isEmpty()) {
            sb.append("\nExisting goals (do not duplicate):\n");
            for (AgentGoal goal : context.existingGoals()) {
                sb.append("- ").append(goal.name()).append(": ")
                  .append(goal.description()).append('\n');
            }
        }

        return sb.toString();
    }

    private @Nullable DriveGoalProposal parseResponse(String response, DriveAxis axis,
                                                      double intensity) {
        try {
            JsonNode root = MAPPER.readTree(response);

            if (!root.has("goalName") || root.get("goalName").isNull()) {
                return null;
            }

            String name        = root.get("goalName").asText();
            String description = root.get("goalDescription").asText();
            String reason      = root.get("formationReason").asText();

            if (name.isBlank()) {return null;}

            return new DriveGoalProposal(axis, name, description, reason, intensity);
        } catch (Exception e) {
            return null;
        }
    }
}
