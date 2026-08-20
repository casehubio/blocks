package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.FeatureVectorCbrCase;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import io.casehub.platform.api.path.Path;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import io.quarkus.arc.DefaultBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@DefaultBean
@ApplicationScoped
public class CbrUserProfileStore implements UserProfileStore {

    private final CbrCaseMemoryStore cbrStore;
    private final MemoryDomain domain;
    private final String caseType;

    CbrUserProfileStore(CbrCaseMemoryStore cbrStore, UserModelConfig config) {
        this.cbrStore = cbrStore;
        this.domain = new MemoryDomain(config.memoryDomain());
        this.caseType = config.caseType();
    }

    @Override
    public void store(UserProfile profile) {
        var features = UserProfileSchema.toFeatures(profile);
        var summary = UserProfileSchema.toSummary(profile);
        var cbrCase = new FeatureVectorCbrCase(
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

        var results = cbrStore.retrieveSimilar(query, CbrCase.class);
        return results.stream()
                .filter(s -> agentId.equals(s.cbrCase().producerAgentId()))
                .findFirst()
                .map(s -> UserProfileSchema.fromCase(s, agentId, tenantId));
    }

    @Override
    public List<UserProfile> findByAgent(String agentId, String tenantId) {
        var query = CbrQuery.of(tenantId, domain, Path.root(), caseType,
                        Map.of(), 100)
                .withMinSimilarity(0.0);

        var results = cbrStore.retrieveSimilar(query, CbrCase.class);
        var profiles = new ArrayList<UserProfile>();
        for (var scored : results) {
            if (agentId.equals(scored.cbrCase().producerAgentId())) {
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

        var results = cbrStore.retrieveSimilar(query, CbrCase.class);
        for (var scored : results) {
            var subjectFeature = scored.cbrCase().features().get(UserProfileSchema.SUBJECT_ID);
            if (subjectFeature instanceof io.casehub.neocortex.memory.cbr.FeatureValue.StringVal sv
                    && subjectId.equals(sv.value())) {
                cbrStore.erase(new EraseRequest(
                        scored.cbrCase().producerAgentId() != null
                                ? scored.cbrCase().producerAgentId() : "unknown",
                        domain, tenantId, scored.caseId()));
            }
        }
    }
}
