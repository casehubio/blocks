package io.casehub.blocks.social.jpa.quarkus;

import io.casehub.blocks.agentic.social.MentalModelSnapshot;
import io.casehub.blocks.agentic.social.MentalModelStore;
import io.casehub.blocks.social.jpa.MentalModelEntity;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Alternative
@jakarta.annotation.Priority(3)
@jakarta.enterprise.context.ApplicationScoped
@Transactional
public class JpaMentalModelStore implements MentalModelStore {

    @Inject
    EntityManager em;

    @Override
    public void store(MentalModelSnapshot snapshot) {
        var existing = findEntity(snapshot.agentId(), snapshot.subjectId(), snapshot.tenantId());
        if (existing != null) {
            mapToEntity(snapshot, existing);
        } else {
            var entity = new MentalModelEntity();
            entity.id = UUID.randomUUID().toString();
            mapToEntity(snapshot, entity);
            em.persist(entity);
        }
    }

    @Override
    public Optional<MentalModelSnapshot> lookup(String agentId, String subjectId, String tenantId) {
        var entity = findEntity(agentId, subjectId, tenantId);
        return entity == null ? Optional.empty() : Optional.of(toDomain(entity));
    }

    @Override
    public List<MentalModelSnapshot> findByAgent(String agentId, String tenantId) {
        return em.createQuery(
                        "SELECT e FROM MentalModelEntity e WHERE e.agentId = :a AND e.tenantId = :t",
                        MentalModelEntity.class)
                .setParameter("a", agentId).setParameter("t", tenantId)
                .getResultList().stream().map(this::toDomain).toList();
    }

    @Override
    public void eraseSubject(String subjectId, String tenantId) {
        em.createQuery("DELETE FROM MentalModelEntity e WHERE e.subjectId = :s AND e.tenantId = :t")
                .setParameter("s", subjectId).setParameter("t", tenantId)
                .executeUpdate();
    }

    private MentalModelEntity findEntity(String agentId, String subjectId, String tenantId) {
        var results = em.createQuery(
                        "SELECT e FROM MentalModelEntity e WHERE e.agentId = :a AND e.subjectId = :s AND e.tenantId = :t",
                        MentalModelEntity.class)
                .setParameter("a", agentId).setParameter("s", subjectId).setParameter("t", tenantId)
                .getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    private void mapToEntity(MentalModelSnapshot s, MentalModelEntity e) {
        e.agentId = s.agentId();
        e.subjectId = s.subjectId();
        e.tenantId = s.tenantId();
        e.beliefs = s.beliefs();
        e.desires = s.desires();
        e.intentions = s.intentions();
        e.lastSignal = s.lastSignal();
        e.lastInference = s.lastInference();
        e.snapshotCreated = s.snapshotCreated();
    }

    private MentalModelSnapshot toDomain(MentalModelEntity e) {
        return new MentalModelSnapshot(
                e.agentId, e.subjectId, e.tenantId,
                e.beliefs, e.desires, e.intentions,
                e.lastSignal, e.lastInference, e.snapshotCreated);
    }
}
