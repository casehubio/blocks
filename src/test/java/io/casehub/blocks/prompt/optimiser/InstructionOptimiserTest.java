package io.casehub.blocks.prompt.optimiser;

import io.casehub.blocks.prompt.OptimisationDataset;
import io.casehub.blocks.prompt.OptimiserConfig;
import io.casehub.blocks.prompt.PromptSignature;
import io.casehub.blocks.prompt.VariantOutcome;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstructionOptimiserTest {

    @Mock AgentProvider agentProvider;

    private VariantOutcome outcome(String result) {
        return new VariantOutcome("v1", "sig", result, 0.8, null, Instant.now());
    }

    private OptimisationDataset dataset(List<VariantOutcome> outcomes) {
        return new OptimisationDataset(outcomes, List.of());
    }

    private PromptSignature signature() {
        return new PromptSignature("test", "Test", "You are a router.", Object.class, Object.class);
    }

    private void mockLlmResponse(String response) {
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().item(new AgentEvent.TextDelta(response)));
    }

    @Test
    void idIsInstruction() {
        assertThat(new InstructionOptimiser(agentProvider).id()).isEqualTo("instruction");
    }

    @Test
    void producesInstructionDeltaFromLlmResponse() {
        mockLlmResponse("When routing triage tasks, prefer agents with medical domain expertise.");
        var optimiser = new InstructionOptimiser(agentProvider);
        var outcomes = List.of(outcome("SUCCESS"), outcome("FAILURE"), outcome("SUCCESS"));
        var result = optimiser.optimise(signature(), null, dataset(outcomes),
                OptimiserConfig.defaults()).toCompletableFuture().join();
        assertThat(result.instructionDelta())
                .isEqualTo("When routing triage tasks, prefer agents with medical domain expertise.");
        assertThat(result.examples()).isEmpty();
    }

    @Test
    void sendsOutcomePatternsInPrompt() {
        mockLlmResponse("Refined instructions.");
        var optimiser = new InstructionOptimiser(agentProvider);
        var outcomes = List.of(outcome("SUCCESS"), outcome("FAILURE"));
        optimiser.optimise(signature(), null, dataset(outcomes),
                OptimiserConfig.defaults()).toCompletableFuture().join();
        var captor = ArgumentCaptor.forClass(AgentSessionConfig.class);
        verify(agentProvider).invoke(captor.capture());
        assertThat(captor.getValue().userPrompt()).contains("SUCCESS");
        assertThat(captor.getValue().userPrompt()).contains("FAILURE");
    }

    @Test
    void includesBaseSystemPromptInMetaPrompt() {
        mockLlmResponse("delta");
        var optimiser = new InstructionOptimiser(agentProvider);
        optimiser.optimise(signature(), null, dataset(List.of(outcome("SUCCESS"))),
                OptimiserConfig.defaults()).toCompletableFuture().join();
        var captor = ArgumentCaptor.forClass(AgentSessionConfig.class);
        verify(agentProvider).invoke(captor.capture());
        assertThat(captor.getValue().userPrompt()).contains("You are a router.");
    }

    @Test
    void returnsNullDeltaOnEmptyOutcomes() {
        var optimiser = new InstructionOptimiser(agentProvider);
        var result = optimiser.optimise(signature(), null, dataset(List.of()),
                OptimiserConfig.defaults()).toCompletableFuture().join();
        assertThat(result.instructionDelta()).isNull();
    }

    @Test
    void returnsNullDeltaOnLlmFailure() {
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().failure(new RuntimeException("LLM unavailable")));
        var optimiser = new InstructionOptimiser(agentProvider);
        var result = optimiser.optimise(signature(), null, dataset(List.of(outcome("SUCCESS"))),
                OptimiserConfig.defaults()).toCompletableFuture().join();
        assertThat(result.instructionDelta()).isNull();
    }

    @Test
    void treatsBlankResponseAsNullDelta() {
        mockLlmResponse("   ");
        var optimiser = new InstructionOptimiser(agentProvider);
        var result = optimiser.optimise(signature(), null, dataset(List.of(outcome("SUCCESS"))),
                OptimiserConfig.defaults()).toCompletableFuture().join();
        assertThat(result.instructionDelta()).isNull();
    }
}
