package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.jackson.YamlMappers;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.blocks.agentic.yaml.registry.CoalitionEvaluatorRegistry;
import io.casehub.blocks.agentic.yaml.registry.EventConcurrencyPolicyRegistry;
import io.casehub.blocks.agentic.yaml.registry.ExecutionBackendRegistry;
import io.casehub.blocks.agentic.yaml.registry.ExecutionListenerRegistry;
import io.casehub.blocks.agentic.yaml.registry.VerifierStrategyRegistry;
import io.casehub.blocks.agentic.coalition.CapabilityCoverageEvaluator;
import io.casehub.blocks.agentic.listener.EventLogListener;
import io.casehub.blocks.agentic.listener.LedgerExecutionListener;
import io.casehub.blocks.agentic.listener.MetricsListener;
import io.casehub.blocks.agentic.intention.ReconsiderationReason;
import io.casehub.blocks.agentic.intention.ReconsiderationSignal;
import io.opentelemetry.api.metrics.Meter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ExecutionInfraSpecSerializationTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = YamlMappers.create();
        mapper.registerModule(new JavaTimeModule());
    }

    @Nested
    class ExecutionBackendSpecs {
        @Test
        void reactive() throws Exception {
            var spec = mapper.readValue("type: reactive", ExecutionBackendSpec.class);
            assertThat(spec).isInstanceOf(ExecutionBackendSpec.Reactive.class);
        }

        @Test
        void choreographed() throws Exception {
            var yaml = """
                    type: choreographed
                    policy:
                      type: serialize
                    """;
            var spec = mapper.readValue(yaml, ExecutionBackendSpec.class);
            assertThat(spec).isInstanceOf(ExecutionBackendSpec.Choreographed.class);
            var c = (ExecutionBackendSpec.Choreographed) spec;
            assertThat(c.policy()).isInstanceOf(EventConcurrencyPolicySpec.Serialize.class);
        }

        @Test
        void engineHosted() throws Exception {
            var spec = mapper.readValue("type: engine-hosted", ExecutionBackendSpec.class);
            assertThat(spec).isInstanceOf(ExecutionBackendSpec.EngineHosted.class);
        }
    }

    @Nested
    class EventConcurrencyPolicySpecs {
        @Test
        void serialize() throws Exception {
            var spec = mapper.readValue("type: serialize", EventConcurrencyPolicySpec.class);
            assertThat(spec).isInstanceOf(EventConcurrencyPolicySpec.Serialize.class);
        }

        @Test
        void coalesce() throws Exception {
            var spec = mapper.readValue("type: coalesce", EventConcurrencyPolicySpec.class);
            assertThat(spec).isInstanceOf(EventConcurrencyPolicySpec.Coalesce.class);
            assertThat(((EventConcurrencyPolicySpec.Coalesce) spec).window()).isNull();
        }

        @Test
        void coalesceWithWindow() throws Exception {
            var yaml = """
                    type: coalesce
                    window: PT5S
                    """;
            var spec = mapper.readValue(yaml, EventConcurrencyPolicySpec.class);
            assertThat(spec).isInstanceOf(EventConcurrencyPolicySpec.Coalesce.class);
            assertThat(((EventConcurrencyPolicySpec.Coalesce) spec).window())
                    .isEqualTo(Duration.ofSeconds(5));
        }

        @Test
        void coalesceBySource() throws Exception {
            var spec = mapper.readValue("type: coalesce-by-source", EventConcurrencyPolicySpec.class);
            assertThat(spec).isInstanceOf(EventConcurrencyPolicySpec.CoalesceBySource.class);
        }
    }

    @Nested
    class ExecutionListenerSpecs {
        @Test
        void eventLog() throws Exception {
            var spec = mapper.readValue("type: event-log", ExecutionListenerSpec.class);
            assertThat(spec).isInstanceOf(ExecutionListenerSpec.EventLog.class);
        }

        @Test
        void ledger() throws Exception {
            var yaml = """
                    type: ledger
                    supervisorActorId: supervisor-1
                    """;
            var spec = mapper.readValue(yaml, ExecutionListenerSpec.class);
            assertThat(spec).isInstanceOf(ExecutionListenerSpec.Ledger.class);
            assertThat(((ExecutionListenerSpec.Ledger) spec).supervisorActorId())
                    .isEqualTo("supervisor-1");
        }

        @Test
        void metrics() throws Exception {
            var spec = mapper.readValue("type: metrics", ExecutionListenerSpec.class);
            assertThat(spec).isInstanceOf(ExecutionListenerSpec.Metrics.class);
        }

        @Test
        void checkpointing() throws Exception {
            var spec = mapper.readValue("type: checkpointing", ExecutionListenerSpec.class);
            assertThat(spec).isInstanceOf(ExecutionListenerSpec.Checkpointing.class);
        }
    }

    @Nested
    class VerifierStrategySpecs {
        @Test
        void llmEvaluation() throws Exception {
            var spec = mapper.readValue("type: llm-evaluation", VerifierStrategySpec.class);
            assertThat(spec).isInstanceOf(VerifierStrategySpec.LlmEvaluation.class);
        }

        @Test
        void schemaValidation() throws Exception {
            var spec = mapper.readValue("type: schema-validation", VerifierStrategySpec.class);
            assertThat(spec).isInstanceOf(VerifierStrategySpec.SchemaValidation.class);
        }
    }

    @Nested
    class CoalitionEvaluatorSpecs {
        @Test
        void capabilityCoverage() throws Exception {
            var spec = mapper.readValue("type: capability-coverage", CoalitionEvaluatorSpec.class);
            assertThat(spec).isInstanceOf(CoalitionEvaluatorSpec.CapabilityCoverage.class);
        }
    }

    @Nested
    class JointIntentionSpecs {
        @Test
        void fullSpec() throws Exception {
            var yaml = """
                    intentionId: intent-1
                    planDescription: Coordinate delivery
                    parties:
                      - agent-a
                      - agent-b
                    """;
            var spec = mapper.readValue(yaml, JointIntentionSpec.class);
            assertThat(spec.intentionId()).isEqualTo("intent-1");
            assertThat(spec.planDescription()).isEqualTo("Coordinate delivery");
            assertThat(spec.parties()).containsExactlyInAnyOrder("agent-a", "agent-b");
        }

        @Test
        void emptyPartiesThrows() {
            assertThatThrownBy(() -> new JointIntentionSpec("id", "plan", Set.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class ReconsiderationSignalSpecs {
        @Test
        void directReuse() throws Exception {
            var yaml = """
                    reason: CONTEXT_CHANGED
                    detail: Environment shifted
                    shouldDrop: false
                    """;
            var spec = mapper.readValue(yaml, ReconsiderationSignal.class);
            assertThat(spec.reason()).isEqualTo(ReconsiderationReason.CONTEXT_CHANGED);
            assertThat(spec.detail()).isEqualTo("Environment shifted");
            assertThat(spec.shouldDrop()).isFalse();
        }
    }

    @Nested
    class RegistryTests {
        @Test
        void eventConcurrencyPolicyRegistry() {
            var registry = new EventConcurrencyPolicyRegistry();
            assertThat(registry.resolve(new EventConcurrencyPolicySpec.Serialize())).isNotNull();
            assertThat(registry.resolve(new EventConcurrencyPolicySpec.Coalesce(null))).isNotNull();
            assertThat(registry.resolve(new EventConcurrencyPolicySpec.Coalesce(Duration.ofSeconds(5)))).isNotNull();
            assertThat(registry.resolve(new EventConcurrencyPolicySpec.CoalesceBySource())).isNotNull();
        }

        @Test
        void executionBackendRegistry() {
            var registry = new ExecutionBackendRegistry(new EventConcurrencyPolicyRegistry());
            assertThat(registry.resolve(new ExecutionBackendSpec.Reactive())).isNotNull();
            assertThat(registry.resolve(new ExecutionBackendSpec.Choreographed(
                    new EventConcurrencyPolicySpec.Serialize()))).isNotNull();
        }

        @Test
        void executionListenerRegistryEventLog() {
            var registry = new ExecutionListenerRegistry();
            var sink = mock(EventLogListener.EventSink.class);
            var listener = registry.resolve(new ExecutionListenerSpec.EventLog(), sink, null, null);
            assertThat(listener).isInstanceOf(EventLogListener.class);
        }

        @Test
        void executionListenerRegistryLedger() {
            var registry = new ExecutionListenerRegistry();
            var sink = mock(LedgerExecutionListener.LedgerSink.class);
            var listener = registry.resolve(new ExecutionListenerSpec.Ledger("actor-1"), null, sink, null);
            assertThat(listener).isInstanceOf(LedgerExecutionListener.class);
        }

        @Test
        void executionListenerRegistryMetrics() {
            var registry = new ExecutionListenerRegistry();
            var meter = io.opentelemetry.api.OpenTelemetry.noop().getMeter("test");
            var listener = registry.resolve(new ExecutionListenerSpec.Metrics(), null, null, meter);
            assertThat(listener).isInstanceOf(MetricsListener.class);
        }

        @Test
        void executionListenerMissingSinkThrows() {
            var registry = new ExecutionListenerRegistry();
            assertThatThrownBy(() ->
                    registry.resolve(new ExecutionListenerSpec.EventLog(), null, null, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("EventSink");
        }

        @Test
        void executionBackendRegistryEngineHosted() {
            var registry = new ExecutionBackendRegistry(new EventConcurrencyPolicyRegistry());
            assertThatThrownBy(() -> registry.resolve(new ExecutionBackendSpec.EngineHosted()))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void executionListenerRegistryCheckpointing() {
            var registry = new ExecutionListenerRegistry();
            assertThatThrownBy(() -> registry.resolve(new ExecutionListenerSpec.Checkpointing(), null, null, null))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void verifierStrategyRegistry() {
            var registry = new VerifierStrategyRegistry();
            assertThat(registry.resolve(new VerifierStrategySpec.LlmEvaluation())).isEqualTo("llm-evaluation");
            assertThat(registry.resolve(new VerifierStrategySpec.SchemaValidation())).isEqualTo("schema-validation");
        }

        @Test
        void coalitionEvaluatorRegistry() {
            var registry = new CoalitionEvaluatorRegistry();
            assertThat(registry.resolve(new CoalitionEvaluatorSpec.CapabilityCoverage()))
                    .isInstanceOf(CapabilityCoverageEvaluator.class);
        }
    }
}
