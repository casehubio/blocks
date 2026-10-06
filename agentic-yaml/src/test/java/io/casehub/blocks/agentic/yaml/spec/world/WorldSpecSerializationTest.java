package io.casehub.blocks.agentic.yaml.spec.world;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.jackson.YamlMappers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.blocks.summarisation.observation.affordance.ResolutionTier;

import static org.assertj.core.api.Assertions.assertThat;

class WorldSpecSerializationTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = YamlMappers.create();
    }

    @Test
    void affordanceSpecAllFields() throws Exception {
        var yaml = """
                actionType: USE
                label: to apply
                requiredItem: key
                acceptsItems:
                  - rat-poison
                  - arsenic
                """;
        var spec = mapper.readValue(yaml, AffordanceSpec.class);
        assertThat(spec.actionType()).isEqualTo("USE");
        assertThat(spec.label()).isEqualTo("to apply");
        assertThat(spec.requiredItem()).isEqualTo("key");
        assertThat(spec.acceptsItems()).containsExactly("rat-poison", "arsenic");
    }

    @Test
    void affordanceSpecMinimal() throws Exception {
        var yaml = "actionType: TAKE";
        var spec = mapper.readValue(yaml, AffordanceSpec.class);
        assertThat(spec.actionType()).isEqualTo("TAKE");
        assertThat(spec.label()).isNull();
        assertThat(spec.requiredItem()).isNull();
        assertThat(spec.acceptsItems()).isNull();
    }

    @Test
    void entitySpecWithAffordances() throws Exception {
        var yaml = """
                displayName: Rat Poison
                description: A dusty bottle
                affordances:
                  - actionType: TAKE
                    label: to pick up
                """;
        var spec = mapper.readValue(yaml, EntitySpec.class);
        assertThat(spec.displayName()).isEqualTo("Rat Poison");
        assertThat(spec.description()).isEqualTo("A dusty bottle");
        assertThat(spec.affordances()).hasSize(1);
        assertThat(spec.affordances().get(0).actionType()).isEqualTo("TAKE");
    }

    @Test
    void entitySpecMinimal() throws Exception {
        var yaml = "displayName: Door";
        var spec = mapper.readValue(yaml, EntitySpec.class);
        assertThat(spec.displayName()).isEqualTo("Door");
        assertThat(spec.description()).isNull();
        assertThat(spec.affordances()).isNull();
    }

    @Test
    void inlineEntitySpecCarriesId() throws Exception {
        var yaml = """
                id: hidden-door
                displayName: Hidden Door
                """;
        var spec = mapper.readValue(yaml, InlineEntitySpec.class);
        assertThat(spec.id()).isEqualTo("hidden-door");
        assertThat(spec.displayName()).isEqualTo("Hidden Door");
    }

    @Test
    void actionDescriptorSpecAllFields() throws Exception {
        var yaml = """
                type: MOVE
                description: Move to an adjacent room
                parameterFormat: <room-id>
                """;
        var spec = mapper.readValue(yaml, ActionDescriptorSpec.class);
        assertThat(spec.type()).isEqualTo("MOVE");
        assertThat(spec.description()).isEqualTo("Move to an adjacent room");
        assertThat(spec.parameterFormat()).isEqualTo("<room-id>");
    }

    @Test
    void actionDescriptorSpecMinimal() throws Exception {
        var yaml = """
                type: LOOK
                description: Look around
                """;
        var spec = mapper.readValue(yaml, ActionDescriptorSpec.class);
        assertThat(spec.parameterFormat()).isNull();
    }

    @Test
    void entityGroupSpecWithRefs() throws Exception {
        var yaml = """
                type: entity-group
                header: Visible Objects
                emptyMessage: Nothing here.
                entityRefs:
                  - poison
                  - tea-service
                """;
        var spec = mapper.readValue(yaml, ObservationSectionSpec.class);
        assertThat(spec).isInstanceOf(ObservationSectionSpec.EntityGroupSpec.class);
        var group = (ObservationSectionSpec.EntityGroupSpec) spec;
        assertThat(group.header()).isEqualTo("Visible Objects");
        assertThat(group.emptyMessage()).isEqualTo("Nothing here.");
        assertThat(group.entityRefs()).containsExactly("poison", "tea-service");
        assertThat(group.entities()).isNull();
    }

    @Test
    void entityGroupSpecWithInlineEntities() throws Exception {
        var yaml = """
                type: entity-group
                header: Objects
                entities:
                  - id: door
                    displayName: Wooden Door
                """;
        var spec = mapper.readValue(yaml, ObservationSectionSpec.class);
        var group = (ObservationSectionSpec.EntityGroupSpec) spec;
        assertThat(group.entities()).hasSize(1);
        assertThat(group.entities().get(0).id()).isEqualTo("door");
    }

    @Test
    void textBlockSpec() throws Exception {
        var yaml = """
                type: text
                header: Current Location
                content: "Kitchen: A large room."
                """;
        var spec = mapper.readValue(yaml, ObservationSectionSpec.class);
        assertThat(spec).isInstanceOf(ObservationSectionSpec.TextBlockSpec.class);
        var text = (ObservationSectionSpec.TextBlockSpec) spec;
        assertThat(text.header()).isEqualTo("Current Location");
        assertThat(text.content()).isEqualTo("Kitchen: A large room.");
    }

    @Test
    void itemListSpec() throws Exception {
        var yaml = """
                type: item-list
                header: Your Goals
                items:
                  - "[PRIMARY] Find the Diamond"
                  - "[SECONDARY] Solve puzzles"
                """;
        var spec = mapper.readValue(yaml, ObservationSectionSpec.class);
        assertThat(spec).isInstanceOf(ObservationSectionSpec.ItemListSpec.class);
        var list = (ObservationSectionSpec.ItemListSpec) spec;
        assertThat(list.items()).hasSize(2);
    }

    @Test
    void entityGroupSpecWithAnnotationProperties() throws Exception {
        var yaml = """
                type: entity-group
                header: Secret Room
                requiredTags:
                  - perception-enhanced
                resolutions:
                  REDUCED:
                    type: text
                    header: Secret Room
                    content: You sense something.
                interpretiveFrame: Only visible to enhanced agents
                entityRefs:
                  - hidden-door
                """;
        var spec = mapper.readValue(yaml, ObservationSectionSpec.class);
        var group = (ObservationSectionSpec.EntityGroupSpec) spec;
        assertThat(group.requiredTags()).containsExactly("perception-enhanced");
        assertThat(group.resolutions()).containsKey(ResolutionTier.REDUCED);
        var resolution = group.resolutions().get(ResolutionTier.REDUCED);
        assertThat(resolution).isInstanceOf(ObservationSectionSpec.TextBlockSpec.class);
        assertThat(group.interpretiveFrame()).isEqualTo("Only visible to enhanced agents");
    }

    @Test
    void sectionWithoutAnnotationPropertiesHasNulls() throws Exception {
        var yaml = """
                type: text
                header: Location
                content: A room.
                """;
        var spec = mapper.readValue(yaml, ObservationSectionSpec.class);
        var text = (ObservationSectionSpec.TextBlockSpec) spec;
        assertThat(text.requiredTags()).isNull();
        assertThat(text.resolutions()).isNull();
        assertThat(text.interpretiveFrame()).isNull();
    }

    @Test
    void perceptionFilterSpec() throws Exception {
        var yaml = "type: perception";
        var spec = mapper.readValue(yaml, ObservationFilterSpec.class);
        assertThat(spec).isInstanceOf(ObservationFilterSpec.PerceptionSpec.class);
    }

    @Test
    void rendererSpecAllFields() throws Exception {
        var yaml = """
                verbatimThreshold: 5
                groupedThreshold: 20
                """;
        var spec = mapper.readValue(yaml, RendererSpec.class);
        assertThat(spec.verbatimThreshold()).isEqualTo(5);
        assertThat(spec.groupedThreshold()).isEqualTo(20);
    }

    @Test
    void rendererSpecVerbatimOnly() throws Exception {
        var yaml = "verbatimThreshold: 10";
        var spec = mapper.readValue(yaml, RendererSpec.class);
        assertThat(spec.verbatimThreshold()).isEqualTo(10);
        assertThat(spec.groupedThreshold()).isNull();
    }

    @Test
    void worldDefinitionFullYaml() throws Exception {
        var yaml = """
                actions:
                  - type: MOVE
                    description: Move to a room
                  - type: TAKE
                    description: Pick up an object
                    parameterFormat: <object-id>
                entities:
                  poison:
                    displayName: Rat Poison
                    description: A dusty bottle
                    affordances:
                      - actionType: TAKE
                        label: to pick up
                  tea-service:
                    displayName: Tea Service
                    affordances:
                      - actionType: USE
                        acceptsItems:
                          - rat-poison
                sections:
                  - type: entity-group
                    header: Objects
                    entityRefs:
                      - poison
                      - tea-service
                  - type: text
                    header: Location
                    content: A kitchen.
                pipeline:
                  - type: perception
                renderer:
                  verbatimThreshold: 5
                  groupedThreshold: 20
                """;
        var def = mapper.readValue(yaml, WorldDefinition.class);
        assertThat(def.actions()).hasSize(2);
        assertThat(def.entities()).hasSize(2);
        assertThat(def.entities()).containsKey("poison");
        assertThat(def.sections()).hasSize(2);
        assertThat(def.pipeline()).hasSize(1);
        assertThat(def.renderer()).isNotNull();
        assertThat(def.renderer().verbatimThreshold()).isEqualTo(5);
    }

    @Test
    void worldDefinitionEmpty() throws Exception {
        var yaml = "{}";
        var def = mapper.readValue(yaml, WorldDefinition.class);
        assertThat(def.actions()).isNull();
        assertThat(def.entities()).isNull();
        assertThat(def.sections()).isNull();
        assertThat(def.pipeline()).isNull();
        assertThat(def.renderer()).isNull();
    }
}
