package io.casehub.engine.agentic.judgment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.casehub.api.model.ai.ChatModelProvider;
import io.casehub.api.spi.judgment.CallerConfig;
import io.casehub.api.spi.judgment.JudgmentVerifier;
import io.casehub.api.spi.judgment.VerificationContext;
import io.casehub.api.spi.judgment.VerificationResult;
import io.casehub.blocks.agentic.aggregation.AggregationResult;
import io.casehub.blocks.agentic.judgment.JudgmentContext;
import io.casehub.blocks.agentic.judgment.JudgmentDecision;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LlmJudgmentPhaseTest {

  private ChatModel chatModel;
  private ChatModelProvider provider;

  @BeforeEach
  void setUp() {
    chatModel = mock(ChatModel.class);
    provider = mock(ChatModelProvider.class);
    when(provider.get()).thenReturn(chatModel);
  }

  @Test
  void approved_whenLlmReturnsApproveResponse() {
    mockLlmResponse("APPROVE: The analysis is thorough and well-structured.");
    var config = new PatternJudgmentConfig(
        "Review the output",
        new CallerConfig.Llm(null, "test-model", null),
        null, List.of(), null, false);
    var phase = new LlmJudgmentPhase<>(provider, config, null);

    var ctx = new JudgmentContext<>(
        (Object) "state", List.of(), new AggregationResult.Resolved("result"), 0, null);
    var decision = phase.evaluate(ctx);

    assertThat(decision).isInstanceOf(JudgmentDecision.Approved.class);
  }

  @Test
  void rejected_whenLlmReturnsRejectResponse() {
    mockLlmResponse("REJECT: Missing error handling analysis.");
    var config = new PatternJudgmentConfig(
        "Review the output",
        new CallerConfig.Llm(null, "test-model", null),
        null, List.of(), null, false);
    var phase = new LlmJudgmentPhase<>(provider, config, null);

    var ctx = new JudgmentContext<>(
        (Object) "state", List.of(), new AggregationResult.Resolved("result"), 0, null);
    var decision = phase.evaluate(ctx);

    assertThat(decision).isInstanceOf(JudgmentDecision.Rejected.class);
    assertThat(((JudgmentDecision.Rejected) decision).feedback())
        .contains("Missing error handling");
  }

  @Test
  void previousFeedback_includedInPrompt() {
    mockLlmResponse("APPROVE: Now looks complete.");
    var config = new PatternJudgmentConfig(
        "Review the output",
        new CallerConfig.Llm(null, "test-model", null),
        null, List.of(), null, false);
    var phase = new LlmJudgmentPhase<>(provider, config, null);

    var ctx = new JudgmentContext<>(
        (Object) "state", List.of(), new AggregationResult.Resolved("result"), 1,
        "needs more detail");
    var decision = phase.evaluate(ctx);

    assertThat(decision).isInstanceOf(JudgmentDecision.Approved.class);
  }

  @Test
  void verifierRejects_returnsRejected() {
    mockLlmResponse("APPROVE: Looks good.");
    var verifier = mock(JudgmentVerifier.class);
    when(verifier.verify(org.mockito.ArgumentMatchers.any(VerificationContext.class)))
        .thenReturn(new VerificationResult.Rejected("schema mismatch"));

    var config = new PatternJudgmentConfig(
        "Review",
        new CallerConfig.Llm(null, "test-model", null),
        "schema-validation", List.of(), null, false);
    var phase = new LlmJudgmentPhase<>(provider, config, verifier);

    var ctx = new JudgmentContext<>(
        (Object) "state", List.of(), new AggregationResult.Resolved("result"), 0, null);
    var decision = phase.evaluate(ctx);

    assertThat(decision).isInstanceOf(JudgmentDecision.Rejected.class);
    assertThat(((JudgmentDecision.Rejected) decision).feedback()).contains("schema mismatch");
  }

  @Test
  void chatModelFailure_returnsEscalated() {
    when(chatModel.chat(anyList())).thenThrow(new RuntimeException("API error"));
    var config = new PatternJudgmentConfig(
        "Review",
        new CallerConfig.Llm(null, "test-model", null),
        null, List.of(), null, false);
    var phase = new LlmJudgmentPhase<>(provider, config, null);

    var ctx = new JudgmentContext<>(
        (Object) "state", List.of(), new AggregationResult.Resolved("result"), 0, null);
    var decision = phase.evaluate(ctx);

    assertThat(decision).isInstanceOf(JudgmentDecision.Escalated.class);
  }

  private void mockLlmResponse(String text) {
    var aiMessage = AiMessage.from(text);
    var chatResponse = ChatResponse.builder().aiMessage(aiMessage).build();
    when(chatModel.chat(anyList())).thenReturn(chatResponse);
  }
}
