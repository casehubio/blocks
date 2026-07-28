package io.casehub.blocks.summarisation.observation.affordance;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AffordanceRendererIntegrationTest {

    final AffordanceRenderer renderer = new AffordanceRenderer();

    @Test
    void wacky_manor_structural_observation() {
        var observation = renderer.renderObservation(List.of(
                ObservationSection.text("Current Location",
                        "Kitchen: A large room with copper pots hanging from the ceiling."),
                ObservationSection.entities("Visible Objects", "Nothing notable here.", List.of(
                        new ObservableEntity("poison", "Rat Poison",
                                "A dusty bottle of rat poison", List.of(
                                new Affordance("TAKE", "to pick up"))),
                        new ObservableEntity("tea-service", "Tea Service",
                                "A silver tea set on the counter", List.of(
                                new Affordance("INTERACT", null),
                                new Affordance("USE", null, null, List.of("rat-poison")))))),
                ObservationSection.entities("Exits", "No exits.", List.of(
                        new ObservableEntity("entrance-hall", "Entrance Hall",
                                "The grand foyer", List.of(
                                new Affordance("MOVE", "to enter"))),
                        new ObservableEntity("library", "Library",
                                "A dusty library filled with books", List.of(
                                new Affordance("MOVE", "to enter"))))),
                ObservationSection.entities("Characters Present", "You are alone.", List.of()),
                ObservationSection.entities("Your Inventory", "You are carrying nothing.", List.of(
                        new ObservableEntity("rat-poison", "rat-poison", null))),
                ObservationSection.items("Your Goals", "No specific goals.", List.of(
                        "[PRIMARY] Find the Doily Diamond",
                        "[SECONDARY] Solve puzzles"))));

        assertThat(observation)
                .contains("== Current Location ==")
                .contains("Kitchen: A large room with copper pots");

        assertThat(observation)
                .contains("== Visible Objects ==")
                .contains("- Rat Poison [id: poison]: A dusty bottle of rat poison [TAKE to pick up]")
                .contains("- Tea Service [id: tea-service]: A silver tea set on the counter [INTERACT] [USE, with: rat-poison]");

        assertThat(observation)
                .contains("== Exits ==")
                .contains("- Entrance Hall [id: entrance-hall]: The grand foyer [MOVE to enter]");

        assertThat(observation)
                .contains("== Characters Present ==")
                .contains("You are alone.");

        assertThat(observation)
                .contains("== Your Inventory ==")
                .contains("- rat-poison [id: rat-poison]");

        assertThat(observation)
                .contains("== Your Goals ==")
                .contains("- [PRIMARY] Find the Doily Diamond")
                .contains("- [SECONDARY] Solve puzzles");
    }

    @Test
    void wacky_manor_action_vocabulary() {
        var vocab = renderer.renderActionVocabulary("Available Actions:", List.of(
                new ActionDescriptor("MOVE", "Move to an adjacent room", "<room-id>"),
                new ActionDescriptor("TAKE", "Pick up a portable object into your inventory", "<object-id>"),
                new ActionDescriptor("USE", "Use an inventory item on a target object", "<item-id> <target-id>"),
                new ActionDescriptor("INTERACT", "Interact with an object in the environment", "<object-id>"),
                new ActionDescriptor("LOOK", "Look around the current room", null),
                new ActionDescriptor("WAIT", "Do nothing this turn", null)));

        assertThat(vocab)
                .startsWith("Available Actions:")
                .contains("- MOVE <room-id>: Move to an adjacent room")
                .contains("- TAKE <object-id>: Pick up a portable object into your inventory")
                .contains("- USE <item-id> <target-id>: Use an inventory item on a target object")
                .contains("- LOOK: Look around the current room")
                .contains("- WAIT: Do nothing this turn");
    }

    @Test
    void grounding_chain_completeness() {
        var entity = new ObservableEntity("poison", "Rat Poison",
                "A dusty bottle", List.of(
                new Affordance("TAKE", "to pick up"),
                new Affordance("USE", null, null, List.of("tea-service"))));
        var result = renderer.renderEntities(List.of(entity));
        assertThat(result).contains("[id: poison]");
        assertThat(result).contains("[TAKE to pick up]");
        assertThat(result).contains("[USE, with: tea-service]");
    }

    @Test
    void combined_affordance_qualifiers() {
        var entity = new ObservableEntity("altar", "Ancient Altar",
                "A stone altar with runes", List.of(
                new Affordance("USE", "to offer", "key", List.of("gold-coin", "silver-coin"))));
        var result = renderer.renderEntities(List.of(entity));
        assertThat(result).contains("[USE to offer, requires: key, with: gold-coin, silver-coin]");
    }
}
