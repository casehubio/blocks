package io.casehub.blocks.social.jpa.spring;

import io.casehub.blocks.agentic.social.narrative.NarrativeState;
import io.casehub.blocks.agentic.social.narrative.NarrativeStore;
import io.casehub.blocks.social.jpa.NarrativeEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional
public class SpringNarrativeStore implements NarrativeStore {

    private final NarrativeEntityRepository repo;

    public SpringNarrativeStore(NarrativeEntityRepository repo) {
        this.repo = repo;
    }

    @Override
    public void store(NarrativeState state) {
        var entity = repo.findByScopeIdAndTenantId(state.scopeId(), state.tenantId()).orElse(null);
        if (entity != null) {
            mapToEntity(state, entity);
        } else {
            entity = new NarrativeEntity();
            entity.id = UUID.randomUUID().toString();
            mapToEntity(state, entity);
        }
        repo.save(entity);
    }

    @Override
    public @Nullable NarrativeState load(String scopeId, String tenantId) {
        return repo.findByScopeIdAndTenantId(scopeId, tenantId)
                .map(this::toDomain).orElse(null);
    }

    private void mapToEntity(NarrativeState s, NarrativeEntity e) {
        e.scopeId = s.scopeId();
        e.tenantId = s.tenantId();
        e.scope = s.scope();
        e.fragments = s.fragments();
        e.synthesisedAt = s.synthesisedAt();
        e.reflectionCountAtSynthesis = s.reflectionCountAtSynthesis();
    }

    private NarrativeState toDomain(NarrativeEntity e) {
        return new NarrativeState(
                e.scopeId, e.tenantId, e.scope,
                e.fragments, e.synthesisedAt, e.reflectionCountAtSynthesis);
    }
}
