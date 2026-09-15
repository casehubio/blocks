/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.engine.agentic.judgment;

import io.casehub.api.spi.judgment.CallerConfig;
import io.casehub.api.spi.judgment.CallerIdentity;
import io.casehub.api.spi.judgment.Evidence;
import io.casehub.api.spi.judgment.EvidenceType;
import io.casehub.api.model.ai.ChatModelProvider;
import io.casehub.blocks.agentic.aggregation.AggregationResult;
import io.casehub.blocks.agentic.judgment.JudgmentContext;
import io.casehub.blocks.agentic.judgment.JudgmentDecision;
import io.casehub.blocks.agentic.judgment.JudgmentPhase;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

public class LlmJudgmentPhase<T> implements JudgmentPhase<T> {

  private final ChatModelProvider chatModelProvider;
  private final PatternJudgmentConfig config;
  private final io.casehub.api.spi.judgment.JudgmentVerifier verifier;

  public LlmJudgmentPhase(
      ChatModelProvider chatModelProvider,
      PatternJudgmentConfig config,
      io.casehub.api.spi.judgment.JudgmentVerifier verifier) {
    this.chatModelProvider = chatModelProvider;
    this.config = config;
    this.verifier = verifier;
  }

  public LlmJudgmentPhase(ChatModelProvider chatModelProvider, PatternJudgmentConfig config) {
    this(chatModelProvider, config, null);
  }

  @Override
  public JudgmentDecision evaluate(JudgmentContext<T> context) {
    try {
      return doEvaluate(context);
    } catch (Exception e) {
      return new JudgmentDecision.Escalated(
          "LLM judgment failed: " + e.getMessage(), buildCallerIdentity());
    }
  }

  private JudgmentDecision doEvaluate(JudgmentContext<T> context) {
    var chatModel = chatModelProvider.get();
    String prompt = buildPrompt(context);

    var messages = new ArrayList<dev.langchain4j.data.message.ChatMessage>();
    if (config.callerConfig() instanceof CallerConfig.Llm llm && llm.systemPrompt() != null) {
      messages.add(dev.langchain4j.data.message.SystemMessage.from(llm.systemPrompt()));
    } else {
      messages.add(
          dev.langchain4j.data.message.SystemMessage.from(
              "You are a judgment evaluator. Review the work output and respond with "
                  + "APPROVE if the output is satisfactory, or REJECT followed by specific "
                  + "feedback if it needs improvement. Only respond with one of these two "
                  + "formats."));
    }
    messages.add(dev.langchain4j.data.message.UserMessage.from(prompt));

    var chatResponse = chatModel.chat(messages);
    String llmOutput = chatResponse.aiMessage().text().trim();

    var evidence = List.of(Evidence.of("llm-reasoning", EvidenceType.REASONING, llmOutput));
    var callerIdentity = buildCallerIdentity();

    if (verifier != null) {
      var verificationCtx = new io.casehub.api.spi.judgment.VerificationContext(
          null, "", "", context.executionContext() instanceof io.casehub.api.model.JudgmentTarget jt ? jt : null,
          java.util.Map.of(), null, llmOutput, evidence, callerIdentity, null);
      var verificationResult = verifier.verify(verificationCtx);
      if (verificationResult instanceof io.casehub.api.spi.judgment.VerificationResult.Rejected rejected) {
        return new JudgmentDecision.Rejected(rejected.reason(), evidence, callerIdentity);
      }
      if (verificationResult instanceof io.casehub.api.spi.judgment.VerificationResult.InsufficientEvidence ie) {
        return new JudgmentDecision.Rejected(ie.feedback(), evidence, callerIdentity);
      }
    }

    if (llmOutput.toUpperCase().startsWith("APPROVE")
        || llmOutput.toUpperCase().startsWith("ACCEPT")) {
      return new JudgmentDecision.Approved(llmOutput, evidence, callerIdentity);
    }

    String feedback =
        llmOutput.toUpperCase().startsWith("REJECT")
            ? llmOutput.substring(Math.min(7, llmOutput.length())).trim()
            : llmOutput;
    return new JudgmentDecision.Rejected(
        feedback.isEmpty() ? "Judgment rejected the output" : feedback, evidence, callerIdentity);
  }

  private String buildPrompt(JudgmentContext<T> context) {
    var sb = new StringBuilder();
    if (config.prompt() != null) {
      sb.append(config.prompt()).append("\n\n");
    }
    if (context.aggregationResult() instanceof AggregationResult.Resolved resolved) {
      sb.append("Work output:\n").append(resolved.value()).append("\n\n");
    }
    if (context.previousFeedback() != null) {
      sb.append("Previous feedback (address this):\n")
          .append(context.previousFeedback())
          .append("\n\n");
    }
    sb.append("Iteration: ").append(context.iteration() + 1).append("\n");
    return sb.toString();
  }

  private CallerIdentity buildCallerIdentity() {
    String modelName = "llm";
    if (config.callerConfig() instanceof CallerConfig.Llm llm && llm.modelName() != null) {
      modelName = llm.modelName();
    }
    return CallerIdentity.of(modelName, "llm");
  }
}
