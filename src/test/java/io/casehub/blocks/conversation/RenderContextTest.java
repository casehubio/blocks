package io.casehub.blocks.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.work.progress.ProgressInstance;
import io.casehub.work.progress.ProgressStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RenderContextTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void empty_hasEmptyProgressMap() {
        assertThat(RenderContext.EMPTY.progress()).isEmpty();
    }

    @Test
    void withProgress_createsContextWithProgress() {
        var pi = testProgressInstance("percentage",
                MAPPER.createObjectNode().put("value", 50));
        var progress = Map.of("review", List.of(pi));

        var ctx = RenderContext.withProgress(progress);

        assertThat(ctx.progress()).isEqualTo(progress);
        assertThat(ctx.reactions()).isEmpty();
        assertThat(ctx.commonGround()).isNull();
        assertThat(ctx.convergence()).isNull();
    }

    @Test
    void withReactions_hasEmptyProgressMap() {
        var ctx = RenderContext.withReactions(Map.of());
        assertThat(ctx.progress()).isEmpty();
    }

    private static ProgressInstance testProgressInstance(String shapeType,
            com.fasterxml.jackson.databind.JsonNode state) {
        return new ProgressInstance(
                UUID.randomUUID(), "tenant-1", "WORK_ITEM", "scope-1",
                null, null, shapeType, null, state,
                ProgressStatus.ACTIVE, null,
                Instant.now(), Instant.now());
    }
}
