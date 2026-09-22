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

public class CbrMentalModelStore implements MentalModelStore {

    private final CbrRecordStore cbrStore;
    private final MemoryDomain domain;
    private final String caseType;

    public CbrMentalModelStore(CbrRecordStore cbrStore, MentalModelConfig config) {
        this.cbrStore = cbrStore;
        this.domain = new MemoryDomain(config.memoryDomain());
        this.caseType = config.caseType();
    }

    @Override
    public void store(MentalModelSnapshot snapshot) {
        var features = MentalModelSchema.toFeatures(snapshot);
        var summary = MentalModelSchema.toSummary(snapshot);
        var cbrCase = new CbrFeatureRecord(
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

        var results = cbrStore.retrieveSimilar(query, CbrRecord.class);
        return results.stream()
                .filter(s -> agentId.equals(s.cbrRecord().producerAgentId()))
                .findFirst()
                .map(s -> MentalModelSchema.fromCase(s, agentId, tenantId));
    }

    @Override
    public List<MentalModelSnapshot> findByAgent(String agentId, String tenantId) {
        var query = CbrQuery.of(tenantId, domain, Path.root(), caseType,
                        Map.of(), 100)
                .withMinSimilarity(0.0);

        var results = cbrStore.retrieveSimilar(query, CbrRecord.class);
        var snapshots = new ArrayList<MentalModelSnapshot>();
        for (var scored : results) {
            if (agentId.equals(scored.cbrRecord().producerAgentId())) {
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

        var results = cbrStore.retrieveSimilar(query, CbrRecord.class);
        for (var scored : results) {
            var subjectFeature = scored.cbrRecord().features().get(MentalModelSchema.SUBJECT_ID);
            if (subjectFeature instanceof FeatureValue.StringVal sv
                    && subjectId.equals(sv.value())) {
                cbrStore.erase(new EraseRequest(
                        scored.cbrRecord().producerAgentId() != null
                                ? scored.cbrRecord().producerAgentId() : "unknown",
                        domain, tenantId, scored.caseId()));
            }
        }
    }
}
