package io.casehub.blocks.annotations.deployment;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.pattern.DebateBuilder;
import io.casehub.blocks.agentic.pattern.SupervisorBuilder;
import io.casehub.blocks.annotations.Agent;
import io.casehub.blocks.annotations.Debate;
import io.casehub.blocks.annotations.Debater;
import io.casehub.blocks.annotations.Judge;
import io.casehub.blocks.annotations.Supervisor;
import io.casehub.engine.annotations.Customize;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomizeValidationTest {

    private Index indexClasses(Class<?>... classes) throws IOException {
        Indexer indexer = new Indexer();
        for (Class<?> clazz : classes) {
            indexClass(indexer, clazz);
        }
        return indexer.complete();
    }

    private void indexClass(Indexer indexer, Class<?> clazz) throws IOException {
        String resourceName = "/" + clazz.getName().replace('.', '/') + ".class";
        try (InputStream stream = clazz.getResourceAsStream(resourceName)) {
            if (stream != null) {
                indexer.index(stream);
            }
        }
    }

    // --- Fixtures ---

    interface TestCdiBean {}

    interface ValidCustomizedDebate {
        @Debate(maxRounds = 5)
        String review(
                @Debater(role = "critic", systemPrompt = "Challenge") AgentRef critic,
                @Judge(systemPrompt = "Judge") AgentRef judge,
                String document);

        @Customize
        static void customize(DebateBuilder<?> builder, TestCdiBean myBean) {
        }
    }

    interface ValidCustomizedSupervisor {
        @Supervisor(maxIterations = 10)
        String triage(
                @Agent(name = "a", systemPrompt = "p") AgentRef a);

        @Customize
        static void customize(SupervisorBuilder<?> builder) {
        }
    }

    interface BadCustomizeNonCdiParam {
        @Debate(maxRounds = 5)
        String review(
                @Debater(role = "a", systemPrompt = "p") AgentRef a,
                String doc);

        @Customize
        static void customize(DebateBuilder<?> builder, String notCdiBean) {
        }
    }

    interface BadCustomizeNoPatternMatch {
        @Debate(maxRounds = 5)
        String review(
                @Debater(role = "a", systemPrompt = "p") AgentRef a,
                String doc);

        @Customize
        static void customize(SupervisorBuilder<?> builder) {
        }
    }

    // --- Tests ---

    @Test
    void accepts_customize_with_cdi_parameter() throws IOException {
        Index index = indexClasses(ValidCustomizedDebate.class);
        var step = new CustomizeAnnotationStep();

        assertThatCode(() -> step.validate(index))
                .doesNotThrowAnyException();
    }

    @Test
    void accepts_customize_with_builder_only() throws IOException {
        Index index = indexClasses(ValidCustomizedSupervisor.class);
        var step = new CustomizeAnnotationStep();

        assertThatCode(() -> step.validate(index))
                .doesNotThrowAnyException();
    }

    @Test
    void rejects_customize_with_non_cdi_parameter() throws IOException {
        Index index = indexClasses(BadCustomizeNonCdiParam.class);
        var step = new CustomizeAnnotationStep();

        assertThatThrownBy(() -> step.validate(index))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a CDI bean");
    }

    @Test
    void rejects_customize_with_mismatched_builder() throws IOException {
        Index index = indexClasses(BadCustomizeNoPatternMatch.class);
        var step = new CustomizeAnnotationStep();

        assertThatThrownBy(() -> step.validate(index))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no matching pattern annotation");
    }

    @Test
    void extracts_customize_info() throws IOException {
        Index index = indexClasses(ValidCustomizedDebate.class);
        var step = new CustomizeAnnotationStep();
        var infos = step.scan(index);

        assertThat(infos).hasSize(1);
        assertThat(infos.get(0).builderType()).contains("DebateBuilder");
        assertThat(infos.get(0).cdiParameterTypes()).hasSize(1);
        assertThat(infos.get(0).cdiParameterTypes().get(0)).contains("TestCdiBean");
    }
}
