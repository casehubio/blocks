package io.casehub.blocks.agentic.yaml.compiler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.jackson.YamlMappers;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.FailurePolicy;
import io.casehub.blocks.agentic.activation.OnExplicitDispatch;
import io.casehub.blocks.agentic.aggregation.CollectAll;
import io.casehub.blocks.agentic.aggregation.MajorityVote;
import io.casehub.blocks.agentic.aggregation.PassThrough;
import io.casehub.blocks.agentic.decomposition.IdentityDecomposition;
import io.casehub.blocks.agentic.model.PatternType;
import io.casehub.blocks.agentic.routing.FirstMatchRouting;
import io.casehub.blocks.agentic.routing.RoundRobinRouting;
import io.casehub.blocks.agentic.routing.SelectAllRouting;
import io.casehub.blocks.agentic.routing.SequentialRouting;
import io.casehub.blocks.agentic.termination.MaxIterationsTermination;
import io.casehub.blocks.agentic.yaml.spec.PatternSpec;
import io.casehub.platform.expression.MvelExpressionEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class PatternCompilerTest {

    private ObjectMapper mapper;
    private PatternCompiler compiler;

    @BeforeEach
    void setUp() {
        mapper = YamlMappers.create();
        mapper.registerModule(new JavaTimeModule());
        compiler = new PatternCompiler(new MvelExpressionEngine());
    }

    @Test
    void compilesSupervisor() throws IOException {
        var spec = loadPattern("supervisor");
        var model = compiler.compile(spec);
        assertThat(model.patternType()).isEqualTo(PatternType.SUPERVISOR);
        assertThat(model.routing()).isInstanceOf(FirstMatchRouting.class);
        assertThat(model.aggregation()).isInstanceOf(CollectAll.class);
        assertThat(model.activation()).isInstanceOf(OnExplicitDispatch.class);
        assertThat(model.decomposition()).isInstanceOf(IdentityDecomposition.class);
        assertThat(model.termination()).isInstanceOf(MaxIterationsTermination.class);
        assertThat(model.candidateSupplier().get()).hasSize(2);
        assertThat(model.failurePolicy()).isEqualTo(FailurePolicy.defaults());
    }

    @Test
    void compilesDebate() throws IOException {
        var spec = loadPattern("debate");
        var model = compiler.compile(spec);
        assertThat(model.patternType()).isEqualTo(PatternType.DEBATE);
        assertThat(model.routing()).isInstanceOf(RoundRobinRouting.class);
        assertThat(model.candidateSupplier().get()).hasSize(2);
    }

    @Test
    void compilesLoop() throws IOException {
        var spec = loadPattern("loop");
        var model = compiler.compile(spec);
        assertThat(model.patternType()).isEqualTo(PatternType.LOOP);
        assertThat(model.termination()).isInstanceOf(MaxIterationsTermination.class);
    }

    @Test
    void compilesParallelWithDefaults() throws IOException {
        var spec = loadPattern("parallel");
        var model = compiler.compile(spec);
        assertThat(model.patternType()).isEqualTo(PatternType.PARALLEL);
        assertThat(model.routing()).isInstanceOf(SelectAllRouting.class);
        assertThat(model.aggregation()).isInstanceOf(CollectAll.class);
        assertThat(model.candidateSupplier().get()).hasSize(3);
    }

    @Test
    void compilesVotingWithDefaults() throws IOException {
        var spec = loadPattern("voting");
        var model = compiler.compile(spec);
        assertThat(model.patternType()).isEqualTo(PatternType.VOTING);
        assertThat(model.routing()).isInstanceOf(SelectAllRouting.class);
        assertThat(model.aggregation()).isInstanceOf(MajorityVote.class);
    }

    @Test
    void compilesSequenceWithAgentCountTermination() throws IOException {
        var spec = loadPattern("sequence");
        var model = compiler.compile(spec);
        assertThat(model.patternType()).isEqualTo(PatternType.SEQUENCE);
        assertThat(model.routing()).isInstanceOf(SequentialRouting.class);
        assertThat(model.termination()).isInstanceOf(MaxIterationsTermination.class);
        assertThat(model.candidateSupplier().get()).hasSize(3);
    }

    @Test
    void compilesConditional() throws IOException {
        var spec = loadPattern("conditional");
        var model = compiler.compile(spec);
        assertThat(model.patternType()).isEqualTo(PatternType.CONDITIONAL);
        assertThat(model.routing()).isInstanceOf(FirstMatchRouting.class);
    }

    @Test
    void compilesHtn() throws IOException {
        var spec = loadPattern("htn");
        var model = compiler.compile(spec);
        assertThat(model.patternType()).isEqualTo(PatternType.HTN);
        assertThat(model.candidateSupplier().get()).hasSize(3);
    }


    @Test
    void compilesSupervisorWithExpressions() throws IOException {
        var spec  = loadPattern("supervisor-with-expressions");
        var model = compiler.compile(spec);
        assertThat(model.patternType()).isEqualTo(PatternType.SUPERVISOR);
        assertThat(model.routing()).isInstanceOf(FirstMatchRouting.class);
        assertThat(model.candidateSupplier().get()).hasSize(2);
    }

    @Test
    void compilesComposedAgent() throws IOException {
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
        var model = compiler.compile(spec);
        var candidate = model.candidateSupplier().get().get(0);
        assertThat(candidate.ref()).isInstanceOf(AgentRef.ComposedAgent.class);
        var composed = (AgentRef.ComposedAgent) candidate.ref();
        assertThat(composed.model().patternType()).isEqualTo(PatternType.PARALLEL);
    }

    @Test
    void failurePolicyFromYaml() throws IOException {
        var yaml = """
                type: supervisor
                failurePolicy:
                  onRoutingFailure: RETRY_BROADER
                  onDeadlock: ESCALATE
                  agentRetry:
                    maxRetries: 5
                    backoff: PT2S
                    backoffStrategy: EXPONENTIAL
                    onExhausted: ESCALATE
                agents:
                  - type: worker
                    name: agent-1
                """;
        var spec = mapper.readValue(yaml, PatternSpec.class);
        var model = compiler.compile(spec);
        assertThat(model.failurePolicy().onRoutingFailure())
                .isEqualTo(FailurePolicy.RoutingFailureAction.RETRY_BROADER);
        assertThat(model.failurePolicy().onDeadlock())
                .isEqualTo(FailurePolicy.AggregationFailureAction.ESCALATE);
    }

    @Test
    void defaultsAppliedWhenFieldsOmitted() throws IOException {
        var yaml = """
                type: supervisor
                agents:
                  - type: worker
                    name: agent-1
                """;
        var spec = mapper.readValue(yaml, PatternSpec.class);
        var model = compiler.compile(spec);
        assertThat(model.routing()).isInstanceOf(FirstMatchRouting.class);
        assertThat(model.activation()).isInstanceOf(OnExplicitDispatch.class);
        assertThat(model.decomposition()).isInstanceOf(IdentityDecomposition.class);
        assertThat(model.aggregation()).isInstanceOf(PassThrough.class);
        assertThat(model.failurePolicy()).isEqualTo(FailurePolicy.defaults());
        assertThat(model.task()).isEqualTo("execution");
    }

    @Test
    void candidateDescriptorWiredFromSpec() throws IOException {
        var spec = loadPattern("supervisor");
        var model = compiler.compile(spec);
        var candidates = model.candidateSupplier().get();

        var analyst = candidates.get(0);
        assertThat(analyst.descriptor()).isNotNull();
        assertThat(analyst.descriptor().name()).isEqualTo("analyst");
        assertThat(analyst.descriptor().briefing()).isEqualTo("Analyses input data");
        assertThat(analyst.descriptor().capabilities()).hasSize(1);
        assertThat(analyst.descriptor().capabilities().get(0).name()).isEqualTo("data-analysis");

        var reviewer = candidates.get(1);
        assertThat(reviewer.descriptor()).isNotNull();
        assertThat(reviewer.descriptor().name()).isEqualTo("reviewer");
        assertThat(reviewer.descriptor().briefing()).isNull();
        assertThat(reviewer.descriptor().capabilities()).isEmpty();
    }

    @Test
    void candidateDescriptorMultipleCapabilities() throws IOException {
        var yaml = """
                type: supervisor
                agents:
                  - type: worker
                    name: multi-agent
                    description: "Does many things"
                    capabilities:
                      - analysis
                      - synthesis
                      - reporting
                """;
        var spec = mapper.readValue(yaml, PatternSpec.class);
        var model = compiler.compile(spec);
        var candidate = model.candidateSupplier().get().get(0);
        assertThat(candidate.descriptor()).isNotNull();
        assertThat(candidate.descriptor().capabilities()).hasSize(3);
        assertThat(candidate.descriptor().capabilities().get(0).name()).isEqualTo("analysis");
        assertThat(candidate.descriptor().capabilities().get(1).name()).isEqualTo("synthesis");
        assertThat(candidate.descriptor().capabilities().get(2).name()).isEqualTo("reporting");
    }

    private PatternSpec loadPattern(String name) throws IOException {
        try (var is = getClass().getResourceAsStream("/patterns/" + name + ".yaml")) {
            return mapper.readValue(is, PatternSpec.class);
        }
    }
}
