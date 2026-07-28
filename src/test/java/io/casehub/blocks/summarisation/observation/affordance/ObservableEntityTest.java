package io.casehub.blocks.summarisation.observation.affordance;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObservableEntityTest {

    @Test
    void rejects_null_id() {
        assertThatThrownBy(() -> new ObservableEntity(null, "Name", "desc", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id required");
    }

    @Test
    void rejects_blank_id() {
        assertThatThrownBy(() -> new ObservableEntity("", "Name", "desc", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id required");
    }

    @Test
    void rejects_null_displayName() {
        assertThatThrownBy(() -> new ObservableEntity("x", null, "desc", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("displayName required");
    }

    @Test
    void rejects_blank_displayName() {
        assertThatThrownBy(() -> new ObservableEntity("x", "  ", "desc", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("displayName required");
    }

    @Test
    void null_description_allowed() {
        var entity = new ObservableEntity("x", "Name", null, List.of());
        assertThat(entity.description()).isNull();
    }

    @Test
    void full_construction() {
        var affordance = new Affordance("TAKE", "to pick up");
        var entity = new ObservableEntity("poison", "Rat Poison",
                "A dusty bottle", List.of(affordance));
        assertThat(entity.id()).isEqualTo("poison");
        assertThat(entity.displayName()).isEqualTo("Rat Poison");
        assertThat(entity.description()).isEqualTo("A dusty bottle");
        assertThat(entity.affordances()).containsExactly(affordance);
    }

    @Test
    void convenience_constructor_empty_affordances() {
        var entity = new ObservableEntity("x", "Name", "desc");
        assertThat(entity.affordances()).isEmpty();
    }

    @Test
    void affordances_defensively_copied() {
        var list = new ArrayList<>(List.of(new Affordance("TAKE", null)));
        var entity = new ObservableEntity("x", "Name", "desc", list);
        list.add(new Affordance("USE", null));
        assertThat(entity.affordances()).hasSize(1);
    }

    @Test
    void affordances_unmodifiable() {
        var entity = new ObservableEntity("x", "Name", "desc",
                List.of(new Affordance("TAKE", null)));
        assertThatThrownBy(() -> entity.affordances().add(new Affordance("USE", null)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
