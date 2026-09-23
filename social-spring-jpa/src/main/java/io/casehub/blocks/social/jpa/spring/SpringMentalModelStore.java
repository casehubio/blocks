package io.casehub.blocks.social.jpa.spring;

import io.casehub.blocks.agentic.social.MentalModelSnapshot;
import io.casehub.blocks.agentic.social.MentalModelStore;
import io.casehub.blocks.social.jpa.MentalModelEntity;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional
public class SpringMentalModelStore implements MentalModelStore {

    private final MentalModelEntityRepository repo;

    public SpringMentalModelStore(MentalModelEntityRepository repo) {
        this.repo = repo;
    }

    @Override
    public void store(MentalModelSnapshot snapshot) {
        var entity = repo.findByAgentIdAndSubjectIdAndTenantId(
                snapshot.agentId(), snapshot.subjectId(), snapshot.tenantId()).orElse(null);
        if (entity != null) {
            mapToEntity(snapshot, entity);
        } else {
            entity = new MentalModelEntity();
            entity.id = UUID.randomUUID().toString();
            mapToEntity(snapshot, entity);
        }
        repo.save(entity);
    }

    @Override
    public Optional<MentalModelSnapshot> lookup(String agentId, String subjectId, String tenantId) {
        return repo.findByAgentIdAndSubjectIdAndTenantId(agentId, subjectId, tenantId)
                .map(this::toDomain);
    }

    @Override
    public List<MentalModelSnapshot> findByAgent(String agentId, String tenantId) {
        return repo.findByAgentIdAndTenantId(agentId, tenantId).stream()
                .map(this::toDomain).toList();
    }

    @Override
    public void eraseSubject(String subjectId, String tenantId) {
        repo.deleteBySubjectIdAndTenantId(subjectId, tenantId);
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
