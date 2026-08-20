package io.casehub.blocks.memory;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.reflection.ReflectionOrchestrator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MemoryHygieneScheduler {

    private static final Logger LOG = Logger.getLogger(MemoryHygieneScheduler.class.getName());

    private final MemoryHygieneOrchestrator orchestrator;
    private final ReflectionOrchestrator reflectionOrchestrator;
    private final ReflectionStore reflectionStore;
    private final IntegrityChecker integrityChecker;
    private final CbrCaseMemoryStore store;
    private final MemoryDomain domain;
    private final List<String> caseTypes;
    private final int maxReflectionSources;
    private final double crossLinkSimilarityThreshold;
    private final Consumer<HygieneEvent> eventSink;

    public MemoryHygieneScheduler(
            MemoryHygieneOrchestrator orchestrator,
            ReflectionOrchestrator reflectionOrchestrator,
            ReflectionStore reflectionStore,
            IntegrityChecker integrityChecker,
            CbrCaseMemoryStore store,
            MemoryDomain domain,
            List<String> caseTypes,
            int maxReflectionSources,
            double crossLinkSimilarityThreshold,
            Consumer<HygieneEvent> eventSink) {
        this.orchestrator = orchestrator;
        this.reflectionOrchestrator = reflectionOrchestrator;
        this.reflectionStore = reflectionStore;
        this.integrityChecker = integrityChecker;
        this.store = store;
        this.domain = domain;
        this.caseTypes = List.copyOf(caseTypes);
        this.maxReflectionSources = maxReflectionSources;
        this.crossLinkSimilarityThreshold = crossLinkSimilarityThreshold;
        this.eventSink = eventSink;
    }

    public MaintenanceTick maintain(String agentId, String tenantId) {
        var hygieneTick = orchestrator.tick(agentId, tenantId);

        if (hygieneTick instanceof HygieneTick.Failed f) {
            return new MaintenanceTick.Failed("tick", f.reason());
        }

        int reflectionsGenerated;
        try {
            reflectionsGenerated = doReflection(agentId, tenantId, hygieneTick);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Reflection failed for " + agentId, e);
            return new MaintenanceTick.Failed("reflection", e.getMessage());
        }

        int crossLinksCreated;
        try {
            crossLinksCreated = doPeerLinking(agentId, tenantId);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Peer-linking failed for " + agentId, e);
            return new MaintenanceTick.Failed("peer-linking", e.getMessage());
        }

        List<IntegrityViolation> violations;
        try {
            violations = integrityChecker.check(agentId, tenantId, domain);
            for (var violation : violations) {
                eventSink.accept(new HygieneEvent.IntegrityViolationDetected(violation));
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Integrity check failed for " + agentId, e);
            return new MaintenanceTick.Failed("integrity", e.getMessage());
        }

        return new MaintenanceTick.Completed(hygieneTick, reflectionsGenerated,
                crossLinksCreated, violations);
    }

    private int doReflection(String agentId, String tenantId, HygieneTick hygieneTick) {
        var sourceCaseIds = switch (hygieneTick) {
            case HygieneTick.Completed c ->
                    c.scores().stream().map(RetentionScore::caseId).toList();
            default -> List.<String>of();
        };

        var since = Instant.now().minusSeconds(86400);
        var reflections = reflectionOrchestrator.reflect(
                agentId, tenantId, since, maxReflectionSources);

        var now = Instant.now();
        for (var insight : reflections) {
            reflectionStore.store(new ReflectionEntry(
                    agentId, tenantId, insight, now, sourceCaseIds));
            eventSink.accept(new HygieneEvent.ReflectionGenerated(agentId, insight));
        }

        return reflections.size();
    }

    private int doPeerLinking(String agentId, String tenantId) {
        return 0;
    }
}
