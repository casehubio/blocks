package io.casehub.blocks.annotations.deployment;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.model.PatternType;
import io.casehub.blocks.annotations.Agent;
import io.casehub.blocks.annotations.Debate;
import io.casehub.blocks.annotations.Debater;
import io.casehub.blocks.annotations.Judge;
import io.casehub.blocks.annotations.Sequence;
import io.casehub.blocks.annotations.Supervisor;
import io.casehub.blocks.annotations.Voter;
import io.casehub.blocks.annotations.Voting;
import io.casehub.blocks.annotations.runtime.PatternDescriptor;
import io.casehub.engine.annotations.Worker;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatternValidationTest {

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

    interface DualPatternBad {
        @Debate(maxRounds = 3)
        @Supervisor
        String review(
                @Debater(role = "a", systemPrompt = "p") AgentRef a);
    }

    interface MissingRoleBad {
        @Debate(maxRounds = 3)
        String review(AgentRef bare);
    }

    interface WorkerAndPatternBad {
        @Worker(capability = "review")
        @Debate(maxRounds = 3)
        String review(
                @Debater(role = "a", systemPrompt = "p") AgentRef a);
    }

    interface BothPromptAndIdBad {
        @Debate(maxRounds = 3)
        String review(
                @Debater(role = "a", systemPrompt = "p", agentId = "id") AgentRef a);
    }

    interface NeitherPromptNorIdBad {
        @Debate(maxRounds = 3)
        String review(
                @Debater(role = "a") AgentRef a);
    }

    interface ValidDebate {
        @Debate(maxRounds = 5)
        String review(
                @Debater(role = "critic", systemPrompt = "Challenge") AgentRef critic,
                @Debater(role = "advocate", systemPrompt = "Defend") AgentRef advocate,
                @Judge(systemPrompt = "Judge") AgentRef judge,
                String document);
    }

    interface ValidSupervisor {
        @Supervisor(maxIterations = 15)
        String triage(
                @Agent(name = "triage", systemPrompt = "Triage the incident") AgentRef triageAgent,
                @Agent(name = "containment", systemPrompt = "Recommend containment") AgentRef containmentAgent,
                String incidentReport);
    }

    interface ValidSequence {
        @Sequence(name = "pipeline")
        String process(
                @Agent(name = "step1", systemPrompt = "First step") AgentRef step1,
                @Agent(name = "step2", systemPrompt = "Second step") AgentRef step2);
    }

    interface ValidVoting {
        @Voting
        String allocate(
                @Voter(role = "fire-chief", systemPrompt = "Prioritise containment") AgentRef fireChief,
                @Voter(role = "medic-lead", systemPrompt = "Prioritise evacuation") AgentRef medicLead);
    }

    // --- Rejection tests ---

    @Test
    void rejects_dual_pattern_annotations() throws IOException {
        Index index = indexClasses(DualPatternBad.class);
        var step = new PatternAnnotationStep();

        assertThatThrownBy(() -> step.scan(index))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("multiple pattern annotations");
    }

    @Test
    void rejects_agentref_without_role_annotation() throws IOException {
        Index index = indexClasses(MissingRoleBad.class);
        var step = new PatternAnnotationStep();

        assertThatThrownBy(() -> step.scan(index))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no role annotation");
    }

    @Test
    void rejects_worker_and_pattern_on_same_method() throws IOException {
        Index index = indexClasses(WorkerAndPatternBad.class);
        var step = new PatternAnnotationStep();

        assertThatThrownBy(() -> step.scan(index))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@Worker");
    }

    @Test
    void rejects_both_systemPrompt_and_agentId() throws IOException {
        Index index = indexClasses(BothPromptAndIdBad.class);
        var step = new PatternAnnotationStep();

        assertThatThrownBy(() -> step.scan(index))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("both systemPrompt and agentId");
    }

    @Test
    void rejects_neither_systemPrompt_nor_agentId() throws IOException {
        Index index = indexClasses(NeitherPromptNorIdBad.class);
        var step = new PatternAnnotationStep();

        assertThatThrownBy(() -> step.scan(index))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must specify systemPrompt or agentId");
    }

    // --- Acceptance tests ---

    @Test
    void accepts_valid_debate() throws IOException {
        Index index = indexClasses(ValidDebate.class);
        var step = new PatternAnnotationStep();
        var descriptors = step.scan(index);

        assertThat(descriptors).hasSize(1);
        var desc = descriptors.get(0);
        assertThat(desc.patternType()).isEqualTo(PatternType.DEBATE);
        assertThat(desc.beanName()).isEqualTo("review");
        assertThat(desc.attributes().get("maxRounds")).isEqualTo(5);
        assertThat(desc.participants()).hasSize(3);
    }

    @Test
    void debate_participants_preserve_role_and_judge_flag() throws IOException {
        Index index = indexClasses(ValidDebate.class);
        var step = new PatternAnnotationStep();
        var desc = step.scan(index).get(0);

        var critic = desc.participants().stream()
                .filter(p -> p.label().equals("critic")).findFirst().orElseThrow();
        assertThat(critic.role()).isEqualTo("critic");
        assertThat(critic.systemPrompt()).isEqualTo("Challenge");
        assertThat(critic.isJudge()).isFalse();

        var judge = desc.participants().stream()
                .filter(p -> p.isJudge()).findFirst().orElseThrow();
        assertThat(judge.systemPrompt()).isEqualTo("Judge");
        assertThat(judge.isJudge()).isTrue();
    }

    @Test
    void accepts_valid_supervisor() throws IOException {
        Index index = indexClasses(ValidSupervisor.class);
        var step = new PatternAnnotationStep();
        var descriptors = step.scan(index);

        assertThat(descriptors).hasSize(1);
        var desc = descriptors.get(0);
        assertThat(desc.patternType()).isEqualTo(PatternType.SUPERVISOR);
        assertThat(desc.beanName()).isEqualTo("triage");
        assertThat(desc.attributes().get("maxIterations")).isEqualTo(15);
        assertThat(desc.participants()).hasSize(2);
        assertThat(desc.participants().get(0).label()).isEqualTo("triage");
    }

    @Test
    void supervisor_uses_method_name_when_name_attribute_empty() throws IOException {
        Index index = indexClasses(ValidSupervisor.class);
        var step = new PatternAnnotationStep();
        var desc = step.scan(index).get(0);

        assertThat(desc.beanName()).isEqualTo("triage");
    }

    @Test
    void accepts_valid_sequence() throws IOException {
        Index index = indexClasses(ValidSequence.class);
        var step = new PatternAnnotationStep();
        var descriptors = step.scan(index);

        assertThat(descriptors).hasSize(1);
        var desc = descriptors.get(0);
        assertThat(desc.patternType()).isEqualTo(PatternType.SEQUENCE);
        assertThat(desc.beanName()).isEqualTo("pipeline");
        assertThat(desc.participants()).hasSize(2);
    }

    @Test
    void accepts_valid_voting() throws IOException {
        Index index = indexClasses(ValidVoting.class);
        var step = new PatternAnnotationStep();
        var descriptors = step.scan(index);

        assertThat(descriptors).hasSize(1);
        var desc = descriptors.get(0);
        assertThat(desc.patternType()).isEqualTo(PatternType.VOTING);
        assertThat(desc.participants()).hasSize(2);
    }

    @Test
    void non_agentref_parameters_are_ignored() throws IOException {
        Index index = indexClasses(ValidDebate.class);
        var step = new PatternAnnotationStep();
        var desc = step.scan(index).get(0);

        assertThat(desc.participants()).hasSize(3);
    }

    @Test
    void annotation_name_attribute_overrides_method_name() throws IOException {
        Index index = indexClasses(ValidSequence.class);
        var step = new PatternAnnotationStep();
        var desc = step.scan(index).get(0);

        assertThat(desc.beanName()).isEqualTo("pipeline");
    }
}
