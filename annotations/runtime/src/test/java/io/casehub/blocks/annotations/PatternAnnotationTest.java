package io.casehub.blocks.annotations;

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PatternAnnotationTest {

    @Test
    void supervisor_has_expected_attributes() {
        assertThat(Supervisor.class.isAnnotation()).isTrue();
        assertThat(Supervisor.class.getAnnotation(Retention.class).value())
                .isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(Supervisor.class.getAnnotation(Target.class).value())
                .containsExactly(ElementType.METHOD);

        assertThat(attributeNames(Supervisor.class)).containsExactlyInAnyOrder(
                "name", "maxIterations", "routing", "decomposition", "aggregation");
    }

    @Test
    void debate_has_expected_attributes() {
        assertThat(attributeNames(Debate.class)).containsExactlyInAnyOrder(
                "name", "maxRounds");
    }

    @Test
    void voting_has_expected_attributes() {
        assertThat(attributeNames(Voting.class)).containsExactlyInAnyOrder(
                "name", "strategy");
    }

    @Test
    void htn_has_expected_attributes() {
        assertThat(attributeNames(Htn.class)).containsExactlyInAnyOrder(
                "name", "decomposition");
    }

    @Test
    void sequence_has_expected_attributes() {
        assertThat(attributeNames(Sequence.class)).containsExactlyInAnyOrder("name");
    }

    @Test
    void parallel_has_expected_attributes() {
        assertThat(attributeNames(Parallel.class)).containsExactlyInAnyOrder("name");
    }

    @Test
    void loop_has_expected_attributes() {
        assertThat(attributeNames(Loop.class)).containsExactlyInAnyOrder(
                "name", "maxIterations");
    }

    @Test
    void conditional_has_expected_attributes() {
        assertThat(attributeNames(Conditional.class)).containsExactlyInAnyOrder("name");
    }

    @Test
    void all_pattern_annotations_are_runtime_retained_method_level() {
        for (var ann : new Class<?>[]{ Supervisor.class, Sequence.class, Parallel.class,
                Loop.class, Conditional.class, Debate.class, Voting.class, Htn.class }) {
            assertThat(ann.getAnnotation(Retention.class).value())
                    .as(ann.getSimpleName() + " retention")
                    .isEqualTo(RetentionPolicy.RUNTIME);
            assertThat(ann.getAnnotation(Target.class).value())
                    .as(ann.getSimpleName() + " target")
                    .containsExactly(ElementType.METHOD);
        }
    }

    @Test
    void role_annotations_are_parameter_level() {
        for (var ann : new Class<?>[]{ Agent.class, Debater.class, Voter.class, Judge.class }) {
            assertThat(ann.getAnnotation(Target.class).value())
                    .as(ann.getSimpleName())
                    .containsExactly(ElementType.PARAMETER);
        }
    }

    @Test
    void role_annotations_have_systemPrompt_and_agentId() {
        for (var ann : new Class<?>[]{ Agent.class, Debater.class, Voter.class, Judge.class }) {
            assertThat(attributeNames(ann))
                    .as(ann.getSimpleName())
                    .contains("systemPrompt", "agentId");
        }
    }

    @Test
    void debater_has_role_attribute() {
        assertThat(attributeNames(Debater.class)).contains("role");
    }

    @Test
    void voter_has_role_attribute() {
        assertThat(attributeNames(Voter.class)).contains("role");
    }

    @Test
    void judge_has_no_role_attribute() {
        assertThat(attributeNames(Judge.class)).doesNotContain("role");
    }

    @Test
    void agent_has_name_attribute() {
        assertThat(attributeNames(Agent.class)).contains("name");
    }

    private static Set<String> attributeNames(Class<?> annotation) {
        return Arrays.stream(annotation.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .collect(Collectors.toSet());
    }
}
