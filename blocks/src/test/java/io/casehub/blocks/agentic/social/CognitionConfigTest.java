package io.casehub.blocks.agentic.social;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CognitionConfigTest {

    @Test
    void allEnablesEverything() {
        var config = CognitionConfig.all();
        assertThat(config.moodEnabled()).isTrue();
        assertThat(config.drivesEnabled()).isTrue();
        assertThat(config.mentalModelEnabled()).isTrue();
        assertThat(config.userModelEnabled()).isTrue();
        assertThat(config.strategyEnabled()).isTrue();
        assertThat(config.narrativeEnabled()).isTrue();
        assertThat(config.goalsEnabled()).isTrue();
        assertThat(config.memoryHygieneEnabled()).isTrue();
        assertThat(config.innerLifeEnabled()).isTrue();
        assertThat(config.characterDrivesEnabled()).isTrue();
        assertThat(config.needsPyramidEnabled()).isTrue();
    }

    @Test
    void noneDisablesEverything() {
        var config = CognitionConfig.none();
        assertThat(config.moodEnabled()).isFalse();
        assertThat(config.drivesEnabled()).isFalse();
        assertThat(config.mentalModelEnabled()).isFalse();
        assertThat(config.userModelEnabled()).isFalse();
        assertThat(config.strategyEnabled()).isFalse();
        assertThat(config.narrativeEnabled()).isFalse();
        assertThat(config.goalsEnabled()).isFalse();
        assertThat(config.memoryHygieneEnabled()).isFalse();
        assertThat(config.innerLifeEnabled()).isFalse();
        assertThat(config.characterDrivesEnabled()).isFalse();
        assertThat(config.needsPyramidEnabled()).isFalse();
    }

    @Test
    void withTogglesNeedsPyramid() {
        var config = CognitionConfig.none().with("needsPyramid", true);
        assertThat(config.needsPyramidEnabled()).isTrue();
        assertThat(config.moodEnabled()).isFalse();
    }

    @Test
    void withTogglesInnerLife() {
        var config = CognitionConfig.all().with("innerLife", false);
        assertThat(config.innerLifeEnabled()).isFalse();
        assertThat(config.moodEnabled()).isTrue();
    }

    @Test
    void withTogglesIndividualSubsystem() {
        var config = CognitionConfig.all().with("mentalModel", false);
        assertThat(config.moodEnabled()).isTrue();
        assertThat(config.mentalModelEnabled()).isFalse();
        assertThat(config.drivesEnabled()).isTrue();
    }

    @Test
    void withoutDisablesMultiple() {
        var config = CognitionConfig.all().without("mentalModel", "narrative", "goals");
        assertThat(config.moodEnabled()).isTrue();
        assertThat(config.drivesEnabled()).isTrue();
        assertThat(config.mentalModelEnabled()).isFalse();
        assertThat(config.narrativeEnabled()).isFalse();
        assertThat(config.goalsEnabled()).isFalse();
        assertThat(config.userModelEnabled()).isTrue();
    }


    @Test
    void allEnablesEntityKnowledge() {
        assertThat(CognitionConfig.all().entityKnowledgeEnabled()).isTrue();
    }

    @Test
    void allDisablesPerspectiveComparison() {
        assertThat(CognitionConfig.all().perspectiveComparisonEnabled()).isFalse();
    }

    @Test
    void noneDisablesEntityKnowledge() {
        assertThat(CognitionConfig.none().entityKnowledgeEnabled()).isFalse();
    }

    @Test
    void noneDisablesPerspectiveComparison() {
        assertThat(CognitionConfig.none().perspectiveComparisonEnabled()).isFalse();
    }

    @Test
    void withTogglesEntityKnowledge() {
        var config = CognitionConfig.none().with("entityKnowledge", true);
        assertThat(config.entityKnowledgeEnabled()).isTrue();
    }

    @Test
    void withTogglesPerspectiveComparison() {
        var config = CognitionConfig.none().with("perspectiveComparison", true);
        assertThat(config.perspectiveComparisonEnabled()).isTrue();
    }

    @Test
    void withRejectsUnknownSubsystem() {
        assertThatThrownBy(() -> CognitionConfig.all().with("bogus", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bogus");
    }
}
