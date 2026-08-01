package io.casehub.blocks.prompt.optimiser;

import io.casehub.blocks.prompt.OptimisationDataset;
import io.casehub.blocks.prompt.OptimiserConfig;
import io.casehub.blocks.prompt.OptimiserResult;
import io.casehub.blocks.prompt.PromptOptimiser;
import io.casehub.blocks.prompt.PromptSignature;
import io.casehub.blocks.prompt.PromptVariant;
import io.casehub.blocks.prompt.VariantOutcome;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

public class InstructionOptimiser implements PromptOptimiser {

    private static final System.Logger LOG = System.getLogger(InstructionOptimiser.class.getName());

    private static final String META_SYSTEM_PROMPT = """
            You are a prompt optimisation assistant. Given outcome patterns from an LLM-driven \
            decision system, generate concise instruction refinements that would improve future decisions.

            Respond with ONLY the instruction text to append to the system prompt. \
            No explanations, no markdown, no preamble. If no improvements are needed, respond with \
            an empty string.""";

    private final AgentProvider agentProvider;

    public InstructionOptimiser(AgentProvider agentProvider) {
        this.agentProvider = agentProvider;
    }

    @Override
    public String id() {
        return "instruction";
    }

    @Override
    public CompletionStage<OptimiserResult> optimise(
            PromptSignature signature,
            @Nullable PromptVariant currentVariant,
            OptimisationDataset dataset,
            OptimiserConfig config) {

        if (dataset.outcomes().isEmpty()) {
            return CompletableFuture.completedFuture(new OptimiserResult(List.of(), null, 0.0));
        }

        try {
            var userPrompt = buildMetaPrompt(signature, dataset.outcomes());
            var sessionConfig = AgentSessionConfig.of(META_SYSTEM_PROMPT, userPrompt);

            var response = agentProvider.invoke(sessionConfig)
                    .filter(e -> e instanceof AgentEvent.TextDelta)
                    .map(e -> ((AgentEvent.TextDelta) e).text())
                    .collect().with(Collectors.joining())
                    .await().indefinitely();

            var delta = response != null && !response.isBlank() ? response.strip() : null;
            return CompletableFuture.completedFuture(new OptimiserResult(List.of(), delta, 0.0));
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "Instruction optimisation failed", e);
            return CompletableFuture.completedFuture(new OptimiserResult(List.of(), null, 0.0));
        }
    }

    private String buildMetaPrompt(PromptSignature signature, List<VariantOutcome> outcomes) {
        var sb = new StringBuilder();
        sb.append("Signature: ").append(signature.id())
                .append(" — ").append(signature.description()).append("\n\n");
        sb.append("Current base system prompt:\n").append(signature.baseSystemPrompt()).append("\n\n");
        sb.append("Outcome distribution from ").append(outcomes.size()).append(" decisions:\n");

        var grouped = outcomes.stream()
                .collect(Collectors.groupingBy(VariantOutcome::outcome, Collectors.counting()));
        grouped.forEach((outcome, count) ->
                sb.append("  ").append(outcome).append(": ").append(count)
                        .append(" (").append(String.format("%.0f%%", 100.0 * count / outcomes.size()))
                        .append(")\n"));

        sb.append("\nGenerate instruction refinements to improve the success rate.");
        return sb.toString();
    }
}
