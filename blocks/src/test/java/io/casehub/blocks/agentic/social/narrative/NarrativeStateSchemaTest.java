package io.casehub.blocks.agentic.social.narrative;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NarrativeStateSchemaTest {

    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");
    private static final Instant EARLIER = Instant.parse("2026-08-20T08:00:00Z");

    @Test
    void episodeRoundTrip() {
        var episode = new IndividualEpisode("e1", EARLIER, NOW,
                List.of("growth", "challenge"), "Helped resolve a crisis",
                0.7, List.of("r1", "r2"));
        var fragments = List.<NarrativeFragment>of(episode);

        var json = NarrativeStateSchema.serializeFragments(fragments);
        var deserialized = NarrativeStateSchema.deserializeFragments(json);

        assertThat(deserialized).hasSize(1);
        assertThat(deserialized.getFirst()).isInstanceOf(IndividualEpisode.class);
        var result = (IndividualEpisode) deserialized.getFirst();
        assertThat(result.id()).isEqualTo("e1");
        assertThat(result.from()).isEqualTo(EARLIER);
        assertThat(result.to()).isEqualTo(NOW);
        assertThat(result.thematicTags()).containsExactly("growth", "challenge");
        assertThat(result.description()).isEqualTo("Helped resolve a crisis");
        assertThat(result.emotionalValence()).isEqualTo(0.7);
        assertThat(result.sourceReflectionIds()).containsExactly("r1", "r2");
    }

    @Test
    void groupEpisodeRoundTrip() {
        var group = new GroupEpisode("g1", EARLIER, null,
                List.of("teamwork"), "Collaborative debugging session",
                0.4, Set.of("agent1", "agent2"),
                Map.of("agent1", "leader", "agent2", "supporter"), 0.85);
        var fragments = List.<NarrativeFragment>of(group);

        var json = NarrativeStateSchema.serializeFragments(fragments);
        var deserialized = NarrativeStateSchema.deserializeFragments(json);

        assertThat(deserialized).hasSize(1);
        assertThat(deserialized.getFirst()).isInstanceOf(GroupEpisode.class);
        var result = (GroupEpisode) deserialized.getFirst();
        assertThat(result.id()).isEqualTo("g1");
        assertThat(result.from()).isEqualTo(EARLIER);
        assertThat(result.to()).isNull();
        assertThat(result.thematicTags()).containsExactly("teamwork");
        assertThat(result.description()).isEqualTo("Collaborative debugging session");
        assertThat(result.emotionalValence()).isEqualTo(0.4);
        assertThat(result.membershipAtTime()).containsExactlyInAnyOrder("agent1", "agent2");
        assertThat(result.roleAttributions()).containsEntry("agent1", "leader")
                .containsEntry("agent2", "supporter");
        assertThat(result.consensusLevel()).isEqualTo(0.85);
    }

    @Test
    void themeRoundTrip() {
        var theme = new DerivedTheme("t1", EARLIER, null,
                List.of("helper"), "crisis-helper", 0.9,
                Map.of(DriveAxis.CURIOSITY, 0.3, DriveAxis.AFFILIATION, 0.5),
                List.of("e1", "g1"));
        var fragments = List.<NarrativeFragment>of(theme);

        var json = NarrativeStateSchema.serializeFragments(fragments);
        var deserialized = NarrativeStateSchema.deserializeFragments(json);

        assertThat(deserialized).hasSize(1);
        assertThat(deserialized.getFirst()).isInstanceOf(DerivedTheme.class);
        var result = (DerivedTheme) deserialized.getFirst();
        assertThat(result.id()).isEqualTo("t1");
        assertThat(result.label()).isEqualTo("crisis-helper");
        assertThat(result.salience()).isEqualTo(0.9);
        assertThat(result.axisModulationWeights())
                .containsEntry(DriveAxis.CURIOSITY, 0.3)
                .containsEntry(DriveAxis.AFFILIATION, 0.5);
        assertThat(result.supportingFragmentIds()).containsExactly("e1", "g1");
    }

    @Test
    void mixedFragmentsRoundTrip() {
        var episode = new IndividualEpisode("e1", EARLIER, null,
                List.of("growth"), "Learned a new skill", 0.6, List.of("r1"));
        var group = new GroupEpisode("g1", EARLIER, NOW,
                List.of("team"), "Joint project", 0.5,
                Set.of("a1", "a2"), Map.of("a1", "dev"), 0.9);
        var theme = new DerivedTheme("t1", EARLIER, null,
                List.of("expert"), "technical-expert", 0.8,
                Map.of(DriveAxis.COMPETENCE, 0.7), List.of("e1"));
        var fragments = List.<NarrativeFragment>of(episode, group, theme);

        var json = NarrativeStateSchema.serializeFragments(fragments);
        var deserialized = NarrativeStateSchema.deserializeFragments(json);

        assertThat(deserialized).hasSize(3);
        assertThat(deserialized.get(0)).isInstanceOf(IndividualEpisode.class);
        assertThat(deserialized.get(1)).isInstanceOf(GroupEpisode.class);
        assertThat(deserialized.get(2)).isInstanceOf(DerivedTheme.class);
    }

    @Test
    void emptyFragmentsRoundTrip() {
        assertThat(NarrativeStateSchema.deserializeFragments("[]")).isEmpty();
        assertThat(NarrativeStateSchema.deserializeFragments("")).isEmpty();
        assertThat(NarrativeStateSchema.deserializeFragments(null)).isEmpty();
    }

    @Test
    void specialCharactersInDescriptionRoundTrip() {
        var episode = new IndividualEpisode("e1", NOW, null,
                List.of("tag"), "Description with \"quotes\" and \\backslash",
                0.0, List.of());

        var json = NarrativeStateSchema.serializeFragments(List.of(episode));
        var deserialized = NarrativeStateSchema.deserializeFragments(json);

        assertThat(deserialized).hasSize(1);
        var result = (IndividualEpisode) deserialized.getFirst();
        assertThat(result.description()).isEqualTo("Description with \"quotes\" and \\backslash");
    }

    @Test
    void negativeEmotionalValenceRoundTrip() {
        var episode = new IndividualEpisode("e1", NOW, null,
                List.of(), "A setback", -0.8, List.of());

        var json = NarrativeStateSchema.serializeFragments(List.of(episode));
        var deserialized = NarrativeStateSchema.deserializeFragments(json);

        var result = (IndividualEpisode) deserialized.getFirst();
        assertThat(result.emotionalValence()).isEqualTo(-0.8);
    }

    @Test
    void negativeAxisWeightsRoundTrip() {
        var theme = new DerivedTheme("t1", NOW, null,
                List.of(), "dampener", 0.5,
                Map.of(DriveAxis.AUTONOMY, -0.4, DriveAxis.CURIOSITY, 0.2),
                List.of());

        var json = NarrativeStateSchema.serializeFragments(List.of(theme));
        var deserialized = NarrativeStateSchema.deserializeFragments(json);

        var result = (DerivedTheme) deserialized.getFirst();
        assertThat(result.axisModulationWeights())
                .containsEntry(DriveAxis.AUTONOMY, -0.4)
                .containsEntry(DriveAxis.CURIOSITY, 0.2);
    }

    @Test
    void fullStateRoundTrip() {
        var episode = new IndividualEpisode("e1", EARLIER, NOW,
                List.of("growth"), "Resolved issue", 0.6, List.of("r1"));
        var theme = new DerivedTheme("t1", EARLIER, null,
                List.of("expert"), "problem-solver", 0.85,
                Map.of(DriveAxis.COMPETENCE, 0.6), List.of("e1"));
        var state = new NarrativeState("agent1", "tenant1",
                NarrativeScope.INDIVIDUAL, List.of(episode, theme), NOW, 5);

        var features = NarrativeStateSchema.toFeatures(state);
        var summary = NarrativeStateSchema.toSummary(state);

        assertThat(summary).contains("agent1").contains("INDIVIDUAL")
                .contains("episodes=1").contains("themes=1");
        assertThat(features).containsKey(NarrativeStateSchema.SCOPE_ID);
        assertThat(features).containsKey(NarrativeStateSchema.FRAGMENTS_JSON);
    }

    @Test
    void emptyWeightsRoundTrip() {
        var theme = new DerivedTheme("t1", NOW, null,
                List.of(), "neutral-theme", 0.3, Map.of(), List.of());

        var json = NarrativeStateSchema.serializeFragments(List.of(theme));
        var deserialized = NarrativeStateSchema.deserializeFragments(json);

        var result = (DerivedTheme) deserialized.getFirst();
        assertThat(result.axisModulationWeights()).isEmpty();
    }

    @Test
    void emptyMembershipAndRolesRoundTrip() {
        var group = new GroupEpisode("g1", NOW, null,
                List.of(), "Empty group", 0.0,
                Set.of(), Map.of(), 1.0);

        var json = NarrativeStateSchema.serializeFragments(List.of(group));
        var deserialized = NarrativeStateSchema.deserializeFragments(json);

        var result = (GroupEpisode) deserialized.getFirst();
        assertThat(result.membershipAtTime()).isEmpty();
        assertThat(result.roleAttributions()).isEmpty();
    }
}
