package io.casehub.blocks.agentic;

import io.casehub.worker.api.WorkerResult;
import io.casehub.worker.api.Worker;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentResultTest {

    private static AgentRef createTestAgent() {
        var worker = Worker.builder()
                .name("test")
                .capabilityNames("test")
                .function(x -> WorkerResult.of(Map.of()))
                .build();
        return new AgentRef.WorkerAgent(worker);
    }

    @Nested
    class Construction {
        @Test
        void carriesAllFields() {
            var agent = createTestAgent();
            var result = new AgentResult(agent, "output", Duration.ofMillis(100),
                    AgentResult.AgentResultStatus.SUCCESS);
            assertThat(result.agent()).isEqualTo(agent);
            assertThat(result.output()).isEqualTo("output");
            assertThat(result.duration()).isEqualTo(Duration.ofMillis(100));
            assertThat(result.status()).isEqualTo(AgentResult.AgentResultStatus.SUCCESS);
        }
    }

    @Nested
    class FactoryMethods {
        @Test
        void successFactory() {
            var agent = createTestAgent();
            var result = AgentResult.success(agent, "output");
            assertThat(result.status()).isEqualTo(AgentResult.AgentResultStatus.SUCCESS);
            assertThat(result.output()).isEqualTo("output");
        }

        @Test
        void failureFactory() {
            var agent = createTestAgent();
            var result = AgentResult.failure(agent, "error");
            assertThat(result.status()).isEqualTo(AgentResult.AgentResultStatus.FAILURE);
        }

        @Test
        void timeoutFactory() {
            var agent = createTestAgent();
            var result = AgentResult.timeout(agent);
            assertThat(result.status()).isEqualTo(AgentResult.AgentResultStatus.TIMEOUT);
        }

        @Test
        void declinedFactory() {
            var agent = createTestAgent();
            var result = AgentResult.declined(agent, "overloaded");
            assertThat(result.status()).isEqualTo(AgentResult.AgentResultStatus.DECLINED);
        }
    }

    @Test
    void allStatusValuesExist() {
        assertThat(AgentResult.AgentResultStatus.values()).containsExactly(
                AgentResult.AgentResultStatus.SUCCESS,
                AgentResult.AgentResultStatus.FAILURE,
                AgentResult.AgentResultStatus.TIMEOUT,
                AgentResult.AgentResultStatus.DECLINED);
    }
}
