package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.jackson.YamlMappers;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class PatternSpecSerializationTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = YamlMappers.create();
        mapper.registerModule(new JavaTimeModule());
    }

    @ParameterizedTest
    @ValueSource(strings = {"supervisor", "debate", "loop", "parallel",
            "voting", "conditional", "sequence", "htn"})
    void deserializesAllTopologies(String topology) throws IOException {
        var yaml = readFixture(topology);
        var spec = mapper.readValue(yaml, PatternSpec.class);
        assertThat(spec).isNotNull();
        assertThat(spec.agents()).isNotEmpty();
    }

    @Test
    void supervisorFields() throws IOException {
        var spec = loadPattern("supervisor", PatternSpec.Supervisor.class);
        assertThat(spec.routing()).isInstanceOf(RoutingSpec.FirstMatch.class);
        assertThat(((RoutingSpec.FirstMatch) spec.routing()).guard()).isEqualTo("priority > 5");
        assertThat(spec.termination()).hasSize(1);
        assertThat(spec.termination().get(0)).isInstanceOf(TerminationSpec.MaxIterations.class);
        assertThat(spec.aggregation()).isInstanceOf(AggregationSpec.CollectAll.class);
        assertThat(spec.agents()).hasSize(2);
    }

    @Test
    void debateFields() throws IOException {
        var spec = loadPattern("debate", PatternSpec.Debate.class);
        assertThat(spec.maxRounds()).isEqualTo(7);
        assertThat(spec.judge()).isNotNull();
        assertThat(spec.judge().name()).isEqualTo("arbiter");
    }

    @Test
    void loopFields() throws IOException {
        var spec = loadPattern("loop", PatternSpec.Loop.class);
        assertThat(spec.maxIterations()).isEqualTo(15);
        assertThat(spec.exitCondition()).isEqualTo("qualityScore > 0.9");
    }

    @Test
    void conditionalBranches() throws IOException {
        var spec = loadPattern("conditional", PatternSpec.Conditional.class);
        assertThat(spec.branches()).hasSize(2);
        assertThat(spec.branches().get(0).condition()).isEqualTo("category == 'medical'");
        assertThat(spec.branches().get(0).agent().name()).isEqualTo("medical-expert");
    }

    @Test
    void htnRootTask() throws IOException {
        var spec = loadPattern("htn", PatternSpec.Htn.class);
        assertThat(spec.rootTask()).isInstanceOf(TaskNodeSpec.Compound.class);
        var root = (TaskNodeSpec.Compound) spec.rootTask();
        assertThat(root.name()).isEqualTo("main-task");
        assertThat(root.subtasks()).hasSize(2);
    }

    @Test
    void composedAgentWithNestedPattern() throws IOException {
        var yaml = """
                type: supervisor
                agents:
                  - type: composed
                    name: research-team
                    pattern:
                      type: parallel
                      agents:
                        - type: worker
                          name: web-researcher
                        - type: worker
                          name: db-researcher
                """;
        var spec = mapper.readValue(yaml, PatternSpec.class);
        var composed = (AgentRefSpec.Composed) spec.agents().get(0);
        assertThat(composed.pattern()).isInstanceOf(PatternSpec.Parallel.class);
        assertThat(composed.pattern().agents()).hasSize(2);
    }

    @Test
    void judgmentSpec() throws IOException {
        var yaml = """
                type: supervisor
                judgment:
                  trigger:
                    type: iteration-based
                    every: 3
                  caller:
                    type: single
                    callerName: quality-judge
                agents:
                  - type: worker
                    name: worker-1
                """;
        var spec = (PatternSpec.Supervisor) mapper.readValue(yaml, PatternSpec.class);
        assertThat(spec.judgment()).isNotNull();
        assertThat(spec.judgment().trigger())
                .isInstanceOf(JudgmentSpec.TriggerSpec.IterationBased.class);
        assertThat(((JudgmentSpec.TriggerSpec.IterationBased) spec.judgment().trigger())
                .every()).isEqualTo(3);
    }

    @Test
    void failurePolicyDeserializes() throws IOException {
        var yaml = """
                type: supervisor
                failurePolicy:
                  onRoutingFailure: RETRY_BROADER
                  onDeadlock: ESCALATE
                agents:
                  - type: worker
                    name: agent-1
                """;
        var spec = (PatternSpec.Supervisor) mapper.readValue(yaml, PatternSpec.class);
        assertThat(spec.failurePolicy()).isNotNull();
        assertThat(spec.failurePolicy().onRoutingFailure().name()).isEqualTo("RETRY_BROADER");
    }

    private <T extends PatternSpec> T loadPattern(String name, Class<T> type) throws IOException {
        var spec = mapper.readValue(readFixture(name), PatternSpec.class);
        assertThat(spec).isInstanceOf(type);
        return type.cast(spec);
    }

    private String readFixture(String name) throws IOException {
        try (var is = getClass().getResourceAsStream("/patterns/" + name + ".yaml")) {
            if (is == null) throw new IOException("Missing fixture: " + name + ".yaml");
            return new String(is.readAllBytes());
        }
    }
}
