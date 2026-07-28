package io.casehub.blocks.summarisation.observation.affordance;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObservationSectionTest {

    // --- EntityGroup ---

    @Test
    void entityGroup_rejects_null_header() {
        assertThatThrownBy(() -> ObservationSection.entities(null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("header required");
    }

    @Test
    void entityGroup_rejects_blank_header() {
        assertThatThrownBy(() -> ObservationSection.entities("  ", null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("header required");
    }

    @Test
    void entityGroup_factory_creates_correct_type() {
        var entity = new ObservableEntity("x", "X", "desc");
        var section = ObservationSection.entities("Objects", "Nothing here.", List.of(entity));
        assertThat(section).isInstanceOf(ObservationSection.EntityGroup.class);
        assertThat(section.header()).isEqualTo("Objects");
        var eg = (ObservationSection.EntityGroup) section;
        assertThat(eg.emptyMessage()).isEqualTo("Nothing here.");
        assertThat(eg.entities()).containsExactly(entity);
    }

    @Test
    void entityGroup_entities_defensively_copied() {
        var list = new ArrayList<>(List.of(new ObservableEntity("x", "X", "desc")));
        var section = ObservationSection.entities("Objects", null, list);
        list.add(new ObservableEntity("y", "Y", "desc"));
        assertThat(((ObservationSection.EntityGroup) section).entities()).hasSize(1);
    }

    @Test
    void entityGroup_entities_unmodifiable() {
        var section = ObservationSection.entities("Objects", null,
                List.of(new ObservableEntity("x", "X", "desc")));
        assertThatThrownBy(() -> ((ObservationSection.EntityGroup) section).entities()
                .add(new ObservableEntity("y", "Y", "desc")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void entityGroup_null_emptyMessage_allowed() {
        var section = ObservationSection.entities("Objects", null, List.of());
        assertThat(((ObservationSection.EntityGroup) section).emptyMessage()).isNull();
    }

    // --- TextBlock ---

    @Test
    void textBlock_rejects_null_header() {
        assertThatThrownBy(() -> ObservationSection.text(null, "content"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("header required");
    }

    @Test
    void textBlock_rejects_null_content() {
        assertThatThrownBy(() -> ObservationSection.text("Header", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content required");
    }

    @Test
    void textBlock_rejects_blank_content() {
        assertThatThrownBy(() -> ObservationSection.text("Header", "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content required");
    }

    @Test
    void textBlock_factory_creates_correct_type() {
        var section = ObservationSection.text("Location", "Kitchen: A warm room.");
        assertThat(section).isInstanceOf(ObservationSection.TextBlock.class);
        assertThat(section.header()).isEqualTo("Location");
        assertThat(((ObservationSection.TextBlock) section).content())
                .isEqualTo("Kitchen: A warm room.");
    }

    // --- ItemList ---

    @Test
    void itemList_rejects_null_header() {
        assertThatThrownBy(() -> ObservationSection.items(null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("header required");
    }

    @Test
    void itemList_factory_creates_correct_type() {
        var section = ObservationSection.items("Goals", "No goals.", List.of("Find diamond"));
        assertThat(section).isInstanceOf(ObservationSection.ItemList.class);
        assertThat(section.header()).isEqualTo("Goals");
        var il = (ObservationSection.ItemList) section;
        assertThat(il.emptyMessage()).isEqualTo("No goals.");
        assertThat(il.items()).containsExactly("Find diamond");
    }

    @Test
    void itemList_items_defensively_copied() {
        var list = new ArrayList<>(List.of("a"));
        var section = ObservationSection.items("Goals", null, list);
        list.add("b");
        assertThat(((ObservationSection.ItemList) section).items()).hasSize(1);
    }

    @Test
    void itemList_items_unmodifiable() {
        var section = ObservationSection.items("Goals", null, List.of("a"));
        assertThatThrownBy(() -> ((ObservationSection.ItemList) section).items().add("b"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void itemList_null_emptyMessage_allowed() {
        var section = ObservationSection.items("Goals", null, List.of());
        assertThat(((ObservationSection.ItemList) section).emptyMessage()).isNull();
    }
}
