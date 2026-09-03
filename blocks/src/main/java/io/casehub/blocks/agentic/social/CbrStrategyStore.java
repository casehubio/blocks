package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.FeatureVectorCbrCase;
import io.casehub.platform.api.path.Path;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@DefaultBean
@ApplicationScoped
public class CbrStrategyStore implements StrategyStore {

    private final CbrCaseMemoryStore cbrStore;
    private final MemoryDomain domain;
    private final String profileCaseType;
    private final String engagementCaseType;

    CbrStrategyStore(CbrCaseMemoryStore cbrStore, StrategyLearningConfig config) {
        this.cbrStore = cbrStore;
        this.domain = config.memoryDomain();
        this.profileCaseType = config.profileCaseType();
        this.engagementCaseType = config.engagementCaseType();
    }

    @Override
    public void store(StrategyProfile profile) {
        var features = StrategyProfileSchema.toFeatures(profile);
        var summary = StrategyProfileSchema.toSummary(profile);
        var cbrCase = new FeatureVectorCbrCase(
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
        var results = cbrStore.retrieveSimilar(query, CbrCase.class);
        return results.stream()
                .filter(s -> agentId.equals(s.cbrCase().producerAgentId()))
                .findFirst()
                .map(s -> StrategyProfileSchema.fromCase(s, agentId, tenantId));
    }

    @Override
    public List<String> subjectInsights(String agentId, String subjectId,
                                         String tenantId) {
        var query = CbrQuery.of(tenantId, domain, Path.root(), engagementCaseType,
                        Map.of("subjectId", FeatureValue.string(subjectId)), 50)
                .withMinSimilarity(0.0);
        var results = cbrStore.retrieveSimilar(query, CbrCase.class);
        var insights = new ArrayList<String>();
        for (var scored : results) {
            if (!agentId.equals(scored.cbrCase().producerAgentId())) continue;
            var features = scored.cbrCase().features();
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
        var results = cbrStore.retrieveSimilar(query, CbrCase.class);
        for (var scored : results) {
            var sv = scored.cbrCase().features().get("subjectId");
            if (sv instanceof FeatureValue.StringVal s && subjectId.equals(s.value())) {
                cbrStore.erase(new EraseRequest(
                        scored.cbrCase().producerAgentId() != null
                                ? scored.cbrCase().producerAgentId() : "unknown",
                        domain, tenantId, scored.caseId()));
            }
        }
    }

    private void eraseCases(String agentId, String tenantId, String caseType) {
        var query = CbrQuery.of(tenantId, domain, Path.root(), caseType,
                        Map.of(), 100)
                .withMinSimilarity(0.0);
        var results = cbrStore.retrieveSimilar(query, CbrCase.class);
        for (var scored : results) {
            if (agentId.equals(scored.cbrCase().producerAgentId())) {
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
