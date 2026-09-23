package io.casehub.blocks.social.jpa.spring;

import io.casehub.blocks.agentic.social.EngagementEvidence;
import io.casehub.blocks.agentic.social.StrategyProfile;
import io.casehub.blocks.agentic.social.StrategyStore;
import io.casehub.blocks.social.jpa.EngagementEvidenceEntity;
import io.casehub.blocks.social.jpa.StrategyProfileEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional
public class SpringStrategyStore implements StrategyStore {

    private final StrategyProfileEntityRepository profileRepo;
    private final EngagementEvidenceEntityRepository evidenceRepo;

    public SpringStrategyStore(StrategyProfileEntityRepository profileRepo,
                                EngagementEvidenceEntityRepository evidenceRepo) {
        this.profileRepo = profileRepo;
        this.evidenceRepo = evidenceRepo;
    }

    @Override
    public void store(StrategyProfile profile) {
        var entity = profileRepo.findByAgentIdAndTenantId(profile.agentId(), profile.tenantId())
                .orElse(null);
        if (entity != null) {
            mapToEntity(profile, entity);
        } else {
            entity = new StrategyProfileEntity();
            entity.id = UUID.randomUUID().toString();
            mapToEntity(profile, entity);
        }
        profileRepo.save(entity);
    }

    @Override
    public Optional<StrategyProfile> lookup(String agentId, String tenantId) {
        return profileRepo.findByAgentIdAndTenantId(agentId, tenantId)
                .map(this::toDomain);
    }

    @Override
    public List<String> subjectInsights(String agentId, String subjectId, String tenantId) {
        var results = evidenceRepo.findByAgentIdAndSubjectIdAndTenantIdOrderByRecordedAtDesc(
                agentId, subjectId, tenantId, PageRequest.of(0, 10));
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
        evidenceRepo.save(entity);
    }

    @Override
    public int evidenceCount(String agentId, String tenantId) {
        return evidenceRepo.countByAgentIdAndTenantId(agentId, tenantId);
    }

    @Override
    public List<EngagementEvidence> recentEvidence(String agentId, String tenantId, int limit) {
        return evidenceRepo.findByAgentIdAndTenantIdOrderByRecordedAtDesc(
                agentId, tenantId, PageRequest.of(0, limit))
                .stream().map(this::toEvidence).toList();
    }

    @Override
    public void eraseAgent(String agentId, String tenantId) {
        profileRepo.deleteByAgentIdAndTenantId(agentId, tenantId);
        evidenceRepo.deleteByAgentIdAndTenantId(agentId, tenantId);
    }

    @Override
    public void eraseSubject(String subjectId, String tenantId) {
        evidenceRepo.deleteBySubjectIdAndTenantId(subjectId, tenantId);
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
