package io.casehub.blocks.social.jpa;

import com.fasterxml.jackson.core.type.TypeReference;
import io.casehub.blocks.agentic.social.AttributedState;
import io.casehub.blocks.agentic.social.BdiDimension;
import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.social.narrative.DerivedTheme;
import io.casehub.blocks.agentic.social.narrative.GroupEpisode;
import io.casehub.blocks.agentic.social.narrative.IndividualEpisode;
import io.casehub.blocks.agentic.social.narrative.NarrativeFragment;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SocialJsonMapperTest {

    @Test
    void roundTripStringDoubleMap() {
        var map = Map.of("verbosity", 0.6, "formality", 0.8);
        var json = SocialJsonMapper.toJson(map);
        Map<String, Double> result = SocialJsonMapper.fromJson(json, new TypeReference<>() {});
        assertThat(result).isEqualTo(map);
    }

    @Test
    void roundTripStringList() {
        var list = List.of("guideline 1", "guideline 2");
        var json = SocialJsonMapper.toJson(list);
        List<String> result = SocialJsonMapper.fromJson(json, new TypeReference<>() {});
        assertThat(result).isEqualTo(list);
    }

    @Test
    void roundTripStringStringMap() {
        var map = Map.of("key1", "val1", "key2", "val2");
        var json = SocialJsonMapper.toJson(map);
        Map<String, String> result = SocialJsonMapper.fromJson(json, new TypeReference<>() {});
        assertThat(result).isEqualTo(map);
    }

    @Test
    void roundTripAttributedStateList() {
        var now = Instant.now();
        var states = List.of(
                new AttributedState("k1", "desc", 0.8, 3, now, BdiDimension.BELIEF),
                new AttributedState("k2", "desc2", 0.5, 1, now, BdiDimension.DESIRE));
        var json = SocialJsonMapper.toJson(states);
        List<AttributedState> result = SocialJsonMapper.fromJson(json, new TypeReference<>() {});
        assertThat(result).hasSize(2);
        assertThat(result.get(0).key()).isEqualTo("k1");
        assertThat(result.get(0).dimension()).isEqualTo(BdiDimension.BELIEF);
    }

    @Test
    void roundTripNarrativeFragmentList_individualEpisode() {
        var now = Instant.now();
        List<NarrativeFragment> fragments = List.of(
                new IndividualEpisode("ep1", now, null, List.of("tag1"), "desc", 0.5, List.of("r1")));
        var json = SocialJsonMapper.toJson(fragments, new TypeReference<>() {});
        assertThat(json).contains("\"@type\":\"individual\"");
        List<NarrativeFragment> result = SocialJsonMapper.fromJson(json, new TypeReference<>() {});
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isInstanceOf(IndividualEpisode.class);
    }

    @Test
    void roundTripNarrativeFragmentList_groupEpisode() {
        var now = Instant.now();
        List<NarrativeFragment> fragments = List.of(
                new GroupEpisode("g1", now, null, List.of("tag1"), "desc", 0.3,
                        Set.of("m1", "m2"), Map.of("m1", "leader"), 0.9));
        var json = SocialJsonMapper.toJson(fragments, new TypeReference<>() {});
        assertThat(json).contains("\"@type\":\"group\"");
        List<NarrativeFragment> result = SocialJsonMapper.fromJson(json, new TypeReference<>() {});
        assertThat(result.get(0)).isInstanceOf(GroupEpisode.class);
    }

    @Test
    void roundTripNarrativeFragmentList_derivedTheme() {
        var now = Instant.now();
        List<NarrativeFragment> fragments = List.of(
                new DerivedTheme("t1", now, null, List.of("tag1"), "curiosity",
                        0.8, Map.of(DriveAxis.CURIOSITY, 0.5), List.of("ep1")));
        var json = SocialJsonMapper.toJson(fragments, new TypeReference<>() {});
        assertThat(json).contains("\"@type\":\"theme\"");
        List<NarrativeFragment> result = SocialJsonMapper.fromJson(json, new TypeReference<>() {});
        assertThat(result.get(0)).isInstanceOf(DerivedTheme.class);
        var theme = (DerivedTheme) result.get(0);
        assertThat(theme.axisModulationWeights()).containsEntry(DriveAxis.CURIOSITY, 0.5);
    }

    @Test
    void roundTripMixedNarrativeFragments() {
        var now = Instant.now();
        List<NarrativeFragment> fragments = List.of(
                new IndividualEpisode("ep1", now, null, List.of(), "episode", 0.0, List.of()),
                new GroupEpisode("g1", now, null, List.of(), "group", 0.0, Set.of(), Map.of(), 0.5),
                new DerivedTheme("t1", now, null, List.of(), "theme", 0.5, Map.of(), List.of()));
        var json = SocialJsonMapper.toJson(fragments, new TypeReference<>() {});
        List<NarrativeFragment> result = SocialJsonMapper.fromJson(json, new TypeReference<>() {});
        assertThat(result).hasSize(3);
        assertThat(result.get(0)).isInstanceOf(IndividualEpisode.class);
        assertThat(result.get(1)).isInstanceOf(GroupEpisode.class);
        assertThat(result.get(2)).isInstanceOf(DerivedTheme.class);
    }
}
