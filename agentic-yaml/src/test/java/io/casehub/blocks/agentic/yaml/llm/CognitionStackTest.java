package io.casehub.blocks.agentic.yaml.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.blocks.agentic.yaml.compiler.CognitionCompiler;
import io.casehub.blocks.agentic.yaml.spec.cognition.CognitionDefinition;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class CognitionStackTest {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
            .registerModule(new JavaTimeModule());

    @Test
    void buildsFromCompiledCognition() throws IOException {
        var compiled = loadCompiledCognition();
        var stack = CognitionStack.from(compiled, null);

        assertThat(stack.mood()).isNotNull();
        assertThat(stack.drives()).isNotNull();
        assertThat(stack.narrative()).isNotNull();
    }

    @Test
    void tickProducesMoodState() throws IOException {
        var compiled = loadCompiledCognition();
        var stack = CognitionStack.from(compiled, null);

        stack.tick("leonardo", "test-tenant", null);

        assertThat(stack.mood().currentMood("leonardo", "test-tenant"))
                .isPresent();
    }

    @Test
    void promptSectionsNonEmpty() throws IOException {
        var compiled = loadCompiledCognition();
        var stack = CognitionStack.from(compiled, null);

        stack.tick("leonardo", "test-tenant", null);

        var sections = stack.promptSections();
        assertThat(sections).isNotEmpty();
    }

    private static io.casehub.blocks.agentic.yaml.compiler.CompiledCognition loadCompiledCognition() throws IOException {
        try (var is = CognitionStackTest.class.getResourceAsStream(
                "/examples/historical-encounter/cognition.yaml")) {
            var def = YAML.readValue(is, CognitionDefinition.class);
            return new CognitionCompiler().compile(def);
        }
    }
}
