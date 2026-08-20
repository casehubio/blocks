package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.FeatureVectorCbrCase;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CbrUserProfileStoreTest {

    @Mock
    CbrCaseMemoryStore cbrStore;
    CbrUserProfileStore store;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        store = new CbrUserProfileStore(cbrStore, UserModelConfig.defaults());
    }

    @Test
    void storeConvertsProfileToCbrCaseAndPersists() {
        var now = Instant.now();
        var profile = new UserProfile("agent-1", "user-1", "t1",
                "acquaintance", 0.3, 10, 7, 2, 1, now, now, null,
                "formal", "tech", null, null, Map.of());

        store.store(profile);

        var captor = ArgumentCaptor.forClass(CbrCase.class);
        verify(cbrStore).store(captor.capture(), eq("user-profile"), eq("agent-1"),
                any(MemoryDomain.class), eq("t1"), isNull(), any());
        var stored = captor.getValue();
        assertThat(stored.features().get("subject_id"))
                .isEqualTo(FeatureValue.string("user-1"));
        assertThat(stored.features().get("familiarity_score"))
                .isEqualTo(FeatureValue.number(0.3));
        assertThat(stored.features().get("relationship_stage"))
                .isEqualTo(FeatureValue.string("acquaintance"));
        assertThat(stored.features().get("communication_style"))
                .isEqualTo(FeatureValue.string("formal"));
    }

    @Test
    void lookupReturnsEmptyWhenNoProfile() {
        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of());

        var result = store.lookup("agent-1", "user-1", "t1");
        assertThat(result).isEmpty();
    }

    @Test
    void lookupReconstructsProfileFromCbrCase() {
        var now = Instant.now();
        var features = Map.<String, FeatureValue>of(
                "subject_id", FeatureValue.string("user-1"),
                "relationship_stage", FeatureValue.string("friend"),
                "familiarity_score", FeatureValue.number(0.65),
                "total_interactions", FeatureValue.number(50),
                "positive_signals", FeatureValue.number(35),
                "negative_signals", FeatureValue.number(5),
                "neutral_signals", FeatureValue.number(10));

        CbrCase cbrCase = new FeatureVectorCbrCase(
                "Profile for user-1", "-", null, null, features, null, "agent-1");

        var scored = new ScoredCbrCase<>(cbrCase, "case-1", 1.0);

        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of(scored));

        var result = store.lookup("agent-1", "user-1", "t1");
        assertThat(result).isPresent();
        var profile = result.get();
        assertThat(profile.subjectId()).isEqualTo("user-1");
        assertThat(profile.relationshipStage()).isEqualTo("friend");
        assertThat(profile.familiarityScore()).isCloseTo(0.65, org.assertj.core.data.Offset.offset(0.001));
        assertThat(profile.totalInteractions()).isEqualTo(50);
    }

    @Test
    void eraseSubjectCallsEraseOnStore() {
        var now = Instant.now();
        var features = Map.<String, FeatureValue>of(
                "subject_id", FeatureValue.string("user-1"),
                "relationship_stage", FeatureValue.string("stranger"),
                "familiarity_score", FeatureValue.number(0.0),
                "total_interactions", FeatureValue.number(0),
                "positive_signals", FeatureValue.number(0),
                "negative_signals", FeatureValue.number(0),
                "neutral_signals", FeatureValue.number(0));

        CbrCase cbrCase = new FeatureVectorCbrCase(
                "Profile for user-1", "-", null, null, features, null, "agent-1");

        var scored = new ScoredCbrCase<>(cbrCase, "case-1", 1.0);

        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of(scored));

        store.eraseSubject("user-1", "t1");

        verify(cbrStore).erase(any(EraseRequest.class));
    }
}
