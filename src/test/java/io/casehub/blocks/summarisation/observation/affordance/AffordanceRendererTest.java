package io.casehub.blocks.summarisation.observation.affordance;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AffordanceRendererTest {

    final AffordanceRenderer renderer = new AffordanceRenderer();

    // --- renderEntities ---

    @Test
    void renderEntities_empty_list_returns_empty_string() {
        assertThat(renderer.renderEntities(List.of())).isEmpty();
    }

    @Test
    void renderEntities_empty_list_with_emptyMessage() {
        assertThat(renderer.renderEntities(List.of(), "Nothing here."))
                .isEqualTo("Nothing here.");
    }

    @Test
    void renderEntities_single_entity_no_affordances() {
        var entity = new ObservableEntity("rack", "Coat Rack",
                "A wooden rack by the door.");
        assertThat(renderer.renderEntities(List.of(entity)))
                .isEqualTo("- Coat Rack [id: rack]: A wooden rack by the door.");
    }

    @Test
    void renderEntities_entity_null_description() {
        var entity = new ObservableEntity("x", "Widget", null);
        assertThat(renderer.renderEntities(List.of(entity)))
                .isEqualTo("- Widget [id: x]");
    }

    @Test
    void renderEntities_entity_with_one_affordance() {
        var entity = new ObservableEntity("poison", "Rat Poison",
                "A dusty bottle", List.of(new Affordance("TAKE", "to pick up")));
        assertThat(renderer.renderEntities(List.of(entity)))
                .isEqualTo("- Rat Poison [id: poison]: A dusty bottle [TAKE to pick up]");
    }

    @Test
    void renderEntities_entity_with_multiple_affordances() {
        var entity = new ObservableEntity("tea", "Tea Service",
                "A silver set", List.of(
                    new Affordance("INTERACT", null),
                    new Affordance("USE", null, null, List.of("rat-poison"))));
        assertThat(renderer.renderEntities(List.of(entity)))
                .isEqualTo("- Tea Service [id: tea]: A silver set" +
                           " [INTERACT] [USE, with: rat-poison]");
    }

    @Test
    void renderEntities_multiple_entities_newline_separated() {
        var e1 = new ObservableEntity("a", "Alpha", "First");
        var e2 = new ObservableEntity("b", "Beta", "Second");
        var result = renderer.renderEntities(List.of(e1, e2));
        assertThat(result).isEqualTo(
                "- Alpha [id: a]: First\n" +
                "- Beta [id: b]: Second");
    }

    @Test
    void renderEntities_non_empty_list_ignores_emptyMessage() {
        var entity = new ObservableEntity("x", "X", "desc");
        assertThat(renderer.renderEntities(List.of(entity), "Nothing here."))
                .isEqualTo("- X [id: x]: desc");
    }

    // --- Affordance tag formats (all 8 combinations) ---

    @Test
    void affordance_actionType_only() {
        var entity = new ObservableEntity("x", "X", null,
                List.of(new Affordance("LOOK", null)));
        assertThat(renderer.renderEntities(List.of(entity)))
                .contains("[LOOK]");
    }

    @Test
    void affordance_with_label() {
        var entity = new ObservableEntity("x", "X", null,
                List.of(new Affordance("TAKE", "to pick up")));
        assertThat(renderer.renderEntities(List.of(entity)))
                .contains("[TAKE to pick up]");
    }

    @Test
    void affordance_with_requiredItem() {
        var entity = new ObservableEntity("x", "X", null,
                List.of(new Affordance("INTERACT", null, "fake-medal", List.of())));
        assertThat(renderer.renderEntities(List.of(entity)))
                .contains("[INTERACT, requires: fake-medal]");
    }

    @Test
    void affordance_with_label_and_requiredItem() {
        var entity = new ObservableEntity("x", "X", null,
                List.of(new Affordance("INTERACT", "to examine",
                        "fake-medal", List.of())));
        assertThat(renderer.renderEntities(List.of(entity)))
                .contains("[INTERACT to examine, requires: fake-medal]");
    }

    @Test
    void affordance_with_acceptsItems() {
        var entity = new ObservableEntity("x", "X", null,
                List.of(new Affordance("USE", null, null,
                        List.of("rat-poison", "arsenic"))));
        assertThat(renderer.renderEntities(List.of(entity)))
                .contains("[USE, with: rat-poison, arsenic]");
    }

    @Test
    void affordance_with_label_and_acceptsItems() {
        var entity = new ObservableEntity("x", "X", null,
                List.of(new Affordance("USE", "to apply", null,
                        List.of("rat-poison"))));
        assertThat(renderer.renderEntities(List.of(entity)))
                .contains("[USE to apply, with: rat-poison]");
    }

    @Test
    void affordance_with_requiredItem_and_acceptsItems() {
        var entity = new ObservableEntity("x", "X", null,
                List.of(new Affordance("USE", null, "key",
                        List.of("rat-poison"))));
        assertThat(renderer.renderEntities(List.of(entity)))
                .contains("[USE, requires: key, with: rat-poison]");
    }

    @Test
    void affordance_with_all_fields() {
        var entity = new ObservableEntity("x", "X", null,
                List.of(new Affordance("USE", "to apply", "key",
                        List.of("rat-poison"))));
        assertThat(renderer.renderEntities(List.of(entity)))
                .contains("[USE to apply, requires: key, with: rat-poison]");
    }

    // --- renderObservation ---

    @Test
    void renderObservation_empty_sections_returns_empty() {
        assertThat(renderer.renderObservation(List.of())).isEmpty();
    }

    @Test
    void renderObservation_single_entity_section() {
        var entity = new ObservableEntity("poison", "Rat Poison",
                                          "A dusty bottle",
                                          List.of(new Affordance("TAKE", "to pick up")));
        var result = renderer.renderObservation(List.of(
                ObservationSection.entities("Visible Objects", null,
                                            List.of(entity))));
        assertThat(result).isEqualTo(
                "== Visible Objects ==\n" +
                "- Rat Poison [id: poison]: A dusty bottle [TAKE to pick up]");
    }

    @Test
    void renderObservation_entity_section_empty_with_message() {
        var result = renderer.renderObservation(List.of(
                ObservationSection.entities("Inventory",
                                            "You are carrying nothing.", List.of())));
        assertThat(result).isEqualTo(
                "== Inventory ==\n" +
                "You are carrying nothing.");
    }

    @Test
    void renderObservation_entity_section_empty_null_message_omits() {
        var result = renderer.renderObservation(List.of(
                ObservationSection.entities("Objects", null, List.of())));
        assertThat(result).isEmpty();
    }

    @Test
    void renderObservation_text_section() {
        var result = renderer.renderObservation(List.of(
                ObservationSection.text("Current Location",
                                        "Kitchen: A warm room.")));
        assertThat(result).isEqualTo(
                "== Current Location ==\n" +
                "Kitchen: A warm room.");
    }

    @Test
    void renderObservation_item_list_section() {
        var result = renderer.renderObservation(List.of(
                ObservationSection.items("Goals", null,
                                         List.of("[PRIMARY] Find diamond",
                                                 "[SECONDARY] Solve puzzles"))));
        assertThat(result).isEqualTo(
                "== Goals ==\n" +
                "- [PRIMARY] Find diamond\n" +
                "- [SECONDARY] Solve puzzles");
    }

    @Test
    void renderObservation_item_list_empty_with_message() {
        var result = renderer.renderObservation(List.of(
                ObservationSection.items("Goals", "No goals.", List.of())));
        assertThat(result).isEqualTo(
                "== Goals ==\n" +
                "No goals.");
    }

    @Test
    void renderObservation_item_list_empty_null_message_omits() {
        var result = renderer.renderObservation(List.of(
                ObservationSection.items("Goals", null, List.of())));
        assertThat(result).isEmpty();
    }

    @Test
    void renderObservation_mixed_sections_blank_line_separated() {
        var entity = new ObservableEntity("kitchen", "Kitchen",
                                          "A warm room",
                                          List.of(new Affordance("MOVE", "to enter")));
        var result = renderer.renderObservation(List.of(
                ObservationSection.text("Location",
                                        "Entrance Hall: Grand foyer."),
                ObservationSection.entities("Exits", null,
                                            List.of(entity)),
                ObservationSection.items("Goals", null,
                                         List.of("Find diamond"))));
        assertThat(result).isEqualTo(
                "== Location ==\n" +
                "Entrance Hall: Grand foyer.\n\n" +
                "== Exits ==\n" +
                "- Kitchen [id: kitchen]: A warm room [MOVE to enter]\n\n" +
                "== Goals ==\n" +
                "- Find diamond");
    }

    @Test
    void renderObservation_section_ordering_preserved() {
        var result = renderer.renderObservation(List.of(
                ObservationSection.text("B", "second"),
                ObservationSection.text("A", "first")));
        assertThat(result).startsWith("== B ==");
    }

    @Test
    void renderObservation_all_sections_omitted_returns_empty() {
        var result = renderer.renderObservation(List.of(
                ObservationSection.entities("Objects", null, List.of()),
                ObservationSection.items("Goals", null, List.of())));
        assertThat(result).isEmpty();
    }

    @Test
    void renderObservation_omitted_section_no_extra_blank_lines() {
        var result = renderer.renderObservation(List.of(
                ObservationSection.text("A", "first"),
                ObservationSection.entities("B", null, List.of()),
                ObservationSection.text("C", "third")));
        assertThat(result).isEqualTo(
                "== A ==\n" +
                "first\n\n" +
                "== C ==\n" +
                "third");
    }

    // --- renderActionVocabulary ---

    @Test
    void renderActionVocabulary_multiple_actions() {
        var result = renderer.renderActionVocabulary("Available Actions:",
                                                     List.of(
                                                             new ActionDescriptor("MOVE",
                                                                                  "Move to an adjacent room", "<room-id>"),
                                                             new ActionDescriptor("TAKE",
                                                                                  "Pick up a portable object", "<object-id>")));
        assertThat(result).isEqualTo(
                "Available Actions:\n" +
                "- MOVE <room-id>: Move to an adjacent room\n" +
                "- TAKE <object-id>: Pick up a portable object");
    }

    @Test
    void renderActionVocabulary_null_parameterFormat() {
        var result = renderer.renderActionVocabulary("Actions:",
                                                     List.of(new ActionDescriptor("WAIT",
                                                                                  "Do nothing this turn", null)));
        assertThat(result).isEqualTo(
                "Actions:\n" +
                "- WAIT: Do nothing this turn");
    }

    @Test
    void renderActionVocabulary_empty_actions_returns_header_only() {
        assertThat(renderer.renderActionVocabulary("Actions:", List.of()))
                .isEqualTo("Actions:");
    }

    @Test
    void renderActionVocabulary_header_not_through_headerFormatter() {
        var custom = new AffordanceRenderer(h -> "## " + h);
        var result = custom.renderActionVocabulary("Available Actions:",
                List.of(new ActionDescriptor("WAIT", "Do nothing", null)));
        assertThat(result).startsWith("Available Actions:");
        assertThat(result).doesNotContain("##");
    }

    // --- withHeaderFormatter ---

    @Test
    void custom_header_formatter_applied_to_sections() {
        var custom = renderer.withHeaderFormatter(h -> "## " + h);
        var result = custom.renderObservation(List.of(
                ObservationSection.text("Location", "Kitchen")));
        assertThat(result).isEqualTo("## Location\n" + "Kitchen");
    }

    @Test
    void withHeaderFormatter_returns_new_instance() {
        var custom = renderer.withHeaderFormatter(h -> "## " + h);
        assertThat(custom).isNotSameAs(renderer);
        var original = renderer.renderObservation(List.of(
                ObservationSection.text("Location", "Kitchen")));
        assertThat(original).startsWith("== Location ==");
    }
}
