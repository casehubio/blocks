package io.casehub.blocks.summarisation;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WindowPolicyTest {

    @Test
    void rejects_negativeMaxAge() {
        assertThatThrownBy(() -> new WindowPolicy(-1, 5))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxAge");
    }

    @Test
    void rejects_negativeMaxCount() {
        assertThatThrownBy(() -> new WindowPolicy(100, -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxCount");
    }

    @Test
    void rejects_bothZero() {
        assertThatThrownBy(() -> new WindowPolicy(0, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("At least one");
    }

    @Test
    void accepts_countOnly() {
        var policy = new WindowPolicy(0, 5);
        assertThat(policy.maxAge()).isZero();
        assertThat(policy.maxCount()).isEqualTo(5);
    }

    @Test
    void accepts_ageOnly() {
        var policy = new WindowPolicy(100, 0);
        assertThat(policy.maxAge()).isEqualTo(100);
        assertThat(policy.maxCount()).isZero();
    }

    @Test
    void accepts_bothPositive() {
        var policy = new WindowPolicy(100, 5);
        assertThat(policy.maxAge()).isEqualTo(100);
        assertThat(policy.maxCount()).isEqualTo(5);
    }

    @Test
    void ofCount_setsCountOnly() {
        var policy = WindowPolicy.ofCount(10);
        assertThat(policy.maxAge()).isZero();
        assertThat(policy.maxCount()).isEqualTo(10);
    }

    @Test
    void ofAge_setsAgeOnly() {
        var policy = WindowPolicy.ofAge(5000);
        assertThat(policy.maxAge()).isEqualTo(5000);
        assertThat(policy.maxCount()).isZero();
    }

    @Test
    void of_setsBoth() {
        var policy = WindowPolicy.of(3000, 8);
        assertThat(policy.maxAge()).isEqualTo(3000);
        assertThat(policy.maxCount()).isEqualTo(8);
    }
}
