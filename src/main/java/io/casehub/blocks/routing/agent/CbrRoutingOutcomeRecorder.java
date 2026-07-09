package io.casehub.blocks.routing.agent;

import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.api.spi.routing.RoutingOutcome;
import io.casehub.api.spi.routing.RoutingOutcomeRecorder;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.PlanTrace;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class CbrRoutingOutcomeRecorder implements RoutingOutcomeRecorder {

    private final @Nullable CbrCaseMemoryStore cbrStore;
    private final RoutingFeatureExtractor featureExtractor;

    @Inject
    public CbrRoutingOutcomeRecorder(
            Instance<CbrCaseMemoryStore> cbrStore,
            Instance<RoutingFeatureExtractor> featureExtractor) {
        this.cbrStore = cbrStore.isUnsatisfied() ? null : cbrStore.get();
        this.featureExtractor = featureExtractor.isUnsatisfied()
                ? new TextOnlyFeatureExtractor() : featureExtractor.get();
    }

    CbrRoutingOutcomeRecorder(@Nullable CbrCaseMemoryStore cbrStore,
                              RoutingFeatureExtractor featureExtractor) {
        this.cbrStore = cbrStore;
        this.featureExtractor = featureExtractor;
    }

    @Override
    public Uni<Void> record(AgentRoutingContext context, String workerId,
                            String bindingName, RoutingOutcome outcome,
                            @Nullable Duration executionDuration) {
        if (cbrStore == null) return Uni.createFrom().voidItem();

        return Uni.createFrom().item(() -> {
            var trace = new PlanTrace(
                    bindingName, context.capabilityName(),
                    workerId, outcome.name(), 0, Map.of());
            var problem = featureExtractor.extractProblem(context);
            var cbrCase = new PlanCbrCase(
                    problem != null ? problem : context.capabilityName(),
                    "Routed to " + workerId,
                    outcome.name(), null,
                    featureExtractor.extractFeatures(context),
                    List.of(trace));
            cbrStore.store(cbrCase, context.caseId().toString(),
                    "agent-routing", new MemoryDomain(context.capabilityName()),
                    context.tenancyId(), context.caseId().toString());
            return null;
        }).emitOn(Infrastructure.getDefaultWorkerPool())
          .replaceWithVoid();
    }
}
