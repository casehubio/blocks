package io.casehub.blocks.trust;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.runtime.model.PlainLedgerEntry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

import java.util.UUID;

@ApplicationScoped
public class TrustEventRecorder {

    private final TrustEvolutionConfig config;
    private final LedgerEntryRepository ledgerRepo;

    @Inject
    public TrustEventRecorder(TrustEvolutionConfig config, LedgerEntryRepository ledgerRepo) {
        this.config = config;
        this.ledgerRepo = ledgerRepo;
    }

    public void onTrustRelevantAction(@ObservesAsync TrustRelevantAction event) {
        var mapping = config.findMapping(event.actionType());
        if (mapping.isEmpty()) return;

        var m = mapping.get();
        var entry = new PlainLedgerEntry();
        entry.actorId = event.actorId();
        entry.entryType = LedgerEntryType.EVENT;
        entry.subjectId = UUID.nameUUIDFromBytes(event.targetId().getBytes());
        var saved = ledgerRepo.save(entry, event.tenantId());

        createAttestation(saved.id, event.targetId(), m.verdict(), m.confidence(), event.tenantId());

        for (String witnessId : event.witnessIds()) {
            createAttestation(saved.id, witnessId, m.verdict(), m.witnessConfidence(), event.tenantId());
        }
    }

    private void createAttestation(UUID entryId, String attestorId,
                                   AttestationVerdict verdict, double confidence,
                                   String tenantId) {
        var attestation = new LedgerAttestation();
        attestation.ledgerEntryId = entryId;
        attestation.attestorId = attestorId;
        attestation.verdict = verdict;
        attestation.confidence = confidence;
        ledgerRepo.saveAttestation(attestation, tenantId);
    }
}
