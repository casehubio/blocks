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
package io.casehub.engine.agentic;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.api.model.ai.ChatModelProvider;
import io.casehub.api.spi.judgment.JudgmentVerifier;
import io.casehub.engine.agentic.judgment.LlmEvaluationVerifier;
import io.casehub.engine.agentic.judgment.LlmJudgmentScheduler;
import io.casehub.engine.agentic.judgment.SchemaValidationVerifier;
import io.casehub.engine.common.internal.event.EventBusAddresses;
import io.casehub.engine.common.internal.event.JudgmentCompletedEvent;
import io.casehub.engine.common.spi.EventLogRepository;
import io.casehub.engine.internal.executor.WorkerRuntimeFactory;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

@ApplicationScoped
public class EngineAdapterBeans {

  @Inject Instance<ChatModelProvider> chatModelProviderInstance;
  @Inject Instance<JudgmentVerifier> judgmentVerifierInstance;
  @Inject EventBus eventBus;

  @Produces
  @ApplicationScoped
  public PatternCheckpointStore patternCheckpointStore(
      EventLogRepository eventLogRepository, ObjectMapper objectMapper) {
    return new PatternCheckpointStore(eventLogRepository, objectMapper);
  }

  @Produces
  @ApplicationScoped
  public PatternWorkerFunctionProvider patternWorkerFunctionProvider() {
    return new PatternWorkerFunctionProvider();
  }

  @Produces
  @ApplicationScoped
  public PatternWorkerFunctionHandler patternWorkerFunctionHandler(
      WorkerRuntimeFactory workerRuntimeFactory, PatternCheckpointStore checkpointStore) {
    return new PatternWorkerFunctionHandler(
        workerRuntimeFactory,
        checkpointStore,
        optionalFrom(chatModelProviderInstance),
        listFrom(judgmentVerifierInstance));
  }

  @Produces
  @ApplicationScoped
  public SchemaValidationVerifier schemaValidationVerifier() {
    return new SchemaValidationVerifier();
  }

  @Produces
  @ApplicationScoped
  public LlmEvaluationVerifier llmEvaluationVerifier() {
    return new LlmEvaluationVerifier(optionalFrom(chatModelProviderInstance));
  }

  @Produces
  @ApplicationScoped
  public LlmJudgmentScheduler llmJudgmentScheduler() {
    return new LlmJudgmentScheduler(
        optionalFrom(chatModelProviderInstance),
        event -> eventBus.publish(EventBusAddresses.JUDGMENT_COMPLETED, event));
  }

  private static <T> Optional<T> optionalFrom(Instance<T> instance) {
    return instance.isResolvable() ? Optional.of(instance.get()) : Optional.empty();
  }

  private static <T> List<T> listFrom(Instance<T> instance) {
    return StreamSupport.stream(instance.spliterator(), false).toList();
  }
}
