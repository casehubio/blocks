package io.casehub.blocks.annotations.deployment;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.model.PatternType;
import io.casehub.blocks.agentic.routing.FirstMatchRouting;
import io.casehub.blocks.agentic.routing.RoundRobinRouting;
import io.casehub.blocks.agentic.routing.SequentialRouting;
import io.casehub.blocks.agentic.termination.JudgeConvergence;
import io.casehub.blocks.agentic.termination.MaxIterationsTermination;
import io.casehub.blocks.annotations.runtime.BlocksAnnotationsRecorder;
import io.casehub.blocks.annotations.runtime.PatternDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecorderWiringTest {

    @Test
    void recorder_builds_debate_with_judge_uses_JudgeConvergence() {
        var recorder = new BlocksAnnotationsRecorder();
        var attrs = Map.<String, Object>of("maxRounds", 3);
        var participants = List.of(
                new PatternDescriptor.AgentParticipant("critic", "critic", "Challenge", "", false),
                new PatternDescriptor.AgentParticipant("advocate", "advocate", "Defend", "", false),
                new PatternDescriptor.AgentParticipant("judge", "judge", "Judge", "", true)
        );
        var desc = new PatternDescriptor(PatternType.DEBATE, attrs, participants, "review");

        var model = recorder.createExecutionModel(desc).get();

        assertThat(model).isNotNull();
        assertThat(model.patternType()).isEqualTo(PatternType.DEBATE);
        assertThat(model.task()).isEqualTo("review");
        assertThat(model.candidateSupplier().get()).hasSize(3);
        assertThat(model.termination()).isInstanceOf(JudgeConvergence.class);
        assertThat(model.routing()).isInstanceOf(RoundRobinRouting.class);
    }

    @Test
    void recorder_builds_debate_without_judge_uses_maxIterations() {
        var recorder = new BlocksAnnotationsRecorder();
        var attrs = Map.<String, Object>of("maxRounds", 7);
        var participants = List.of(
                new PatternDescriptor.AgentParticipant("critic", "critic", "Challenge", "", false),
                new PatternDescriptor.AgentParticipant("advocate", "advocate", "Defend", "", false)
        );
        var desc = new PatternDescriptor(PatternType.DEBATE, attrs, participants, "review");

        var model = recorder.createExecutionModel(desc).get();

        assertThat(model.termination()).isInstanceOf(MaxIterationsTermination.class);
    }

    @Test
    void recorder_builds_supervisor_with_defaults() {
        var recorder = new BlocksAnnotationsRecorder();
        var attrs = Map.<String, Object>of("maxIterations", 10);
        var participants = List.of(
                new PatternDescriptor.AgentParticipant("triage", "triage", "Triage the incident", "", false)
        );
        var desc = new PatternDescriptor(PatternType.SUPERVISOR, attrs, participants, "triage");

        var model = recorder.createExecutionModel(desc).get();

        assertThat(model.patternType()).isEqualTo(PatternType.SUPERVISOR);
        assertThat(model.task()).isEqualTo("triage");
        assertThat(model.routing()).isInstanceOf(FirstMatchRouting.class);
        assertThat(model.candidateSupplier().get()).hasSize(1);
    }

    @Test
    void recorder_builds_sequence_with_routing_and_termination() {
        var recorder = new BlocksAnnotationsRecorder();
        var participants = List.of(
                new PatternDescriptor.AgentParticipant("step1", "step1", "First step", "", false),
                new PatternDescriptor.AgentParticipant("step2", "step2", "Second step", "", false)
        );
        var desc = new PatternDescriptor(PatternType.SEQUENCE, Map.of(), participants, "pipeline");

        var model = recorder.createExecutionModel(desc).get();

        assertThat(model.patternType()).isEqualTo(PatternType.SEQUENCE);
        assertThat(model.routing()).isInstanceOf(SequentialRouting.class);
        assertThat(model.termination()).isNotNull();
        assertThat(model.candidateSupplier().get()).hasSize(2);
    }

    @Test
    void recorder_builds_loop_with_maxIterations() {
        var recorder = new BlocksAnnotationsRecorder();
        var attrs = Map.<String, Object>of("maxIterations", 20);
        var participants = List.of(
                new PatternDescriptor.AgentParticipant("worker", "worker", "Do work", "", false)
        );
        var desc = new PatternDescriptor(PatternType.LOOP, attrs, participants, "retry");

        var model = recorder.createExecutionModel(desc).get();

        assertThat(model.patternType()).isEqualTo(PatternType.LOOP);
        assertThat(model.termination()).isInstanceOf(MaxIterationsTermination.class);
    }

    @Test
    void recorder_builds_parallel() {
        var recorder = new BlocksAnnotationsRecorder();
        var participants = List.of(
                new PatternDescriptor.AgentParticipant("a", "a", "Agent A", "", false),
                new PatternDescriptor.AgentParticipant("b", "b", "Agent B", "", false)
        );
        var desc = new PatternDescriptor(PatternType.PARALLEL, Map.of(), participants, "fanout");

        var model = recorder.createExecutionModel(desc).get();

        assertThat(model.patternType()).isEqualTo(PatternType.PARALLEL);
        assertThat(model.candidateSupplier().get()).hasSize(2);
    }

    @Test
    void recorder_builds_voting() {
        var recorder = new BlocksAnnotationsRecorder();
        var participants = List.of(
                new PatternDescriptor.AgentParticipant("voter1", "voter1", "Vote A", "", false),
                new PatternDescriptor.AgentParticipant("voter2", "voter2", "Vote B", "", false)
        );
        var desc = new PatternDescriptor(PatternType.VOTING, Map.of(), participants, "consensus");

        var model = recorder.createExecutionModel(desc).get();

        assertThat(model.patternType()).isEqualTo(PatternType.VOTING);
        assertThat(model.candidateSupplier().get()).hasSize(2);
    }

    @Test
    void recorder_builds_htn() {
        var recorder = new BlocksAnnotationsRecorder();
        var participants = List.of(
                new PatternDescriptor.AgentParticipant("planner", "planner", "Plan tasks", "", false)
        );
        var desc = new PatternDescriptor(PatternType.HTN, Map.of(), participants, "planner");

        var model = recorder.createExecutionModel(desc).get();

        assertThat(model.patternType()).isEqualTo(PatternType.HTN);
    }

    @Test
    void recorder_builds_conditional() {
        var recorder = new BlocksAnnotationsRecorder();
        var participants = List.of(
                new PatternDescriptor.AgentParticipant("branch", "branch", "Handle branch", "", false)
        );
        var desc = new PatternDescriptor(PatternType.CONDITIONAL, Map.of(), participants, "router");

        var model = recorder.createExecutionModel(desc).get();

        assertThat(model.patternType()).isEqualTo(PatternType.CONDITIONAL);
        assertThat(model.candidateSupplier().get()).hasSize(1);
    }

    @Test
    void recorder_creates_ExternalAgent_for_inline_systemPrompt() {
        var recorder = new BlocksAnnotationsRecorder();
        var participants = List.of(
                new PatternDescriptor.AgentParticipant("triage", "triage", "Triage the incident", "", false)
        );
        var desc = new PatternDescriptor(PatternType.SUPERVISOR, Map.<String, Object>of("maxIterations", 10), participants, "triage");

        var model = recorder.createExecutionModel(desc).get();
        var candidates = model.candidateSupplier().get();

        assertThat(candidates.get(0).ref()).isInstanceOf(AgentRef.ExternalAgent.class);
        assertThat(candidates.get(0).ref().name()).isEqualTo("triage");
    }

    @Test
    void recorder_sets_task_to_beanName() {
        var recorder = new BlocksAnnotationsRecorder();
        var desc = new PatternDescriptor(PatternType.SUPERVISOR, Map.<String, Object>of("maxIterations", 5),
                List.of(new PatternDescriptor.AgentParticipant("a", "a", "prompt", "", false)), "myCustomName");

        var model = recorder.createExecutionModel(desc).get();

        assertThat(model.task()).isEqualTo("myCustomName");
    }
}
