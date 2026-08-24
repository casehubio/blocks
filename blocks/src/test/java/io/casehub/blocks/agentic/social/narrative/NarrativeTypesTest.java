package io.casehub.blocks.agentic.social.narrative;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class NarrativeTypesTest {

    private final Instant now = Instant.parse("2026-08-24T12:00:00Z");

    @Test
    void individualEpisode_validConstruction() {
        var episode = new IndividualEpisode("e1", now, null,
                List.of("crisis", "teamwork"), "Helped team through crisis",
                0.8, List.of("r1", "r2"));
        assertThat(episode.id()).isEqualTo("e1");
        assertThat(episode.emotionalValence()).isEqualTo(0.8);
        assertThat(episode.thematicTags()).containsExactly("crisis", "teamwork");
        assertThat(episode.sourceReflectionIds()).containsExactly("r1", "r2");
    }

    @Test
    void individualEpisode_rejectsValenceAboveOne() {
        assertThatThrownBy(() -> new IndividualEpisode("e1", now, null,
                List.of(), "desc", 1.5, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("emotionalValence");
    }

    @Test
    void individualEpisode_rejectsValenceBelowNegativeOne() {
        assertThatThrownBy(() -> new IndividualEpisode("e1", now, null,
                List.of(), "desc", -1.5, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void individualEpisode_allowsBoundaryValues() {
        assertThatCode(() -> new IndividualEpisode("e1", now, null,
                List.of(), "desc", 1.0, List.of())).doesNotThrowAnyException();
        assertThatCode(() -> new IndividualEpisode("e2", now, null,
                List.of(), "desc", -1.0, List.of())).doesNotThrowAnyException();
    }

    @Test
    void individualEpisode_defensiveCopy() {
        var tags = new ArrayList<>(List.of("a"));
        var episode = new IndividualEpisode("e1", now, null, tags, "desc", 0.0, List.of());
        tags.add("b");
        assertThat(episode.thematicTags()).containsExactly("a");
    }

    @Test
    void individualEpisode_rejectsNullId() {
        assertThatThrownBy(() -> new IndividualEpisode(null, now, null,
                List.of(), "desc", 0.0, List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void groupEpisode_validConstruction() {
        var episode = new GroupEpisode("g1", now, null, List.of("collaboration"),
                "Joint investigation", 0.6,
                Set.of("agent-a", "agent-b"),
                Map.of("agent-a", "lead", "agent-b", "support"), 0.9);
        assertThat(episode.membershipAtTime()).containsExactlyInAnyOrder("agent-a", "agent-b");
        assertThat(episode.roleAttributions()).hasSize(2);
        assertThat(episode.consensusLevel()).isEqualTo(0.9);
    }

    @Test
    void groupEpisode_rejectsConsensusAboveOne() {
        assertThatThrownBy(() -> new GroupEpisode("g1", now, null, List.of(),
                "desc", 0.0, Set.of(), Map.of(), 1.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("consensusLevel");
    }

    @Test
    void groupEpisode_rejectsNegativeConsensus() {
        assertThatThrownBy(() -> new GroupEpisode("g1", now, null, List.of(),
                "desc", 0.0, Set.of(), Map.of(), -0.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void groupEpisode_defensiveCopyMembership() {
        var members = new java.util.HashSet<>(Set.of("a"));
        var episode = new GroupEpisode("g1", now, null, List.of(),
                "desc", 0.0, members, Map.of(), 0.5);
        members.add("b");
        assertThat(episode.membershipAtTime()).containsExactly("a");
    }

    @Test
    void derivedTheme_validConstruction() {
        var theme = new DerivedTheme("t1", now, null, List.of("helper"),
                "crisis-helper", 0.8,
                Map.of(DriveAxis.AFFILIATION, 0.5, DriveAxis.COMPETENCE, 0.3),
                List.of("e1", "e2"));
        assertThat(theme.label()).isEqualTo("crisis-helper");
        assertThat(theme.salience()).isEqualTo(0.8);
        assertThat(theme.axisModulationWeights()).hasSize(2);
    }

    @Test
    void derivedTheme_rejectsSalienceAboveOne() {
        assertThatThrownBy(() -> new DerivedTheme("t1", now, null, List.of(),
                "label", 1.5, Map.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("salience");
    }

    @Test
    void derivedTheme_rejectsModulationWeightAboveOne() {
        assertThatThrownBy(() -> new DerivedTheme("t1", now, null, List.of(),
                "label", 0.5,
                Map.of(DriveAxis.CURIOSITY, 2.0), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("axis modulation weight");
    }

    @Test
    void derivedTheme_rejectsModulationWeightBelowNegativeOne() {
        assertThatThrownBy(() -> new DerivedTheme("t1", now, null, List.of(),
                "label", 0.5,
                Map.of(DriveAxis.CURIOSITY, -1.5), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void derivedTheme_allowsNegativeModulationWeight() {
        var theme = new DerivedTheme("t1", now, null, List.of(),
                "label", 0.5,
                Map.of(DriveAxis.AUTONOMY, -0.5), List.of());
        assertThat(theme.axisModulationWeights().get(DriveAxis.AUTONOMY)).isEqualTo(-0.5);
    }

    @Test
    void narrativeState_typedAccessors() {
        var episode = new IndividualEpisode("e1", now, null, List.of(),
                "episode", 0.5, List.of());
        var group = new GroupEpisode("g1", now, null, List.of(),
                "group ep", 0.3, Set.of("a"), Map.of(), 0.8);
        var theme1 = new DerivedTheme("t1", now, null, List.of(),
                "helper", 0.9, Map.of(), List.of());
        var theme2 = new DerivedTheme("t2", now, null, List.of(),
                "expert", 0.4, Map.of(), List.of());

        var state = new NarrativeState("agent-1", "tenant-1",
                NarrativeScope.INDIVIDUAL,
                List.of(episode, group, theme1, theme2), now, 10);

        assertThat(state.episodes()).hasSize(1);
        assertThat(state.groupEpisodes()).hasSize(1);
        assertThat(state.themes()).hasSize(2);
        assertThat(state.dominantTheme().label()).isEqualTo("helper");
    }

    @Test
    void narrativeState_dominantTheme_empty() {
        var state = new NarrativeState("a", "t", NarrativeScope.INDIVIDUAL,
                List.of(), now, 0);
        assertThat(state.dominantTheme()).isNull();
    }

    @Test
    void narrativeState_defensiveCopy() {
        var fragments = new ArrayList<NarrativeFragment>();
        var episode = new IndividualEpisode("e1", now, null, List.of(),
                "desc", 0.0, List.of());
        fragments.add(episode);
        var state = new NarrativeState("a", "t", NarrativeScope.INDIVIDUAL,
                fragments, now, 1);
        fragments.add(new IndividualEpisode("e2", now, null, List.of(),
                "other", 0.0, List.of()));
        assertThat(state.fragments()).hasSize(1);
    }

    @Test
    void narrativeScope_values() {
        assertThat(NarrativeScope.values()).containsExactly(
                NarrativeScope.INDIVIDUAL, NarrativeScope.GROUP);
    }

    @Test
    void sealedHierarchy_permits() {
        assertThat(NarrativeFragment.class.getPermittedSubclasses()).hasSize(3);
    }

    @Test
    void narrativeState_rejectsNullScopeId() {
        assertThatThrownBy(() -> new NarrativeState(null, "t",
                NarrativeScope.INDIVIDUAL, List.of(), now, 0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void narrativeState_rejectsNullScope() {
        assertThatThrownBy(() -> new NarrativeState("a", "t",
                null, List.of(), now, 0))
                .isInstanceOf(NullPointerException.class);
    }
}
