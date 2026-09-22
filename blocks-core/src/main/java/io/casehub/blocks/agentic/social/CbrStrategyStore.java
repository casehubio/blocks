package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrRecord;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.CbrFeatureRecord;
import io.casehub.platform.api.path.Path;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CbrStrategyStore implements StrategyStore {

    private final CbrRecordStore cbrStore;
    private final MemoryDomain domain;
    private final String profileCaseType;
    private final String engagementCaseType;

    public CbrStrategyStore(CbrRecordStore cbrStore, StrategyLearningConfig config) {
        this.cbrStore = cbrStore;
        this.domain = config.memoryDomain();
        this.profileCaseType = config.profileCaseType();
        this.engagementCaseType = config.engagementCaseType();
    }

    @Override
    public void store(StrategyProfile profile) {
        var features = StrategyProfileSchema.toFeatures(profile);
        var summary = StrategyProfileSchema.toSummary(profile);
        var cbrCase = new CbrFeatureRecord(
                summary, "-", null, null, features, null, profile.agentId());
        cbrStore.store(cbrCase, profileCaseType, profile.agentId(), domain,
                profile.tenantId(), null, Path.root());
    }

    @Override
    public Optional<StrategyProfile> lookup(String agentId, String tenantId) {
        var query = CbrQuery.of(tenantId, domain, Path.root(), profileCaseType,
                        Map.of(StrategyProfileSchema.AGENT_ID,
                                FeatureValue.string(agentId)), 10)
                .withMinSimilarity(0.0);
        var results = cbrStore.retrieveSimilar(query, CbrRecord.class);
        return results.stream()
                .filter(s -> agentId.equals(s.cbrRecord().producerAgentId()))
                .findFirst()
                .map(s -> StrategyProfileSchema.fromCase(s, agentId, tenantId));
    }

    @Override
    public List<String> subjectInsights(String agentId, String subjectId,
                                         String tenantId) {
        var query = CbrQuery.of(tenantId, domain, Path.root(), engagementCaseType,
                        Map.of("subjectId", FeatureValue.string(subjectId)), 50)
                .withMinSimilarity(0.0);
        var results = cbrStore.retrieveSimilar(query, CbrRecord.class);
        var insights = new ArrayList<String>();
        for (var scored : results) {
            if (!agentId.equals(scored.cbrRecord().producerAgentId())) continue;
            var features = scored.cbrRecord().features();
            var sv = features.get("subjectId");
            if (!(sv instanceof FeatureValue.StringVal s) || !subjectId.equals(s.value()))
                continue;

            double contRate = numberVal(features, "continuationRate", -1);
            double avgLen = numberVal(features, "avgResponseLength", -1);
            double sentiment = numberVal(features, "meanAffectShift", 0);

            if (contRate >= 0 || avgLen >= 0) {
                insights.add(String.format(
                        "With %s: engagement rate %.0f%%, avg response length %.0f, sentiment %+.2f",
                        subjectId, contRate * 100, avgLen, sentiment));
            }
        }
        return List.copyOf(insights);
    }

    @Override
    public void eraseAgent(String agentId, String tenantId) {
        eraseCases(agentId, tenantId, profileCaseType);
        eraseCases(agentId, tenantId, engagementCaseType);
    }

    @Override
    public void eraseSubject(String subjectId, String tenantId) {
        var query = CbrQuery.of(tenantId, domain, Path.root(), engagementCaseType,
                        Map.of("subjectId", FeatureValue.string(subjectId)), 100)
                .withMinSimilarity(0.0);
        var results = cbrStore.retrieveSimilar(query, CbrRecord.class);
        for (var scored : results) {
            var sv = scored.cbrRecord().features().get("subjectId");
            if (sv instanceof FeatureValue.StringVal s && subjectId.equals(s.value())) {
                cbrStore.erase(new EraseRequest(
                        scored.cbrRecord().producerAgentId() != null
                                ? scored.cbrRecord().producerAgentId() : "unknown",
                        domain, tenantId, scored.caseId()));
            }
        }
    }

    private void eraseCases(String agentId, String tenantId, String caseType) {
        var query = CbrQuery.of(tenantId, domain, Path.root(), caseType,
                        Map.of(), 100)
                .withMinSimilarity(0.0);
        var results = cbrStore.retrieveSimilar(query, CbrRecord.class);
        for (var scored : results) {
            if (agentId.equals(scored.cbrRecord().producerAgentId())) {
                cbrStore.erase(new EraseRequest(agentId, domain, tenantId,
                        scored.caseId()));
            }
        }
    }

    private static double numberVal(Map<String, FeatureValue> features,
                                     String key, double defaultVal) {
        var val = features.get(key);
        if (val instanceof FeatureValue.NumberVal nv) return nv.value();
        return defaultVal;
    }
}
