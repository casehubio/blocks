package io.casehub.blocks.social.jpa.quarkus;

import io.casehub.blocks.agentic.social.narrative.NarrativeState;
import io.casehub.blocks.agentic.social.narrative.NarrativeStore;
import io.casehub.blocks.social.jpa.NarrativeEntity;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

@Alternative
@jakarta.annotation.Priority(3)
@jakarta.enterprise.context.ApplicationScoped
@Transactional
public class JpaNarrativeStore implements NarrativeStore {

    @Inject
    EntityManager em;

    @Override
    public void store(NarrativeState state) {
        var existing = findEntity(state.scopeId(), state.tenantId());
        if (existing != null) {
            mapToEntity(state, existing);
        } else {
            var entity = new NarrativeEntity();
            entity.id = UUID.randomUUID().toString();
            mapToEntity(state, entity);
            em.persist(entity);
        }
    }

    @Override
    public @Nullable NarrativeState load(String scopeId, String tenantId) {
        var entity = findEntity(scopeId, tenantId);
        return entity == null ? null : toDomain(entity);
    }

    private NarrativeEntity findEntity(String scopeId, String tenantId) {
        var results = em.createQuery(
                        "SELECT e FROM NarrativeEntity e WHERE e.scopeId = :s AND e.tenantId = :t",
                        NarrativeEntity.class)
                .setParameter("s", scopeId).setParameter("t", tenantId)
                .getResultList();
        return results.isEmpty() ? null : results.get(0);
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
