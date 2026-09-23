package io.casehub.blocks.social.jpa.spring;

import io.casehub.blocks.agentic.social.UserProfile;
import io.casehub.blocks.agentic.social.UserProfileStore;
import io.casehub.blocks.social.jpa.UserProfileEntity;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Transactional
public class SpringUserProfileStore implements UserProfileStore {

    private final UserProfileEntityRepository repo;

    public SpringUserProfileStore(UserProfileEntityRepository repo) {
        this.repo = repo;
    }

    @Override
    public void store(UserProfile profile) {
        var entity = repo.findByAgentIdAndSubjectIdAndTenantId(
                profile.agentId(), profile.subjectId(), profile.tenantId()).orElse(null);
        if (entity != null) {
            mapToEntity(profile, entity);
        } else {
            entity = new UserProfileEntity();
            entity.id = UUID.randomUUID().toString();
            mapToEntity(profile, entity);
        }
        repo.save(entity);
    }

    @Override
    public Optional<UserProfile> lookup(String agentId, String subjectId, String tenantId) {
        return repo.findByAgentIdAndSubjectIdAndTenantId(agentId, subjectId, tenantId)
                .map(this::toDomain);
    }

    @Override
    public List<UserProfile> findByAgent(String agentId, String tenantId) {
        return repo.findByAgentIdAndTenantId(agentId, tenantId).stream()
                .map(this::toDomain).toList();
    }

    @Override
    public void eraseSubject(String subjectId, String tenantId) {
        repo.deleteBySubjectIdAndTenantId(subjectId, tenantId);
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
