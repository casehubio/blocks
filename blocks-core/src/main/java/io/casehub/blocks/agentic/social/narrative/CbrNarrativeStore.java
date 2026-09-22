package io.casehub.blocks.agentic.social.narrative;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrRecord;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.CbrFeatureRecord;
import io.casehub.platform.api.path.Path;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public class CbrNarrativeStore implements NarrativeStore {

    private final CbrRecordStore cbrStore;
    private final MemoryDomain domain;
    private final String caseType;

    public CbrNarrativeStore(CbrRecordStore cbrStore, NarrativeConfig config) {
        this.cbrStore = cbrStore;
        this.domain = new MemoryDomain(config.memoryDomain());
        this.caseType = config.caseType();
    }

    @Override
    public void store(NarrativeState state) {
        var features = NarrativeStateSchema.toFeatures(state);
        var summary = NarrativeStateSchema.toSummary(state);
        var cbrCase = new CbrFeatureRecord(
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
        var results = cbrStore.retrieveSimilar(query, CbrRecord.class);
        return results.stream()
                .filter(s -> scopeId.equals(s.cbrRecord().producerAgentId()))
                .findFirst()
                .map(s -> NarrativeStateSchema.fromCase(s, scopeId, tenantId))
                .orElse(null);
    }
}
