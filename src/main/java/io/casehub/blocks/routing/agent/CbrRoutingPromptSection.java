package io.casehub.blocks.routing.agent;

import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.PlanTrace;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

@ApplicationScoped
public class CbrRoutingPromptSection implements RoutingPromptSection {

    private static final System.Logger LOG =
            System.getLogger(CbrRoutingPromptSection.class.getName());

    private final @Nullable CbrCaseMemoryStore cbrStore;
    private final RoutingFeatureExtractor featureExtractor;
    private final int topK;
    private final double minSimilarity;

    @Inject
    public CbrRoutingPromptSection(
            Instance<CbrCaseMemoryStore> cbrStore,
            Instance<RoutingFeatureExtractor> featureExtractor,
            @ConfigProperty(name = "casehub.blocks.cbr.top-k",
                            defaultValue = "10") int topK,
            @ConfigProperty(name = "casehub.blocks.cbr.min-similarity",
                            defaultValue = "0.5") double minSimilarity) {
        this.cbrStore = cbrStore.isUnsatisfied() ? null : cbrStore.get();
        this.featureExtractor = featureExtractor.isUnsatisfied()
                ? new TextOnlyFeatureExtractor() : featureExtractor.get();
        this.topK = topK;
        this.minSimilarity = minSimilarity;
    }

    CbrRoutingPromptSection(@Nullable CbrCaseMemoryStore cbrStore,
                            RoutingFeatureExtractor featureExtractor,
                            int topK, double minSimilarity) {
        this.cbrStore = cbrStore;
        this.featureExtractor = featureExtractor;
        this.topK = topK;
        this.minSimilarity = minSimilarity;
    }

    @Override
    public @Nullable String render(AgentRoutingContext context,
                                    List<AgentCandidate> eligible) {
        if (cbrStore == null) return null;

        try {
            var query = CbrQuery.of(
                    context.tenancyId(),
                    new MemoryDomain(context.capabilityName()),
                    "agent-routing",
                    featureExtractor.extractFeatures(context),
                    topK)
                    .withMinSimilarity(minSimilarity);

            String problem = featureExtractor.extractProblem(context);
            if (problem != null) {
                query = query.withProblem(problem);
            }

            List<ScoredCbrCase<CbrCase>> results =
                    cbrStore.retrieveSimilar(query, CbrCase.class);
            if (results.isEmpty()) return null;

            Set<String> eligibleIds = eligible.stream()
                    .map(AgentCandidate::workerId)
                    .collect(Collectors.toSet());

            return format(results, eligibleIds, context.capabilityName());
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "CBR query failed", e);
            return null;
        }
    }

    private @Nullable String format(List<ScoredCbrCase<CbrCase>> results,
                                     Set<String> eligibleIds,
                                     String capabilityName) {
        Map<String, Map<String, Integer>> agentOutcomes = new LinkedHashMap<>();
        var caseDetails = new StringBuilder();
        int caseNum = 0;

        for (var scored : results) {
            CbrCase cbrCase = scored.cbrCase();
            if (cbrCase instanceof PlanCbrCase planCase) {
                for (PlanTrace trace : planCase.planTrace()) {
                    if (capabilityName.equals(trace.capabilityName())
                            && trace.workerName() != null
                            && eligibleIds.contains(trace.workerName())) {
                        var outcomes = agentOutcomes.computeIfAbsent(
                                trace.workerName(), k -> new LinkedHashMap<>());
                        outcomes.merge(trace.stepOutcome(), 1, Integer::sum);
                        caseNum++;
                        caseDetails.append("  %d. [score: %.2f] problem: \"%s\" → agent: \"%s\" → %s%n"
                                .formatted(caseNum, scored.score(),
                                        truncate(planCase.problem(), 60),
                                        trace.workerName(), trace.stepOutcome()));
                    }
                }
            } else {
                caseNum++;
                caseDetails.append("  %d. [score: %.2f] problem: \"%s\" → %s%n"
                        .formatted(caseNum, scored.score(),
                                truncate(cbrCase.problem(), 60),
                                cbrCase.outcome() != null ? cbrCase.outcome() : "UNKNOWN"));
            }
        }

        if (caseNum == 0) return null;

        var sb = new StringBuilder();
        sb.append("Historical context (%d similar past cases for capability \"%s\"):\n"
                .formatted(caseNum, capabilityName));

        if (!agentOutcomes.isEmpty()) {
            sb.append("\nOutcomes by agent:\n");
            agentOutcomes.forEach((agent, outcomes) -> {
                int total = outcomes.values().stream().mapToInt(Integer::intValue).sum();
                int successes = outcomes.getOrDefault("SUCCESS", 0);
                int pct = total > 0 ? (int) Math.round(100.0 * successes / total) : 0;
                var parts = new StringJoiner(", ");
                outcomes.forEach((outcome, count) -> parts.add(count + " " + outcome));
                sb.append("  \"%s\": %d cases — %s (%d%% success)%n"
                        .formatted(agent, total, parts, pct));
            });
        }

        sb.append("\nCase details:\n");
        sb.append(caseDetails);
        return sb.toString().stripTrailing();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
