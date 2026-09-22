package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrRecord;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.CbrFeatureRecord;
import io.casehub.platform.api.path.Path;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CbrUserProfileStore implements UserProfileStore {

    private final CbrRecordStore cbrStore;
    private final MemoryDomain domain;
    private final String caseType;

    public CbrUserProfileStore(CbrRecordStore cbrStore, UserModelConfig config) {
        this.cbrStore = cbrStore;
        this.domain = new MemoryDomain(config.memoryDomain());
        this.caseType = config.caseType();
    }

    @Override
    public void store(UserProfile profile) {
        var features = UserProfileSchema.toFeatures(profile);
        var summary = UserProfileSchema.toSummary(profile);
        var cbrCase = new CbrFeatureRecord(
                summary, "-", null, null, features, null, profile.agentId());
        cbrStore.store(cbrCase, caseType, profile.agentId(), domain,
                profile.tenantId(), null, Path.root());
    }

    @Override
    public Optional<UserProfile> lookup(String agentId, String subjectId, String tenantId) {
        var query = CbrQuery.of(tenantId, domain, Path.root(), caseType,
                        Map.of(UserProfileSchema.SUBJECT_ID,
                                io.casehub.neocortex.memory.cbr.FeatureValue.string(subjectId)), 10)
                .withMinSimilarity(0.0);

        var results = cbrStore.retrieveSimilar(query, CbrRecord.class);
        return results.stream()
                .filter(s -> agentId.equals(s.cbrRecord().producerAgentId()))
                .findFirst()
                .map(s -> UserProfileSchema.fromCase(s, agentId, tenantId));
    }

    @Override
    public List<UserProfile> findByAgent(String agentId, String tenantId) {
        var query = CbrQuery.of(tenantId, domain, Path.root(), caseType,
                        Map.of(), 100)
                .withMinSimilarity(0.0);

        var results = cbrStore.retrieveSimilar(query, CbrRecord.class);
        var profiles = new ArrayList<UserProfile>();
        for (var scored : results) {
            if (agentId.equals(scored.cbrRecord().producerAgentId())) {
                profiles.add(UserProfileSchema.fromCase(scored, agentId, tenantId));
            }
        }
        return List.copyOf(profiles);
    }

    @Override
    public void eraseSubject(String subjectId, String tenantId) {
        var query = CbrQuery.of(tenantId, domain, Path.root(), caseType,
                        Map.of(UserProfileSchema.SUBJECT_ID,
                                io.casehub.neocortex.memory.cbr.FeatureValue.string(subjectId)), 100)
                .withMinSimilarity(0.0);

        var results = cbrStore.retrieveSimilar(query, CbrRecord.class);
        for (var scored : results) {
            var subjectFeature = scored.cbrRecord().features().get(UserProfileSchema.SUBJECT_ID);
            if (subjectFeature instanceof io.casehub.neocortex.memory.cbr.FeatureValue.StringVal sv
                    && subjectId.equals(sv.value())) {
                cbrStore.erase(new EraseRequest(
                        scored.cbrRecord().producerAgentId() != null
                                ? scored.cbrRecord().producerAgentId() : "unknown",
                        domain, tenantId, scored.caseId()));
            }
        }
    }
}
