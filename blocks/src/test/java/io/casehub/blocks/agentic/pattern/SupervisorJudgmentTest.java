package io.casehub.blocks.agentic.pattern;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.spi.judgment.CallerIdentity;
import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.judgment.JudgmentDecision;
import io.casehub.blocks.agentic.judgment.JudgmentPhase;
import io.casehub.blocks.agentic.model.ExecutionResult;
import io.casehub.blocks.agentic.model.OrchestratedDriver;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SupervisorJudgmentTest {

  @Test
  void supervisorWithJudgment_completesAfterApproval() {
    var agent = AgentRef.external("analyst", ctx ->
        CompletableFuture.completedFuture(AgentResult.success(null, "analysis")));

    JudgmentPhase<Object> judgment = ctx ->
        new JudgmentDecision.Approved("approved", List.of(),
            CallerIdentity.of("reviewer", "llm"));

    var model = new SupervisorBuilder<>()
        .agents(agent)
        .task("supervised-analysis")
        .judgment(judgment)
        .build();

    assertThat(model.judgment()).isNotNull();

    var result = new OrchestratedDriver<>()
        .execute(model, "context").await().indefinitely();
    assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
  }

  @Test
  void supervisorWithRejection_reIteratesThenApproves() {
    var attempts = new AtomicInteger(0);
    var agent = AgentRef.external("analyst", ctx -> {
      attempts.incrementAndGet();
      return CompletableFuture.completedFuture(
          AgentResult.success(null, "attempt-" + attempts.get()));
    });

    var judgmentCalls = new AtomicInteger(0);
    JudgmentPhase<Object> judgment = ctx -> {
      if (judgmentCalls.incrementAndGet() == 1) {
        return new JudgmentDecision.Rejected("incomplete", List.of(),
            CallerIdentity.of("reviewer", "llm"));
      }
      return new JudgmentDecision.Approved("good", List.of(),
          CallerIdentity.of("reviewer", "llm"));
    };

    var model = new SupervisorBuilder<>()
        .agents(agent)
        .task("supervised-analysis")
        .terminate(new io.casehub.blocks.agentic.termination.MaxIterationsTermination<>(2))
        .judgment(judgment)
        .build();

    var result = new OrchestratedDriver<>()
        .execute(model, "context").await().indefinitely();
    assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
    assertThat(attempts.get()).isEqualTo(2);
  }

  @Test
  void debateWithJudgment_completesAfterApproval() {
    var debater1 = AgentRef.external("d1", ctx ->
        CompletableFuture.completedFuture(AgentResult.success(null, "position-A")));
    var debater2 = AgentRef.external("d2", ctx ->
        CompletableFuture.completedFuture(AgentResult.success(null, "position-B")));

    JudgmentPhase<Object> judgment = ctx ->
        new JudgmentDecision.Approved("consensus reached", List.of(),
            CallerIdentity.of("judge", "llm"));

    var model = new DebateBuilder<>()
        .debaters(debater1, debater2)
        .maxRounds(1)
        .judgment(judgment)
        .build();

    assertThat(model.judgment()).isNotNull();

    var result = new OrchestratedDriver<>()
        .execute(model, "context").await().indefinitely();
    assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
  }
}
