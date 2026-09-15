package io.casehub.engine.agentic.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.api.model.ai.ChatModelProvider;
import io.casehub.api.spi.judgment.JudgmentVerifier;
import io.casehub.engine.agentic.PatternCheckpointStore;
import io.casehub.engine.agentic.PatternWorkerFunctionHandler;
import io.casehub.engine.agentic.PatternWorkerFunctionProvider;
import io.casehub.engine.agentic.judgment.LlmEvaluationVerifier;
import io.casehub.engine.agentic.judgment.LlmJudgmentScheduler;
import io.casehub.engine.agentic.judgment.SchemaValidationVerifier;
import io.casehub.engine.common.spi.EventLogRepository;
import io.casehub.engine.internal.executor.WorkerRuntimeFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Optional;

@AutoConfiguration
@ConditionalOnClass(PatternCheckpointStore.class)
public class EngineAdapterAutoConfiguration {

    @Bean
    public PatternCheckpointStore patternCheckpointStore(
            EventLogRepository eventLogRepository, ObjectMapper objectMapper) {
        return new PatternCheckpointStore(eventLogRepository, objectMapper);
    }

    @Bean
    public PatternWorkerFunctionProvider patternWorkerFunctionProvider() {
        return new PatternWorkerFunctionProvider();
    }

    @Bean
    public PatternWorkerFunctionHandler patternWorkerFunctionHandler(
            WorkerRuntimeFactory workerRuntimeFactory,
            PatternCheckpointStore checkpointStore,
            Optional<ChatModelProvider> chatModelProvider,
            List<JudgmentVerifier> judgmentVerifiers) {
        return new PatternWorkerFunctionHandler(
                workerRuntimeFactory, checkpointStore,
                chatModelProvider, judgmentVerifiers);
    }

    @Bean
    public SchemaValidationVerifier schemaValidationVerifier() {
        return new SchemaValidationVerifier();
    }

    @Bean
    public LlmEvaluationVerifier llmEvaluationVerifier(
            Optional<ChatModelProvider> chatModelProvider) {
        return new LlmEvaluationVerifier(chatModelProvider);
    }

    @Bean
    public LlmJudgmentScheduler llmJudgmentScheduler(
            Optional<ChatModelProvider> chatModelProvider,
            ApplicationEventPublisher publisher) {
        return new LlmJudgmentScheduler(
                chatModelProvider,
                event -> publisher.publishEvent(event));
    }
}
