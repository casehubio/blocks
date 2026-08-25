package io.casehub.blocks.agentic.social.narrative;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.FeatureVectorCbrCase;
import io.casehub.platform.api.path.Path;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.util.Map;

@DefaultBean
@ApplicationScoped
public class CbrNarrativeStore implements NarrativeStore {

    private final CbrCaseMemoryStore cbrStore;
    private final MemoryDomain domain;
    private final String caseType;

    @Inject
    CbrNarrativeStore(CbrCaseMemoryStore cbrStore, NarrativeConfig config) {
        this.cbrStore = cbrStore;
        this.domain = new MemoryDomain(config.memoryDomain());
        this.caseType = config.caseType();
    }

    @Override
    public void store(NarrativeState state) {
        var features = NarrativeStateSchema.toFeatures(state);
        var summary = NarrativeStateSchema.toSummary(state);
        var cbrCase = new FeatureVectorCbrCase(
                summary, "-", null, null, features, null, state.scopeId());
        cbrStore.store(cbrCase, caseType, state.scopeId(), domain,
                state.tenantId(), null, Path.root());
    }

    @Override
    public @Nullable NarrativeState load(String scopeId, String tenantId) {
        var query = CbrQuery.of(tenantId, domain, Path.root(), caseType,
                        Map.of(NarrativeStateSchema.SCOPE_ID,
                                FeatureValue.string(scopeId)), 10)
                .withMinSimilarity(0.0);
        var results = cbrStore.retrieveSimilar(query, CbrCase.class);
        return results.stream()
                .filter(s -> scopeId.equals(s.cbrCase().producerAgentId()))
                .findFirst()
                .map(s -> NarrativeStateSchema.fromCase(s, scopeId, tenantId))
                .orElse(null);
    }
}
