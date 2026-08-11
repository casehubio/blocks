package io.casehub.blocks.summarisation.observation.affordance;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionDescriptorTest {

    @Test
    void rejects_null_actionType() {
        assertThatThrownBy(() -> new ActionDescriptor(null, "desc", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actionType required");
    }

    @Test
    void rejects_blank_actionType() {
        assertThatThrownBy(() -> new ActionDescriptor("  ", "desc", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actionType required");
    }

    @Test
    void rejects_null_description() {
        assertThatThrownBy(() -> new ActionDescriptor("TAKE", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("description required");
    }

    @Test
    void rejects_blank_description() {
        assertThatThrownBy(() -> new ActionDescriptor("TAKE", "  ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("description required");
    }

    @Test
    void full_construction() {
        var d = new ActionDescriptor("MOVE", "Move to a room", "<room-id>");
        assertThat(d.actionType()).isEqualTo("MOVE");
        assertThat(d.description()).isEqualTo("Move to a room");
        assertThat(d.parameterFormat()).isEqualTo("<room-id>");
    }

    @Test
    void null_parameterFormat_allowed() {
        var d = new ActionDescriptor("WAIT", "Do nothing", null);
        assertThat(d.parameterFormat()).isNull();
    }
}
