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

import io.casehub.api.model.JudgmentTarget;
import io.casehub.api.model.ai.ChatModelProvider;
import io.casehub.api.spi.judgment.CallerConfig;
import io.casehub.api.spi.judgment.EvidenceRequirement;
import io.casehub.engine.common.internal.event.EventBusAddresses;
import io.casehub.engine.common.internal.event.JudgmentCompletedEvent;
import io.casehub.engine.common.spi.JudgmentPayload;
import io.casehub.engine.common.spi.JudgmentRequest;
import io.casehub.engine.common.spi.JudgmentResponse;
import io.casehub.engine.common.spi.JudgmentScheduleRequest;
import io.casehub.engine.common.spi.JudgmentScheduler;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Map;
import org.jboss.logging.Logger;

@ApplicationScoped
public class LlmJudgmentScheduler implements JudgmentScheduler {

  private static final Logger LOG = Logger.getLogger(LlmJudgmentScheduler.class);

  @Inject Instance<ChatModelProvider> chatModelProviderInstance;
  @Inject EventBus eventBus;

  @Override
  @SuppressWarnings("removal")
  public void schedule(JudgmentScheduleRequest request) {
    LOG.debugf("LlmJudgmentScheduler does not handle legacy JudgmentScheduleRequest — skipping");
  }

  @Override
  public void schedule(JudgmentRequest request) {
    if (!(request.payload() instanceof JudgmentPayload.BindingPayload bp)) {
      LOG.debugf("LlmJudgmentScheduler skipping non-binding payload: caseId=%s", request.caseId());
      return;
    }

    if (!(bp.target().routingConfig() instanceof io.casehub.api.model.RoutingConfig)) {
      // Check if the target has an LLM-oriented caller config context
    }

    if (!chatModelProviderInstance.isResolvable()) {
      LOG.warnf(
          "No ChatModelProvider on classpath — cannot schedule LLM judgment for caseId=%s binding=%s",
          request.caseId(), request.bindingName());
      return;
    }

    Thread.startVirtualThread(
        () -> {
          try {
            executeLlmJudgment(request, bp);
          } catch (Exception e) {
            LOG.errorf(
                e,
                "LLM judgment failed for caseId=%s binding=%s",
                request.caseId(),
                request.bindingName());
          }
        });
  }

  private void executeLlmJudgment(JudgmentRequest request, JudgmentPayload.BindingPayload bp) {
    JudgmentTarget target = bp.target();
    ChatModelProvider provider = chatModelProviderInstance.get();

    String prompt = buildPrompt(target, bp);

    LOG.infof(
        "Executing LLM judgment: caseId=%s binding=%s",
        request.caseId(), request.bindingName());

    dev.langchain4j.model.chat.ChatModel chatModel = provider.get();
    var messages = new ArrayList<dev.langchain4j.data.message.ChatMessage>();
    messages.add(
        dev.langchain4j.data.message.SystemMessage.from(
            "You are a judgment evaluator. Review the context and respond with "
                + "APPROVE if satisfactory, or REJECT followed by specific feedback."));
    messages.add(dev.langchain4j.data.message.UserMessage.from(prompt));

    dev.langchain4j.model.chat.response.ChatResponse chatResponse = chatModel.chat(messages);
    String llmOutput = chatResponse.aiMessage().text();

    String modelName = "llm";
    JudgmentResponse response =
        new JudgmentResponse(
            request.caseId(),
            request.bindingName(),
            request.tenancyId(),
            llmOutput,
            Map.of("llm-reasoning", llmOutput),
            modelName,
            "llm");

    eventBus.publish(
        EventBusAddresses.JUDGMENT_COMPLETED,
        new JudgmentCompletedEvent(
            request.caseId(), request.bindingName(), request.tenancyId(), response));

    LOG.infof(
        "LLM judgment response published: caseId=%s binding=%s",
        request.caseId(), request.bindingName());
  }

  private String buildPrompt(JudgmentTarget target, JudgmentPayload.BindingPayload bp) {
    StringBuilder sb = new StringBuilder();

    if (target.prompt() != null) {
      sb.append(target.prompt()).append("\n\n");
    }

    if (bp.inputData() != null && !bp.inputData().isEmpty()) {
      sb.append("Context:\n");
      bp.inputData()
          .forEach((k, v) -> sb.append("  ").append(k).append(": ").append(v).append("\n"));
      sb.append("\n");
    }

    if (!target.evidenceRequirements().isEmpty()) {
      sb.append("Required evidence:\n");
      for (String req : target.evidenceRequirements()) {
        sb.append("  - ").append(req).append("\n");
      }
    }

    return sb.toString();
  }
}
