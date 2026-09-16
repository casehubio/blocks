package io.casehub.blocks.trust;

import io.casehub.blocks.trust.TrustEvolutionConfig.ConsolidationConfig;
import io.casehub.blocks.trust.TrustEvolutionConfig.LevelConfig;
import io.casehub.blocks.trust.TrustEvolutionConfig.ScoringConfig;
import io.casehub.blocks.trust.TrustEvolutionConfig.TrustEventMapping;
import io.casehub.ledger.api.model.AttestationSummary;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TrustEventRecorderTest {

    private CapturingLedgerRepo repo;
    private TrustEventRecorder recorder;

    @BeforeEach
    void setUp() {
        repo = new CapturingLedgerRepo();
        var config = new TrustEvolutionConfig(
            List.of(
                new TrustEventMapping("STEAL", AttestationVerdict.FLAGGED, 0.9, 0.5),
                new TrustEventMapping("GIVE", AttestationVerdict.SOUND, 0.7, 0.3)),
            new ScoringConfig(30, 1.5),
            new ConsolidationConfig("trust-score", 0.15),
            new LevelConfig(0.7, 0.4, 0.2));
        recorder = new TrustEventRecorder(config, repo);
    }

    @Test
    void createsEntryAndAttestationsForMappedAction() {
        var event = new TrustRelevantAction(
            "hooded-claw", "penelope-pitstop", "STEAL", "success",
            List.of("peter-perfect"), "test-tenant");
        recorder.onTrustRelevantAction(event);

        assertThat(repo.savedEntries).hasSize(1);
        LedgerEntry entry = repo.savedEntries.get(0);
        assertThat(entry.actorId).isEqualTo("hooded-claw");
        assertThat(entry.entryType).isEqualTo(LedgerEntryType.EVENT);
        assertThat(entry.subjectId).isEqualTo(
            UUID.nameUUIDFromBytes("penelope-pitstop".getBytes()));

        assertThat(repo.savedAttestations).hasSize(2);

        var targetAttestation = repo.savedAttestations.stream()
            .filter(a -> "penelope-pitstop".equals(a.attestorId)).findFirst().orElseThrow();
        assertThat(targetAttestation.verdict).isEqualTo(AttestationVerdict.FLAGGED);
        assertThat(targetAttestation.confidence).isEqualTo(0.9);

        var witnessAttestation = repo.savedAttestations.stream()
            .filter(a -> "peter-perfect".equals(a.attestorId)).findFirst().orElseThrow();
        assertThat(witnessAttestation.verdict).isEqualTo(AttestationVerdict.FLAGGED);
        assertThat(witnessAttestation.confidence).isEqualTo(0.5);
    }

    @Test
    void ignoresUnmappedActionType() {
        var event = new TrustRelevantAction(
            "penelope-pitstop", "hooded-claw", "MOVE", "success",
            List.of(), "test-tenant");
        recorder.onTrustRelevantAction(event);

        assertThat(repo.savedEntries).isEmpty();
        assertThat(repo.savedAttestations).isEmpty();
    }

    @Test
    void noWitnessesProducesOnlyTargetAttestation() {
        var event = new TrustRelevantAction(
            "hooded-claw", "penelope-pitstop", "STEAL", "success",
            List.of(), "test-tenant");
        recorder.onTrustRelevantAction(event);

        assertThat(repo.savedAttestations).hasSize(1);
        assertThat(repo.savedAttestations.get(0).attestorId).isEqualTo("penelope-pitstop");
    }

    @Test
    void positiveActionCreatesSOUNDVerdict() {
        var event = new TrustRelevantAction(
            "penelope-pitstop", "peter-perfect", "GIVE", "success",
            List.of(), "test-tenant");
        recorder.onTrustRelevantAction(event);

        assertThat(repo.savedAttestations).hasSize(1);
        assertThat(repo.savedAttestations.get(0).verdict).isEqualTo(AttestationVerdict.SOUND);
        assertThat(repo.savedAttestations.get(0).confidence).isEqualTo(0.7);
    }

    @Test
    void attestationsLinkedToCorrectEntry() {
        var event = new TrustRelevantAction(
            "hooded-claw", "penelope-pitstop", "STEAL", "success",
            List.of("peter-perfect"), "test-tenant");
        recorder.onTrustRelevantAction(event);

        UUID entryId = repo.savedEntries.get(0).id;
        assertThat(repo.savedAttestations).allMatch(a -> entryId.equals(a.ledgerEntryId));
    }

    static class CapturingLedgerRepo implements LedgerEntryRepository {
        final List<LedgerEntry> savedEntries = new ArrayList<>();
        final List<LedgerAttestation> savedAttestations = new ArrayList<>();

        @Override
        public LedgerEntry save(LedgerEntry entry, String tenancyId) {
            if (entry.id == null) entry.id = UUID.randomUUID();
            savedEntries.add(entry);
            return entry;
        }

        @Override
        public LedgerAttestation saveAttestation(LedgerAttestation a, String tenancyId) {
            if (a.id == null) a.id = UUID.randomUUID();
            savedAttestations.add(a);
            return a;
        }

        @Override public List<LedgerEntry> findBySubjectId(UUID s, String t) { return List.of(); }
        @Override public List<LedgerEntry> findBySubjectIdAndTimeRange(UUID s, Instant f, Instant to, String t) { return List.of(); }
        @Override public Optional<LedgerEntry> findLatestBySubjectId(UUID s, String t) { return Optional.empty(); }
        @Override public Optional<LedgerEntry> findEntryById(UUID i, String t) { return Optional.empty(); }
        @Override public List<LedgerAttestation> findAttestationsByEntryId(UUID e, String t) { return List.of(); }
        @Override public List<LedgerEntry> findByActorId(String a, Instant f, Instant t, String te) { return List.of(); }
        @Override public List<LedgerEntry> findByActorRole(String r, Instant f, Instant t, String te) { return List.of(); }
        @Override public List<LedgerEntry> findCausedBy(UUID e, String t) { return List.of(); }
        @Override public List<LedgerAttestation> findAttestationsByEntryIdAndCapabilityTag(UUID e, String c, String t) { return List.of(); }
        @Override public List<LedgerAttestation> findAttestationsByEntryIdGlobal(UUID e, String t) { return List.of(); }
        @Override public List<LedgerAttestation> findAttestationsByAttestorIdAndCapabilityTag(String a, String c, String t) { return List.of(); }
        @Override public Stream<LedgerEntry> streamBySubjectId(UUID s, String t) { return Stream.empty(); }
        @Override public Stream<LedgerEntry> streamByActorId(String a, Instant f, Instant t, String te) { return Stream.empty(); }
        @Override public List<LedgerEntry> findBySubjectIdPaged(UUID s, int a, int l, String t) { return List.of(); }
        @Override public Map<AttestationVerdict, Long> countByActorAndVerdict(String a, Instant f, Instant t, String te) { return Map.of(); }
        @Override public Map<AttestationVerdict, Long> countBySubjectAndVerdict(UUID s, Instant f, Instant t, String te) { return Map.of(); }
        @Override public AttestationSummary summariseAttestationsByActor(String a, Instant f, Instant t, String te) { return AttestationSummary.EMPTY; }
    }
}
