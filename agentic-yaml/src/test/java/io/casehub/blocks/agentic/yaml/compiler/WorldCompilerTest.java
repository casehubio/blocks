package io.casehub.blocks.agentic.yaml.compiler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.jackson.YamlMappers;
import io.casehub.blocks.agentic.yaml.spec.world.WorldDefinition;
import io.casehub.blocks.summarisation.observation.affordance.AnnotatedSection;
import io.casehub.blocks.summarisation.observation.affordance.ObservationSection;
import io.casehub.blocks.summarisation.observation.affordance.ResolutionTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorldCompilerTest {

    private ObjectMapper mapper;
    private WorldCompiler compiler;

    @BeforeEach
    void setUp() {
        mapper = YamlMappers.create();
        compiler = new WorldCompiler(new ObservationFilterRegistry());
    }

    @Test
    void emptyDefinitionProducesEmptyWorld() throws Exception {
        var def = mapper.readValue("{}", WorldDefinition.class);
        var world = compiler.compile(def);
        assertThat(world.actions()).isEmpty();
        assertThat(world.entities()).isEmpty();
        assertThat(world.sections()).isEmpty();
        assertThat(world.pipeline()).isNull();
        assertThat(world.rendererThresholds()).isNull();
    }

    @Test
    void compilesActions() throws Exception {
        var yaml = """
                actions:
                  - type: MOVE
                    description: Move to a room
                    parameterFormat: <room-id>
                """;
        var world = compiler.compile(mapper.readValue(yaml, WorldDefinition.class));
        assertThat(world.actions()).hasSize(1);
        assertThat(world.actions().get(0).actionType()).isEqualTo("MOVE");
        assertThat(world.actions().get(0).description()).isEqualTo("Move to a room");
        assertThat(world.actions().get(0).parameterFormat()).isEqualTo("<room-id>");
    }

    @Test
    void compilesTopLevelEntities() throws Exception {
        var yaml = """
                entities:
                  poison:
                    displayName: Rat Poison
                    description: A dusty bottle
                    affordances:
                      - actionType: TAKE
                        label: to pick up
                """;
        var world = compiler.compile(mapper.readValue(yaml, WorldDefinition.class));
        assertThat(world.entities()).containsKey("poison");
        var entity = world.entities().get("poison");
        assertThat(entity.id()).isEqualTo("poison");
        assertThat(entity.displayName()).isEqualTo("Rat Poison");
        assertThat(entity.affordances()).hasSize(1);
        assertThat(entity.affordances().get(0).actionType()).isEqualTo("TAKE");
        assertThat(entity.affordances().get(0).label()).isEqualTo("to pick up");
    }

    @Test
    void entityGroupResolvesRefs() throws Exception {
        var yaml = """
                entities:
                  poison:
                    displayName: Rat Poison
                sections:
                  - type: entity-group
                    header: Objects
                    entityRefs:
                      - poison
                """;
        var world = compiler.compile(mapper.readValue(yaml, WorldDefinition.class));
        assertThat(world.sections()).hasSize(1);
        var group = (ObservationSection.EntityGroup) world.sections().get(0);
        assertThat(group.entities()).hasSize(1);
        assertThat(group.entities().get(0).id()).isEqualTo("poison");
    }

    @Test
    void entityGroupWithInlineEntities() throws Exception {
        var yaml = """
                sections:
                  - type: entity-group
                    header: Objects
                    entities:
                      - id: door
                        displayName: Door
                """;
        var world = compiler.compile(mapper.readValue(yaml, WorldDefinition.class));
        var group = (ObservationSection.EntityGroup) world.sections().get(0);
        assertThat(group.entities()).hasSize(1);
        assertThat(group.entities().get(0).id()).isEqualTo("door");
    }

    @Test
    void entityGroupMergesRefsAndInline() throws Exception {
        var yaml = """
                entities:
                  poison:
                    displayName: Rat Poison
                sections:
                  - type: entity-group
                    header: Objects
                    entityRefs:
                      - poison
                    entities:
                      - id: door
                        displayName: Door
                """;
        var world = compiler.compile(mapper.readValue(yaml, WorldDefinition.class));
        var group = (ObservationSection.EntityGroup) world.sections().get(0);
        assertThat(group.entities()).hasSize(2);
    }

    @Test
    void unresolvedEntityRefThrows() throws Exception {
        var yaml = """
                sections:
                  - type: entity-group
                    header: Objects
                    entityRefs:
                      - nonexistent
                """;
        assertThatThrownBy(() -> compiler.compile(
                mapper.readValue(yaml, WorldDefinition.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonexistent");
    }

    @Test
    void textBlockCompiles() throws Exception {
        var yaml = """
                sections:
                  - type: text
                    header: Location
                    content: A kitchen.
                """;
        var world = compiler.compile(mapper.readValue(yaml, WorldDefinition.class));
        var text = (ObservationSection.TextBlock) world.sections().get(0);
        assertThat(text.header()).isEqualTo("Location");
        assertThat(text.content()).isEqualTo("A kitchen.");
    }

    @Test
    void itemListCompiles() throws Exception {
        var yaml = """
                sections:
                  - type: item-list
                    header: Goals
                    emptyMessage: No goals.
                    items:
                      - Find the diamond
                """;
        var world = compiler.compile(mapper.readValue(yaml, WorldDefinition.class));
        var list = (ObservationSection.ItemList) world.sections().get(0);
        assertThat(list.items()).containsExactly("Find the diamond");
        assertThat(list.emptyMessage()).isEqualTo("No goals.");
    }

    @Test
    void annotatedSectionWrappedWhenRequiredTagsPresent() throws Exception {
        var yaml = """
                sections:
                  - type: entity-group
                    header: Secret
                    requiredTags:
                      - enhanced
                """;
        var world = compiler.compile(mapper.readValue(yaml, WorldDefinition.class));
        assertThat(world.sections().get(0)).isInstanceOf(AnnotatedSection.class);
        var annotated = (AnnotatedSection) world.sections().get(0);
        assertThat(annotated.requiredTags()).containsExactly("enhanced");
    }

    @Test
    void annotatedSectionWithResolutionFallback() throws Exception {
        var yaml = """
                entities:
                  door:
                    displayName: Hidden Door
                sections:
                  - type: entity-group
                    header: Secret Room
                    requiredTags:
                      - perception
                    resolutions:
                      REDUCED:
                        type: text
                        header: Secret Room
                        content: You sense something.
                    entityRefs:
                      - door
                """;
        var world = compiler.compile(mapper.readValue(yaml, WorldDefinition.class));
        var annotated = (AnnotatedSection) world.sections().get(0);
        assertThat(annotated.resolutions()).containsKey(ResolutionTier.REDUCED);
        var fallback = (ObservationSection.TextBlock) annotated.resolutions()
                .get(ResolutionTier.REDUCED);
        assertThat(fallback.content()).isEqualTo("You sense something.");
    }

    @Test
    void unannotatedSectionNotWrapped() throws Exception {
        var yaml = """
                sections:
                  - type: text
                    header: Location
                    content: A room.
                """;
        var world = compiler.compile(mapper.readValue(yaml, WorldDefinition.class));
        assertThat(world.sections().get(0)).isInstanceOf(ObservationSection.TextBlock.class);
        assertThat(world.sections().get(0)).isNotInstanceOf(AnnotatedSection.class);
    }

    @Test
    void pipelineCompiles() throws Exception {
        var yaml = """
                pipeline:
                  - type: perception
                """;
        var world = compiler.compile(mapper.readValue(yaml, WorldDefinition.class));
        assertThat(world.pipeline()).isNotNull();
    }

    @Test
    void rendererThresholdsCompile() throws Exception {
        var yaml = """
                renderer:
                  verbatimThreshold: 5
                  groupedThreshold: 20
                """;
        var world = compiler.compile(mapper.readValue(yaml, WorldDefinition.class));
        assertThat(world.rendererThresholds()).isNotNull();
        assertThat(world.rendererThresholds().verbatimThreshold()).isEqualTo(5);
        assertThat(world.rendererThresholds().groupedThreshold()).isEqualTo(20);
    }

    @Test
    void rendererValidatesGroupedGreaterThanVerbatim() throws Exception {
        var yaml = """
                renderer:
                  verbatimThreshold: 20
                  groupedThreshold: 5
                """;
        assertThatThrownBy(() -> compiler.compile(
                mapper.readValue(yaml, WorldDefinition.class)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fullEndToEnd() throws Exception {
        var yaml = """
                actions:
                  - type: MOVE
                    description: Move to a room
                  - type: TAKE
                    description: Pick up an object
                entities:
                  poison:
                    displayName: Rat Poison
                    affordances:
                      - actionType: TAKE
                  tea:
                    displayName: Tea Service
                    affordances:
                      - actionType: USE
                        acceptsItems:
                          - poison
                sections:
                  - type: entity-group
                    header: Objects
                    entityRefs:
                      - poison
                      - tea
                  - type: text
                    header: Location
                    content: A kitchen.
                  - type: item-list
                    header: Goals
                    items:
                      - Find the diamond
                pipeline:
                  - type: perception
                renderer:
                  verbatimThreshold: 5
                """;
        var world = compiler.compile(mapper.readValue(yaml, WorldDefinition.class));
        assertThat(world.actions()).hasSize(2);
        assertThat(world.entities()).hasSize(2);
        assertThat(world.sections()).hasSize(3);
        assertThat(world.pipeline()).isNotNull();
        assertThat(world.rendererThresholds().verbatimThreshold()).isEqualTo(5);
        assertThat(world.rendererThresholds().groupedThreshold()).isNull();
    }
}
