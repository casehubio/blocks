package io.casehub.blocks.conversation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.work.progress.ProgressInstance;
import io.casehub.work.progress.ProgressStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultProgressRendererTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final DefaultProgressRenderer renderer = new DefaultProgressRenderer();

    @Test
    void percentage_rendersValueWithLabel() {
        var pi = instance("percentage",
                defn().put("label", "Calibration"),
                state().put("value", 63),
                ProgressStatus.ACTIVE);

        assertThat(renderer.render(pi)).isEqualTo("Calibration: 63%");
    }

    @Test
    void percentage_completed_appendsCheckmark() {
        var pi = instance("percentage",
                defn().put("label", "Calibration"),
                state().put("value", 100),
                ProgressStatus.COMPLETED);

        assertThat(renderer.render(pi)).isEqualTo("Calibration: 100% ✓");
    }

    @Test
    void percentage_failed_appendsCross() {
        var pi = instance("percentage",
                defn().put("label", "Calibration"),
                state().put("value", 63),
                ProgressStatus.FAILED);

        assertThat(renderer.render(pi)).isEqualTo("Calibration: 63% ✗");
    }

    @Test
    void count_rendersCurrentOfTotal() {
        var def = defn().put("label", "Coverage").put("unit", "sensors");
        var pi = instance("count", def,
                state().put("current", 3).put("total", 7),
                ProgressStatus.ACTIVE);

        assertThat(renderer.render(pi)).isEqualTo("Coverage: 3 of 7 sensors");
    }

    @Test
    void count_withoutUnit_omitsUnit() {
        var pi = instance("count",
                defn().put("label", "Coverage"),
                state().put("current", 3).put("total", 7),
                ProgressStatus.ACTIVE);

        assertThat(renderer.render(pi)).isEqualTo("Coverage: 3 of 7");
    }

    @Test
    void count_completed_appendsCheckmark() {
        var pi = instance("count",
                defn().put("label", "Coverage"),
                state().put("current", 7).put("total", 7),
                ProgressStatus.COMPLETED);

        assertThat(renderer.render(pi)).isEqualTo("Coverage: 7 of 7 ✓");
    }

    @Test
    void count_failed_appendsCross() {
        var pi = instance("count",
                defn().put("label", "Coverage"),
                state().put("current", 3).put("total", 7),
                ProgressStatus.FAILED);

        assertThat(renderer.render(pi)).isEqualTo("Coverage: 3 of 7 ✗");
    }

    @Test
    void step_rendersArrowChain() {
        var def = MAPPER.createArrayNode()
                .add(stepDef("unpack"))
                .add(stepDef("assembly"))
                .add(stepDef("calibration"))
                .add(stepDef("testing"));

        var steps = MAPPER.createObjectNode();
        steps.set("unpack", MAPPER.createObjectNode().put("status", "COMPLETED"));
        steps.set("assembly", MAPPER.createObjectNode().put("status", "COMPLETED"));
        steps.set("calibration", MAPPER.createObjectNode().put("status", "ACTIVE"));
        steps.set("testing", MAPPER.createObjectNode().put("status", "PENDING"));

        var pi = instance("step", def, state().set("steps", steps), ProgressStatus.ACTIVE);

        assertThat(renderer.render(pi))
                .isEqualTo("unpack ✓ → assembly ✓ → calibration ⏳ → testing ○");
    }

    @Test
    void step_skippedAndFailed_renderCorrectGlyphs() {
        var def = MAPPER.createArrayNode()
                .add(stepDef("a"))
                .add(stepDef("b"))
                .add(stepDef("c"));

        var steps = MAPPER.createObjectNode();
        steps.set("a", MAPPER.createObjectNode().put("status", "COMPLETED"));
        steps.set("b", MAPPER.createObjectNode().put("status", "SKIPPED"));
        steps.set("c", MAPPER.createObjectNode().put("status", "FAILED"));

        var pi = instance("step", def, state().set("steps", steps), ProgressStatus.FAILED);

        assertThat(renderer.render(pi)).isEqualTo("a ✓ → b ⊘ → c ✗");
    }

    @Test
    void unknownShape_fallsBackToStatusLabel() {
        var pi = instance("custom",
                defn().put("label", "Sync"),
                state(), ProgressStatus.ACTIVE);

        assertThat(renderer.render(pi)).isEqualTo("Sync: ACTIVE");
    }

    @Test
    void nullState_fallsBackToStatusLabel() {
        var pi = instance("percentage",
                defn().put("label", "Calibration"),
                null, ProgressStatus.ACTIVE);

        assertThat(renderer.render(pi)).isEqualTo("Calibration: ACTIVE");
    }

    @Test
    void nullDefinition_useScopeIdAsLabel() {
        var pi = new ProgressInstance(
                UUID.randomUUID(), "tenant-1", "WORK_ITEM", "cal-001",
                null, null, "percentage", null,
                state().put("value", 50),
                ProgressStatus.ACTIVE, null, null, null,
                Instant.now(), Instant.now());

        assertThat(renderer.render(pi)).isEqualTo("cal-001: 50%");
    }

    @Test
    void malformedState_fallsBackToStatusLabel() {
        var pi = instance("percentage",
                defn().put("label", "Calibration"),
                state().put("wrong", true),
                ProgressStatus.ACTIVE);

        assertThat(renderer.render(pi)).isEqualTo("Calibration: ACTIVE");
    }

    @Test
    void labelFromDefinition_takesPrecedenceOverScopeId() {
        var pi = new ProgressInstance(
                UUID.randomUUID(), "tenant-1", "WORK_ITEM", "scope-xyz",
                null, null, "percentage",
                defn().put("label", "Calibration"),
                state().put("value", 42),
                ProgressStatus.ACTIVE, null, null, null,
                Instant.now(), Instant.now());

        assertThat(renderer.render(pi)).isEqualTo("Calibration: 42%");
    }

    private ProgressInstance instance(String shapeType, JsonNode definition,
            JsonNode state, ProgressStatus status) {
        return new ProgressInstance(
                UUID.randomUUID(), "tenant-1", "WORK_ITEM", "scope-1",
                null, null, shapeType, definition, state, status, null, null, null,
                Instant.now(), Instant.now());
    }

    private ObjectNode defn() {
        return MAPPER.createObjectNode();
    }

    private ObjectNode state() {
        return MAPPER.createObjectNode();
    }

    private ObjectNode stepDef(String name) {
        var node = MAPPER.createObjectNode();
        node.put("name", name);
        node.putNull("condition");
        node.put("optional", false);
        node.set("dependsOn", MAPPER.createArrayNode());
        return node;
    }
}
