package io.casehub.blocks.social.jpa.quarkus;

import io.casehub.blocks.agentic.social.UserProfile;
import io.casehub.blocks.agentic.social.UserProfileStore;
import io.casehub.blocks.social.jpa.UserProfileEntity;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Alternative
@jakarta.annotation.Priority(3)
@jakarta.enterprise.context.ApplicationScoped
@Transactional
public class JpaUserProfileStore implements UserProfileStore {

    @Inject
    EntityManager em;

    @Override
    public void store(UserProfile profile) {
        var existing = findEntity(profile.agentId(), profile.subjectId(), profile.tenantId());
        if (existing != null) {
            mapToEntity(profile, existing);
        } else {
            var entity = new UserProfileEntity();
            entity.id = UUID.randomUUID().toString();
            mapToEntity(profile, entity);
            em.persist(entity);
        }
    }

    @Override
    public Optional<UserProfile> lookup(String agentId, String subjectId, String tenantId) {
        var entity = findEntity(agentId, subjectId, tenantId);
        return entity == null ? Optional.empty() : Optional.of(toDomain(entity));
    }

    @Override
    public List<UserProfile> findByAgent(String agentId, String tenantId) {
        return em.createQuery(
                        "SELECT e FROM UserProfileEntity e WHERE e.agentId = :a AND e.tenantId = :t",
                        UserProfileEntity.class)
                .setParameter("a", agentId).setParameter("t", tenantId)
                .getResultList().stream().map(this::toDomain).toList();
    }

    @Override
    public void eraseSubject(String subjectId, String tenantId) {
        em.createQuery("DELETE FROM UserProfileEntity e WHERE e.subjectId = :s AND e.tenantId = :t")
                .setParameter("s", subjectId).setParameter("t", tenantId)
                .executeUpdate();
    }

    private UserProfileEntity findEntity(String agentId, String subjectId, String tenantId) {
        var results = em.createQuery(
                        "SELECT e FROM UserProfileEntity e WHERE e.agentId = :a AND e.subjectId = :s AND e.tenantId = :t",
                        UserProfileEntity.class)
                .setParameter("a", agentId).setParameter("s", subjectId).setParameter("t", tenantId)
                .getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    private void mapToEntity(UserProfile p, UserProfileEntity e) {
        e.agentId = p.agentId();
        e.subjectId = p.subjectId();
        e.tenantId = p.tenantId();
        e.relationshipStage = p.relationshipStage();
        e.familiarityScore = p.familiarityScore();
        e.totalInteractions = p.totalInteractions();
        e.positiveSignals = p.positiveSignals();
        e.negativeSignals = p.negativeSignals();
        e.neutralSignals = p.neutralSignals();
        e.lastInteraction = p.lastInteraction();
        e.profileCreated = p.profileCreated();
        e.lastSynthesised = p.lastSynthesised();
        e.communicationStyle = p.communicationStyle();
        e.topicsOfInterest = p.topicsOfInterest();
        e.preferences = p.preferences();
        e.synthesisNotes = p.synthesisNotes();
        e.metadata = p.metadata() != null ? p.metadata() : Map.of();
    }

    private UserProfile toDomain(UserProfileEntity e) {
        return new UserProfile(
                e.agentId, e.subjectId, e.tenantId,
                e.relationshipStage, e.familiarityScore,
                e.totalInteractions, e.positiveSignals, e.negativeSignals, e.neutralSignals,
                e.lastInteraction, e.profileCreated, e.lastSynthesised,
                e.communicationStyle, e.topicsOfInterest, e.preferences, e.synthesisNotes,
                e.metadata);
    }
}
