package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.FeatureVectorCbrCase;
import io.casehub.platform.api.path.Path;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.arc.DefaultBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@DefaultBean
@ApplicationScoped
public class CbrMentalModelStore implements MentalModelStore {

    private final CbrCaseMemoryStore cbrStore;
    private final MemoryDomain domain;
    private final String caseType;

    @Inject
    CbrMentalModelStore(CbrCaseMemoryStore cbrStore, MentalModelConfig config) {
        this.cbrStore = cbrStore;
        this.domain = new MemoryDomain(config.memoryDomain());
        this.caseType = config.caseType();
    }

    @Override
    public void store(MentalModelSnapshot snapshot) {
        var features = MentalModelSchema.toFeatures(snapshot);
        var summary = MentalModelSchema.toSummary(snapshot);
        var cbrCase = new FeatureVectorCbrCase(
                summary, "-", null, null, features, null, snapshot.agentId());
        cbrStore.store(cbrCase, caseType, snapshot.agentId(), domain,
                snapshot.tenantId(), null, Path.root());
    }

    @Override
    public Optional<MentalModelSnapshot> lookup(String agentId, String subjectId, String tenantId) {
        var query = CbrQuery.of(tenantId, domain, Path.root(), caseType,
                        Map.of(MentalModelSchema.SUBJECT_ID,
                                FeatureValue.string(subjectId)), 10)
                .withMinSimilarity(0.0);

        var results = cbrStore.retrieveSimilar(query, CbrCase.class);
        return results.stream()
                .filter(s -> agentId.equals(s.cbrCase().producerAgentId()))
                .findFirst()
                .map(s -> MentalModelSchema.fromCase(s, agentId, tenantId));
    }

    @Override
    public List<MentalModelSnapshot> findByAgent(String agentId, String tenantId) {
        var query = CbrQuery.of(tenantId, domain, Path.root(), caseType,
                        Map.of(), 100)
                .withMinSimilarity(0.0);

        var results = cbrStore.retrieveSimilar(query, CbrCase.class);
        var snapshots = new ArrayList<MentalModelSnapshot>();
        for (var scored : results) {
            if (agentId.equals(scored.cbrCase().producerAgentId())) {
                snapshots.add(MentalModelSchema.fromCase(scored, agentId, tenantId));
            }
        }
        return List.copyOf(snapshots);
    }

    @Override
    public void eraseSubject(String subjectId, String tenantId) {
        var query = CbrQuery.of(tenantId, domain, Path.root(), caseType,
                        Map.of(MentalModelSchema.SUBJECT_ID,
                                FeatureValue.string(subjectId)), 100)
                .withMinSimilarity(0.0);

        var results = cbrStore.retrieveSimilar(query, CbrCase.class);
        for (var scored : results) {
            var subjectFeature = scored.cbrCase().features().get(MentalModelSchema.SUBJECT_ID);
            if (subjectFeature instanceof FeatureValue.StringVal sv
                    && subjectId.equals(sv.value())) {
                cbrStore.erase(new EraseRequest(
                        scored.cbrCase().producerAgentId() != null
                                ? scored.cbrCase().producerAgentId() : "unknown",
                        domain, tenantId, scored.caseId()));
            }
        }
    }
}
