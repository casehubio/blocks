package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class OutputContractTest {

    @Nested
    class Factories {
        @Test
        void nonNull_rejectsNull() {
            assertThat(OutputContract.nonNull().validate(null)).isFalse();
            assertThat(OutputContract.nonNull().validate("value")).isTrue();
        }

        @Test
        void type_checksInstanceOf() {
            var contract = OutputContract.type(String.class);
            assertThat(contract.validate("hello")).isTrue();
            assertThat(contract.validate(42)).isFalse();
            assertThat(contract.validate(null)).isFalse();
        }

        @Test
        void of_wrapsCustomPredicate() {
            var contract = OutputContract.of(o -> o instanceof String s && s.length() > 3);
            assertThat(contract.validate("long")).isTrue();
            assertThat(contract.validate("ab")).isFalse();
        }
    }

    @Nested
    class Composition {
        @Test
        void and_requiresBothToPass() {
            var contract = OutputContract.nonNull().and(OutputContract.type(String.class));
            assertThat(contract.validate("ok")).isTrue();
            assertThat(contract.validate(null)).isFalse();
            assertThat(contract.validate(42)).isFalse();
        }

        @Test
        void and_isAssociative() {
            var c1 = OutputContract.nonNull();
            var c2 = OutputContract.type(String.class);
            var c3 = OutputContract.of(o -> ((String) o).length() > 2);
            var chain = c1.and(c2).and(c3);
            assertThat(chain.validate("abc")).isTrue();
            assertThat(chain.validate("ab")).isFalse();
        }
    }

    @Nested
    class TaskIntegration {
        private static AgentRef agent() {
            return AgentRef.external(s ->
                    CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
        }

        @Test
        void primitiveTask_carriesContract() {
            var contract = OutputContract.type(String.class);
            var task = new PrimitiveTask<String>("p1", Instant.now(), "desc", agent(),
                    null, null, contract);
            assertThat(task.outputContract()).isSameAs(contract);
        }

        @Test
        void primitiveTask_backwardCompatible_nullContract() {
            var task = new PrimitiveTask<String>("p1", Instant.now(), "desc", agent(), null, null);
            assertThat(task.outputContract()).isNull();
        }

        @Test
        void plannedTask_carriesContract() {
            var contract = OutputContract.nonNull();
            var task = new PlannedTask<String>("t1", Instant.now(), "desc", agent(), null, contract);
            assertThat(task.outputContract()).isSameAs(contract);
        }

        @Test
        void plannedTask_backwardCompatible_nullContract() {
            var task = new PlannedTask<String>("t1", Instant.now(), "desc", agent(), null);
            assertThat(task.outputContract()).isNull();
        }
    }
}
