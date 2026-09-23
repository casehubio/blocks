package io.casehub.blocks.social.jpa.quarkus;

import io.casehub.blocks.agentic.social.EngagementEvidence;
import io.casehub.blocks.agentic.social.StrategyProfile;
import io.casehub.blocks.agentic.social.StrategyStore;
import io.casehub.blocks.social.jpa.EngagementEvidenceEntity;
import io.casehub.blocks.social.jpa.StrategyProfileEntity;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Alternative
@jakarta.annotation.Priority(3)
@jakarta.enterprise.context.ApplicationScoped
@Transactional
public class JpaStrategyStore implements StrategyStore {

    @Inject
    EntityManager em;

    @Override
    public void store(StrategyProfile profile) {
        var existing = findProfileEntity(profile.agentId(), profile.tenantId());
        if (existing != null) {
            mapToEntity(profile, existing);
        } else {
            var entity = new StrategyProfileEntity();
            entity.id = UUID.randomUUID().toString();
            mapToEntity(profile, entity);
            em.persist(entity);
        }
    }

    @Override
    public Optional<StrategyProfile> lookup(String agentId, String tenantId) {
        var entity = findProfileEntity(agentId, tenantId);
        return entity == null ? Optional.empty() : Optional.of(toDomain(entity));
    }

    @Override
    public List<String> subjectInsights(String agentId, String subjectId, String tenantId) {
        var results = em.createQuery(
                        "SELECT e FROM EngagementEvidenceEntity e " +
                                "WHERE e.agentId = :a AND e.subjectId = :s AND e.tenantId = :t " +
                                "ORDER BY e.recordedAt DESC",
                        EngagementEvidenceEntity.class)
                .setParameter("a", agentId).setParameter("s", subjectId).setParameter("t", tenantId)
                .setMaxResults(10)
                .getResultList();
        var insights = new ArrayList<String>();
        for (var e : results) {
            insights.add(String.format("engagement=%.0f%% sentiment=%+.2f turns=%d (%s)",
                    e.continuationRate * 100, e.meanAffectShift, e.turnCount,
                    e.conversationSummary != null ? e.conversationSummary : "no summary"));
        }
        return insights;
    }

    @Override
    public void storeEvidence(EngagementEvidence evidence) {
        var entity = new EngagementEvidenceEntity();
        entity.id = UUID.randomUUID().toString();
        entity.agentId = evidence.agentId();
        entity.subjectId = evidence.subjectId();
        entity.tenantId = evidence.tenantId();
        entity.conversationId = evidence.conversationId();
        entity.conversationSummary = evidence.conversationSummary();
        entity.turnCount = evidence.turnCount();
        entity.continuationRate = evidence.continuationRate();
        entity.avgResponseLength = evidence.avgResponseLength();
        entity.meanAffectShift = evidence.meanAffectShift();
        entity.dimensionSnapshots = evidence.dimensionSnapshots();
        entity.recordedAt = evidence.recordedAt();
        em.persist(entity);
    }

    @Override
    public int evidenceCount(String agentId, String tenantId) {
        return ((Number) em.createQuery(
                        "SELECT COUNT(e) FROM EngagementEvidenceEntity e WHERE e.agentId = :a AND e.tenantId = :t")
                .setParameter("a", agentId).setParameter("t", tenantId)
                .getSingleResult()).intValue();
    }

    @Override
    public List<EngagementEvidence> recentEvidence(String agentId, String tenantId, int limit) {
        return em.createQuery(
                        "SELECT e FROM EngagementEvidenceEntity e WHERE e.agentId = :a AND e.tenantId = :t ORDER BY e.recordedAt DESC",
                        EngagementEvidenceEntity.class)
                .setParameter("a", agentId).setParameter("t", tenantId)
                .setMaxResults(limit)
                .getResultList().stream().map(this::toEvidence).toList();
    }

    @Override
    public void eraseAgent(String agentId, String tenantId) {
        em.createQuery("DELETE FROM StrategyProfileEntity e WHERE e.agentId = :a AND e.tenantId = :t")
                .setParameter("a", agentId).setParameter("t", tenantId)
                .executeUpdate();
        em.createQuery("DELETE FROM EngagementEvidenceEntity e WHERE e.agentId = :a AND e.tenantId = :t")
                .setParameter("a", agentId).setParameter("t", tenantId)
                .executeUpdate();
    }

    @Override
    public void eraseSubject(String subjectId, String tenantId) {
        em.createQuery("DELETE FROM EngagementEvidenceEntity e WHERE e.subjectId = :s AND e.tenantId = :t")
                .setParameter("s", subjectId).setParameter("t", tenantId)
                .executeUpdate();
    }

    private StrategyProfileEntity findProfileEntity(String agentId, String tenantId) {
        var results = em.createQuery(
                        "SELECT e FROM StrategyProfileEntity e WHERE e.agentId = :a AND e.tenantId = :t",
                        StrategyProfileEntity.class)
                .setParameter("a", agentId).setParameter("t", tenantId)
                .getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    private void mapToEntity(StrategyProfile p, StrategyProfileEntity e) {
        e.agentId = p.agentId();
        e.tenantId = p.tenantId();
        e.dimensions = p.dimensions();
        e.guidelines = p.guidelines();
        e.lastReflection = p.lastReflection();
        e.evidenceCount = p.evidenceCount();
    }

    private StrategyProfile toDomain(StrategyProfileEntity e) {
        return new StrategyProfile(
                e.agentId, e.tenantId,
                e.dimensions, e.guidelines, e.lastReflection, e.evidenceCount);
    }

    private EngagementEvidence toEvidence(EngagementEvidenceEntity e) {
        return new EngagementEvidence(
                e.agentId, e.subjectId, e.tenantId,
                e.conversationId, e.conversationSummary,
                e.turnCount, e.continuationRate, e.avgResponseLength, e.meanAffectShift,
                e.dimensionSnapshots, e.recordedAt);
    }
}
