package io.casehub.blocks.memory;

import io.casehub.blocks.summarisation.ContentSummariser;
import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.FeatureVectorCbrCase;
import io.casehub.neocortex.memory.cbr.ScopeDecay;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import io.casehub.neocortex.memory.cbr.TemporalDecay;
import io.casehub.platform.api.path.Path;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MemoryHygieneOrchestrator {

    private static final Logger LOG = Logger.getLogger(MemoryHygieneOrchestrator.class.getName());
    static final         double LOW_RETENTION_THRESHOLD = 0.3;


    private final CbrCaseMemoryStore store;
    private final ConfidenceScorer   confidenceScorer;
    private final TemporalDecay      temporalDecay;
    private final ScopeDecay scopeDecay;
    private final ContentSummariser<ScoredCbrCase<? extends CbrCase>> summariser;
    private final MemoryDomain domain;
    private final List<String>    caseTypes;
    private final RetentionConfig RetentionConfig;
    private final int             consolidationBatchSize;
    private final double crossLinkSimilarityThreshold;
    private final Consumer<HygieneEvent> eventSink;

    private final ConcurrentHashMap<String, ReentrantLock> tickLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, KnowledgeGapSummary> gapStates = new ConcurrentHashMap<>();


    public MemoryHygieneOrchestrator(
            CbrCaseMemoryStore store,
            ConfidenceScorer confidenceScorer,
            TemporalDecay temporalDecay,
            ScopeDecay scopeDecay,
            ContentSummariser<ScoredCbrCase<? extends CbrCase>> summariser,
            MemoryDomain domain,
            List<String> caseTypes,
            RetentionConfig RetentionConfig,
            int consolidationBatchSize,
            double crossLinkSimilarityThreshold,
            Consumer<HygieneEvent> eventSink) {
        this.store            = store;
        this.confidenceScorer = confidenceScorer;
        this.temporalDecay    = temporalDecay;
        this.scopeDecay = scopeDecay;
        this.summariser = summariser;
        this.domain = domain;
        this.caseTypes              = List.copyOf(caseTypes);
        this.RetentionConfig        = RetentionConfig;
        this.consolidationBatchSize = consolidationBatchSize;
        this.crossLinkSimilarityThreshold = crossLinkSimilarityThreshold;
        this.eventSink = eventSink;
    }

    public HygieneTick tick(String agentId, String tenantId) {
        var lock = tickLocks.computeIfAbsent(agentId + ":" + tenantId, k -> new ReentrantLock());
        lock.lock();
        try {
            return doTick(agentId, tenantId);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Memory hygiene tick failed for " + agentId, e);
            return new HygieneTick.Failed(e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    public KnowledgeGapSummary knowledgeGaps(String agentId, String tenantId) {
        var summary = gapStates.get(agentId + ":" + tenantId);
        return summary != null ? summary : KnowledgeGapSummary.empty();
    }


    private HygieneTick doTick(String agentId, String tenantId) {
        var now = Instant.now();
        var allScores = new ArrayList<RetentionScore>();
        int totalEvicted = 0;
        int totalConsolidated = 0;

        for (var caseType : caseTypes) {
            var query = CbrQuery.of(tenantId, domain, Path.root(), caseType,
                            Map.of(), consolidationBatchSize)
                    .withMinSimilarity(0.0);
            List<ScoredCbrCase<CbrCase>> memories = store.retrieveSimilar(query, CbrCase.class);

            var agentMemories = memories.stream()
                    .filter(m -> agentId.equals(m.cbrCase().producerAgentId()))
                    .toList();

            if (agentMemories.isEmpty()) {continue;}

            var scored = agentMemories.stream()
                    .map(m -> RetentionScore.compute(
                            m.caseId(),
                            entityId(m),
                            confidenceScorer.score(m, now),
                            temporalDecay.factor(m.storedAt(), now),
                            scopeDecay.factor(0),
                            m.cbrCase().trustScore() != null ? m.cbrCase().trustScore() : 1.0,
                            RetentionConfig))
                    .toList();

            allScores.addAll(scored);

            var toEvict = scored.stream()
                    .filter(s -> s.composite() < RetentionConfig.retentionThreshold())
                    .toList();

            for (var eviction : toEvict) {
                store.erase(new EraseRequest(eviction.entityId(), domain, tenantId, eviction.caseId()));
                eventSink.accept(new HygieneEvent.MemoryEvicted(eviction.caseId(), eviction));
            }
            totalEvicted += toEvict.size();

            var survivors = agentMemories.stream()
                    .filter(m -> scored.stream()
                            .anyMatch(s -> s.caseId().equals(m.caseId())
                                    && s.composite() >= RetentionConfig.retentionThreshold()))
                    .toList();

            totalConsolidated += consolidate(survivors, tenantId, caseType);
        }

        if (allScores.isEmpty()) {
            return new HygieneTick.Idle("no memories for agent");
        }

        int lowRetention = (int) allScores.stream().filter(s -> s.composite() < LOW_RETENTION_THRESHOLD).count();
        gapStates.put(agentId + ":" + tenantId,
                new KnowledgeGapSummary(lowRetention, totalConsolidated, allScores.size()));

        return new HygieneTick.Completed(totalConsolidated, totalEvicted, allScores.size(), allScores);
    }

    private int consolidate(List<ScoredCbrCase<CbrCase>> survivors, String tenantId, String caseType) {
        if (survivors.size() < 2) {return 0;}

        var groups = findSimilarGroups(survivors);
        int consolidated = 0;

        for (var group : groups) {
            if (group.size() < 2) {continue;}
            try {
                @SuppressWarnings("unchecked")
                var castGroup = (List<ScoredCbrCase<? extends CbrCase>>) (List<?>) group;
                var summaryResult = summariser.summarise(castGroup, null).toCompletableFuture().join();

                var mergedFeatures = new HashMap<String, FeatureValue>();
                var sourceCaseIds = new ArrayList<String>();
                for (var m : group) {
                    mergedFeatures.putAll(m.cbrCase().features());
                    sourceCaseIds.add(m.caseId());
                }
                mergedFeatures.put("source_cases", FeatureValue.stringList(sourceCaseIds));

                var mergedCase = new FeatureVectorCbrCase(
                        summaryResult.text() != null && !summaryResult.text().isBlank()
                                ? summaryResult.text() : "consolidated memory",
                        "consolidated from " + sourceCaseIds.size() + " memories",
                        null, null, mergedFeatures, null,
                        group.getFirst().cbrCase().producerAgentId());

                var mergedId = store.store(mergedCase, caseType,
                        group.getFirst().cbrCase().producerAgentId(),
                        domain, tenantId, null, Path.root());

                for (var sourceId : sourceCaseIds) {
                    store.supersede(sourceId, tenantId, mergedId, "hygiene-consolidation");
                }

                eventSink.accept(new HygieneEvent.MemoryConsolidated(mergedId, sourceCaseIds));
                consolidated++;
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Consolidation failed for group", e);
            }
        }

        return consolidated;
    }

    private List<List<ScoredCbrCase<CbrCase>>> findSimilarGroups(
            List<ScoredCbrCase<CbrCase>> memories) {
        var groups = new ArrayList<List<ScoredCbrCase<CbrCase>>>();
        var used = new boolean[memories.size()];

        for (int i = 0; i < memories.size(); i++) {
            if (used[i]) {continue;}
            var group = new ArrayList<ScoredCbrCase<CbrCase>>();
            group.add(memories.get(i));
            used[i] = true;

            for (int j = i + 1; j < memories.size(); j++) {
                if (used[j]) {continue;}
                if (memories.get(j).score() >= crossLinkSimilarityThreshold) {
                    group.add(memories.get(j));
                    used[j] = true;
                }
            }
            groups.add(group);
        }

        return groups;
    }

    private static String entityId(ScoredCbrCase<CbrCase> m) {
        var features = m.cbrCase().features();
        if (features != null && features.containsKey("entity_id")) {
            var fv = features.get("entity_id");
            if (fv instanceof FeatureValue.StringVal sv) {return sv.value();}
        }
        return m.caseId();
    }


}
