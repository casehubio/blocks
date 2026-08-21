package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.memory.MemoryDomain;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

class StrategyLearningConfigTest {

    @Test void defaults_createsValidConfig() {
        var config = StrategyLearningConfig.defaults();
        assertThat(config.minSignalsForConversationCase()).isEqualTo(3);
        assertThat(config.minCasesForReflection()).isEqualTo(5);
        assertThat(config.maxReflectionSources()).isEqualTo(50);
        assertThat(config.maxGuidelines()).isEqualTo(10);
        assertThat(config.defaultDimensionValue()).isEqualTo(0.5);
        assertThat(config.maxBufferSize()).isEqualTo(100);
        assertThat(config.memoryDomain().name()).isEqualTo("strategy-learning");
        assertThat(config.engagementCaseType()).isEqualTo("engagement-evidence");
        assertThat(config.profileCaseType()).isEqualTo("strategy-profile");
    }

    @Test void rejectsZeroMinSignals() {
        assertThatThrownBy(() -> config(0, 5, 50, 10, 0.5, 100, Duration.ofHours(24)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsZeroMinCases() {
        assertThatThrownBy(() -> config(3, 0, 50, 10, 0.5, 100, Duration.ofHours(24)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsZeroMaxReflectionSources() {
        assertThatThrownBy(() -> config(3, 5, 0, 10, 0.5, 100, Duration.ofHours(24)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsZeroMaxGuidelines() {
        assertThatThrownBy(() -> config(3, 5, 50, 0, 0.5, 100, Duration.ofHours(24)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsZeroMaxBuffer() {
        assertThatThrownBy(() -> config(3, 5, 50, 10, 0.5, 0, Duration.ofHours(24)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsOutOfRangeDefaultHigh() {
        assertThatThrownBy(() -> config(3, 5, 50, 10, 1.5, 100, Duration.ofHours(24)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsOutOfRangeDefaultLow() {
        assertThatThrownBy(() -> config(3, 5, 50, 10, -0.1, 100, Duration.ofHours(24)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsNegativeTimeout() {
        assertThatThrownBy(() -> config(3, 5, 50, 10, 0.5, 100, Duration.ofHours(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsZeroTimeout() {
        assertThatThrownBy(() -> config(3, 5, 50, 10, 0.5, 100, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void acceptsBoundaryValues() {
        var config = config(1, 1, 1, 1, 0.0, 1, Duration.ofSeconds(1));
        assertThat(config.minSignalsForConversationCase()).isEqualTo(1);
        assertThat(config.defaultDimensionValue()).isEqualTo(0.0);

        var config2 = config(1, 1, 1, 1, 1.0, 1, Duration.ofSeconds(1));
        assertThat(config2.defaultDimensionValue()).isEqualTo(1.0);
    }

    private StrategyLearningConfig config(int minSignals, int minCases, int maxSources,
                                           int maxGuidelines, double defaultDim,
                                           int maxBuffer, Duration timeout) {
        return new StrategyLearningConfig(minSignals, minCases, maxSources, maxGuidelines,
                defaultDim, maxBuffer, timeout, new MemoryDomain("test"),
                "engagement", "profile");
    }
}
