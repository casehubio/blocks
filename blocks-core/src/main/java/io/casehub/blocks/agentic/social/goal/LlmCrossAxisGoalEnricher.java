package io.casehub.blocks.agentic.social.goal;

import io.casehub.blocks.agent.StructuredAgentInvoker;
import io.casehub.blocks.agent.StructuredAgentInvoker.InvocationResult;
import io.casehub.blocks.agentic.social.narrative.DerivedTheme;
import io.casehub.blocks.agentic.social.narrative.NarrativeState;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

public class LlmCrossAxisGoalEnricher implements CrossAxisGoalEnricher {

    private static final String SYSTEM_PROMPT =
            "You are a goal formation analyst. Generate a concise, specific "
            + "compound goal description that combines the given drive axes. "
            + "Respond with a single sentence only.";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final AgentProvider agentProvider;

    public LlmCrossAxisGoalEnricher(AgentProvider agentProvider) {
        this.agentProvider = agentProvider;
    }

    @Override
    public @Nullable DriveGoalProposal enrich(DriveGoalProposal heuristic,
                                               NarrativeState narrative,
                                               DerivedTheme sourceTheme) {
        String prompt = buildPrompt(heuristic, narrative, sourceTheme);
        var config = AgentSessionConfig.of(SYSTEM_PROMPT, prompt, TIMEOUT);

        var result = StructuredAgentInvoker.invokeText(agentProvider, config);
        if (!(result instanceof InvocationResult.Success<String> s)) return null;

        return new DriveGoalProposal(heuristic.axis(), heuristic.goalName(),
                s.value().trim(),
                "LLM-enriched cross-axis: " + sourceTheme.label(),
                heuristic.driveIntensity(), heuristic.suggestedPriority(),
                heuristic.proposalAttributes());
    }

    private String buildPrompt(DriveGoalProposal proposal, NarrativeState narrative,
                                DerivedTheme theme) {
        var sb = new StringBuilder();
        sb.append("Theme: ").append(theme.label())
          .append(" (salience: ").append(String.format("%.2f", theme.salience())).append(")\n");
        sb.append("Axes: ").append(theme.axisModulationWeights()).append("\n");
        sb.append("Current goal: ").append(proposal.goalDescription()).append("\n");
        var episodes = narrative.episodes();
        if (!episodes.isEmpty()) {
            sb.append("Recent episodes:\n");
            episodes.stream().limit(3).forEach(e ->
                    sb.append("- ").append(e.description()).append("\n"));
        }
        sb.append("\nGenerate a single sentence describing a compound goal that combines these axes.");
        return sb.toString();
    }
}
