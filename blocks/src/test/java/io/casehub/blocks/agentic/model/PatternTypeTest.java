package io.casehub.blocks.agentic.model;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.pattern.Patterns;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class PatternTypeTest {

    @Test
    void workflowShapedPatterns() {
        assertThat(PatternType.SEQUENCE.isWorkflowShaped()).isTrue();
        assertThat(PatternType.PARALLEL.isWorkflowShaped()).isTrue();
        assertThat(PatternType.LOOP.isWorkflowShaped()).isTrue();
        assertThat(PatternType.CONDITIONAL.isWorkflowShaped()).isTrue();
    }

    @Test
    void nonWorkflowPatterns() {
        assertThat(PatternType.SUPERVISOR.isWorkflowShaped()).isFalse();
        assertThat(PatternType.DEBATE.isWorkflowShaped()).isFalse();
        assertThat(PatternType.VOTING.isWorkflowShaped()).isFalse();
        assertThat(PatternType.HTN.isWorkflowShaped()).isFalse();
    }

    @Test
    void sequenceBuilderSetsPatternType() {
        var agent = AgentRef.external((Object s) ->
            CompletableFuture.completedFuture(AgentResult.success(null, "x")));
        var model = Patterns.<Object>sequence().agents(agent).build();
        assertThat(model.patternType()).isEqualTo(PatternType.SEQUENCE);
    }

    @Test
    void parallelBuilderSetsPatternType() {
        var agent = AgentRef.external((Object s) ->
            CompletableFuture.completedFuture(AgentResult.success(null, "x")));
        var model = Patterns.<Object>parallel().agents(agent).build();
        assertThat(model.patternType()).isEqualTo(PatternType.PARALLEL);
    }

    @Test
    void loopBuilderSetsPatternType() {
        var agent = AgentRef.external((Object s) ->
            CompletableFuture.completedFuture(AgentResult.success(null, "x")));
        var model = Patterns.<Object>loop().agents(agent).build();
        assertThat(model.patternType()).isEqualTo(PatternType.LOOP);
    }

    @Test
    void conditionalBuilderSetsPatternType() {
        var model = Patterns.<Object>conditional()
            .when(c -> true, AgentRef.external((Object s) ->
                CompletableFuture.completedFuture(AgentResult.success(null, "x"))))
            .build();
        assertThat(model.patternType()).isEqualTo(PatternType.CONDITIONAL);
    }

    @Test
    void supervisorBuilderSetsPatternType() {
        var agent = AgentRef.external((Object s) ->
            CompletableFuture.completedFuture(AgentResult.success(null, "x")));
        var model = Patterns.<Object>supervisor().agents(agent).build();
        assertThat(model.patternType()).isEqualTo(PatternType.SUPERVISOR);
    }

    @Test
    void htnBuilderSetsPatternType() {
        var agent = AgentRef.external((Object s) ->
            CompletableFuture.completedFuture(AgentResult.success(null, "x")));
        var model = Patterns.<Object>htn().agents(agent).build();
        assertThat(model.patternType()).isEqualTo(PatternType.HTN);
    }
}
