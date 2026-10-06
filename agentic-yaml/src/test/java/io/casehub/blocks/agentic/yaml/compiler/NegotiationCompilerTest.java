package io.casehub.blocks.agentic.yaml.compiler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.jackson.YamlMappers;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.blocks.agentic.yaml.registry.TerminationConditionRegistry;
import io.casehub.blocks.agentic.yaml.spec.NegotiationSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NegotiationCompilerTest {

    private ObjectMapper mapper;
    private NegotiationCompiler compiler;

    @BeforeEach
    void setUp() {
        mapper = YamlMappers.create();
        mapper.registerModule(new JavaTimeModule());
        compiler = new NegotiationCompiler(new TerminationConditionRegistry(), null);
    }

    @Test
    void compilesWithUnanimousAcceptance() throws Exception {
        var yaml = """
                parties:
                  - buyer
                  - seller
                acceptance:
                  type: unanimous
                """;
        var spec = mapper.readValue(yaml, NegotiationSpec.class);
        var compiled = compiler.compile(spec);
        assertThat(compiled.parties()).containsExactlyInAnyOrder("buyer", "seller");
        assertThat(compiled.projection()).isNotNull();
        assertThat(compiled.termination()).isNull();
    }

    @Test
    void compilesWithThresholdAcceptance() throws Exception {
        var yaml = """
                parties:
                  - a
                  - b
                  - c
                acceptance:
                  type: threshold
                  minAcceptances: 2
                """;
        var spec = mapper.readValue(yaml, NegotiationSpec.class);
        var compiled = compiler.compile(spec);
        assertThat(compiled.parties()).hasSize(3);
    }

    @Test
    void compilesWithTermination() throws Exception {
        var yaml = """
                parties:
                  - buyer
                  - seller
                acceptance:
                  type: unanimous
                termination:
                  - type: accepted
                  - type: max-iterations
                    iterations: 10
                """;
        var spec = mapper.readValue(yaml, NegotiationSpec.class);
        var compiled = compiler.compile(spec);
        assertThat(compiled.termination()).isNotNull();
    }

    @Test
    void compilesWithDeadlineTermination() throws Exception {
        var yaml = """
                parties:
                  - buyer
                  - seller
                acceptance:
                  type: majority
                termination:
                  - type: deadline
                    timeout: PT1H
                  - type: terminal-outcome
                """;
        var spec = mapper.readValue(yaml, NegotiationSpec.class);
        var compiled = compiler.compile(spec);
        assertThat(compiled.termination()).isNotNull();
    }

    @Test
    void singleTerminationNotWrappedInComposite() throws Exception {
        var yaml = """
                parties:
                  - buyer
                  - seller
                acceptance:
                  type: unanimous
                termination:
                  - type: accepted
                """;
        var spec = mapper.readValue(yaml, NegotiationSpec.class);
        var compiled = compiler.compile(spec);
        assertThat(compiled.termination()).isNotNull();
        assertThat(compiled.termination().getClass().getSimpleName())
                .isEqualTo("AcceptedTermination");
    }
}
