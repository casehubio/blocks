package io.casehub.blocks.summarisation.observation.affordance;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AffordanceTest {

    @Test
    void rejects_null_actionType() {
        assertThatThrownBy(() -> new Affordance(null, null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actionType required");
    }

    @Test
    void rejects_blank_actionType() {
        assertThatThrownBy(() -> new Affordance("  ", null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actionType required");
    }

    @Test
    void full_construction() {
        var a = new Affordance("USE", "to apply", "key", List.of("poison", "acid"));
        assertThat(a.actionType()).isEqualTo("USE");
        assertThat(a.label()).isEqualTo("to apply");
        assertThat(a.requiredItem()).isEqualTo("key");
        assertThat(a.acceptsItems()).containsExactly("poison", "acid");
    }

    @Test
    void convenience_constructor() {
        var a = new Affordance("TAKE", "to pick up");
        assertThat(a.actionType()).isEqualTo("TAKE");
        assertThat(a.label()).isEqualTo("to pick up");
        assertThat(a.requiredItem()).isNull();
        assertThat(a.acceptsItems()).isEmpty();
    }

    @Test
    void acceptsItems_defensively_copied() {
        var items = new ArrayList<>(List.of("a", "b"));
        var a = new Affordance("USE", null, null, items);
        items.add("c");
        assertThat(a.acceptsItems()).containsExactly("a", "b");
    }

    @Test
    void acceptsItems_is_unmodifiable() {
        var a = new Affordance("USE", null, null, List.of("a"));
        assertThatThrownBy(() -> a.acceptsItems().add("b"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
