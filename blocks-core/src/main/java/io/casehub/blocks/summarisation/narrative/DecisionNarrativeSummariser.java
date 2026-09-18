package io.casehub.blocks.summarisation.narrative;

import io.casehub.blocks.agent.StructuredAgentInvoker;
import io.casehub.blocks.agent.StructuredAgentInvoker.InvocationResult;
import io.casehub.blocks.summarisation.ContentSummariser;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import jakarta.json.Json;
import org.jspecify.annotations.Nullable;

import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import java.util.logging.Logger;


public class DecisionNarrativeSummariser
        implements ContentSummariser<StepDecisionSummary, DecisionNarrative> {

    private static final Logger LOG = Logger.getLogger(DecisionNarrativeSummariser.class.getName());

    static final String SYSTEM_PROMPT = """
            You are generating a concise decision narrative explaining why an \
            AI agent made specific decisions during case execution. Given the \
            step-level decision summaries and (optionally) the previous narrative, \
            produce a JSON response with:

            1. explanation — a 2-4 sentence human-readable explanation of the \
            decisions made. Focus on WHY: what evidence led to the routing choice, \
            what historical cases informed the approach, what trust levels influenced \
            agent selection.

            2. evidenceSources — array of signal types that contributed (e.g., \
            "RoutingDecision", "CbrRetrieval").

            3. confidence — a number [0.0, 1.0] reflecting overall decision confidence.

            If a previous narrative is provided, incorporate new steps into the \
            existing explanation rather than starting from scratch. Preserve prior \
            context and extend it.

            Respond with JSON only. No explanation outside the JSON.""";

    private final AgentProvider agentProvider;

    public DecisionNarrativeSummariser(AgentProvider agentProvider) {
        this.agentProvider = agentProvider;
    }

    @Override
    public CompletionStage<DecisionNarrative> summarise(
            List<StepDecisionSummary> items, @Nullable DecisionNarrative previous) {
        if (items.isEmpty()) {
            return CompletableFuture.completedFuture(previous != null ? previous
                    : new DecisionNarrative("unknown", List.of(), "No signals.", List.of(), 0.0, Instant.now()));
        }

        var caseId = items.get(0).caseId();
        var stepNames = items.stream().map(StepDecisionSummary::stepName).toList();
        var allStepNames = new ArrayList<String>();
        if (previous != null) {
            allStepNames.addAll(previous.stepNames());
        }
        allStepNames.addAll(stepNames);

        var userPrompt = assembleUserPrompt(items, previous);

        try {
            var textResult = StructuredAgentInvoker.invokeText(agentProvider,
                    AgentSessionConfig.of(SYSTEM_PROMPT, userPrompt));
            if (textResult instanceof InvocationResult.AgentError<?>) {
                return CompletableFuture.completedFuture(templateFallback(items, previous, caseId, allStepNames));
            }
            var responseText = ((InvocationResult.Success<String>) textResult).value();

            if (responseText == null || responseText.isBlank()) {
                return CompletableFuture.completedFuture(templateFallback(items, previous, caseId, allStepNames));
            }

            return CompletableFuture.completedFuture(parseResponse(responseText, caseId, allStepNames));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "LLM invocation failed for case " + caseId, e);
            return CompletableFuture.completedFuture(templateFallback(items, previous, caseId, allStepNames));
        }
    }

    String assembleUserPrompt(List<StepDecisionSummary> items, @Nullable DecisionNarrative previous) {
        var sb = new StringBuilder();
        sb.append("## Previous narrative\n");
        if (previous != null) {
            sb.append(previous.explanation()).append("\n\n");
        } else {
            sb.append("None — first synthesis\n\n");
        }

        sb.append("## New step summaries\n");
        int i = 1;
        for (var step : items) {
            sb.append(i++).append(". Step \"").append(step.stepName())
                    .append("\" (").append(step.from()).append(" → ").append(step.to()).append(")\n");
            for (var digest : step.signals()) {
                sb.append("   - ").append(digest.signalType()).append(": ").append(digest.summary());
                if (!digest.keyFacts().isEmpty()) {
                    sb.append(" [");
                    var entries = new ArrayList<>(digest.keyFacts().entrySet());
                    for (int j = 0; j < entries.size(); j++) {
                        if (j > 0) sb.append(", ");
                        sb.append(entries.get(j).getKey()).append("=").append(entries.get(j).getValue());
                    }
                    sb.append("]");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    DecisionNarrative parseResponse(String responseText, String caseId, List<String> stepNames) {
        try (var reader = Json.createReader(new StringReader(responseText))) {
            var json = reader.readObject();
            var explanation = json.getString("explanation", "");
            var sources = new ArrayList<String>();
            if (json.containsKey("evidenceSources")) {
                var arr = json.getJsonArray("evidenceSources");
                for (int i = 0; i < arr.size(); i++) {
                    sources.add(arr.getString(i));
                }
            }
            var confidence = json.getJsonNumber("confidence") != null
                    ? json.getJsonNumber("confidence").doubleValue() : 0.5;
            confidence = Math.max(0.0, Math.min(1.0, confidence));
            return new DecisionNarrative(caseId, stepNames, explanation, sources, confidence, Instant.now());
        }
    }

    DecisionNarrative templateFallback(List<StepDecisionSummary> items,
                                        @Nullable DecisionNarrative previous,
                                        String caseId, List<String> stepNames) {
        var sb = new StringBuilder();
        if (previous != null) {
            sb.append(previous.explanation()).append(" ");
        }
        for (var step : items) {
            sb.append("Step ").append(step.stepName()).append(": ");
            for (var digest : step.signals()) {
                sb.append(digest.summary()).append(". ");
            }
        }
        var sources = items.stream()
                .flatMap(s -> s.signals().stream())
                .map(SignalDigest::signalType)
                .distinct().toList();
        var minConfidence = items.stream()
                .flatMap(s -> s.signals().stream())
                .mapToDouble(SignalDigest::confidence)
                .min().orElse(0.5);
        return new DecisionNarrative(caseId, stepNames, sb.toString().strip(), sources, minConfidence, Instant.now());
    }
}
