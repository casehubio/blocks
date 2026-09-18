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
package io.casehub.blocks.agent;

import io.casehub.blocks.agent.StructuredAgentInvoker.InvocationMetadata;
import io.casehub.blocks.agent.StructuredAgentInvoker.InvocationResult;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StructuredAgentInvokerTest {

    private final AgentProvider provider = mock(AgentProvider.class);

    // --- invokeText ---

    @Test
    void invokeTextCollectsTextDeltas() {
        when(provider.invoke(any())).thenReturn(Multi.createFrom().items(
                new AgentEvent.TextDelta("Hello"),
                new AgentEvent.TextDelta(" world"),
                new AgentEvent.InvocationComplete(10, 5, 0, 0, 0, 0.001, 200L, 150L, "s1", 1, false)
        ));

        var result = StructuredAgentInvoker.invokeText(provider,
                AgentSessionConfig.of("system", "user"));

        assertThat(result).isInstanceOf(InvocationResult.Success.class);
        var success = (InvocationResult.Success<String>) result;
        assertThat(success.value()).isEqualTo("Hello world");
        assertThat(success.metadata().inputTokens()).isEqualTo(10);
        assertThat(success.metadata().outputTokens()).isEqualTo(5);
        assertThat(success.metadata().durationMs()).isEqualTo(200L);
    }

    @Test
    void invokeTextReturnsAgentErrorOnBlankResponse() {
        when(provider.invoke(any())).thenReturn(Multi.createFrom().items(
                new AgentEvent.InvocationComplete(0, 0, 0, 0, 0, null, 100L, 80L, "s1", 1, false)
        ));

        var result = StructuredAgentInvoker.invokeText(provider,
                AgentSessionConfig.of("system", "user"));

        assertThat(result).isInstanceOf(InvocationResult.AgentError.class);
    }

    @Test
    void invokeTextReturnsAgentErrorOnException() {
        when(provider.invoke(any())).thenReturn(
                Multi.createFrom().failure(new RuntimeException("connection refused")));

        var result = StructuredAgentInvoker.invokeText(provider,
                AgentSessionConfig.of("system", "user"));

        assertThat(result).isInstanceOf(InvocationResult.AgentError.class);
        var error = (InvocationResult.AgentError<String>) result;
        assertThat(error.reason()).contains("connection refused");
    }

    @Test
    void invokeTextIgnoresThinkingDeltas() {
        when(provider.invoke(any())).thenReturn(Multi.createFrom().items(
                new AgentEvent.ThinkingDelta("internal reasoning"),
                new AgentEvent.TextDelta("visible output"),
                new AgentEvent.InvocationComplete(10, 5, 3, 0, 0, null, 200L, 150L, "s1", 1, false)
        ));

        var result = StructuredAgentInvoker.invokeText(provider,
                AgentSessionConfig.of("system", "user"));

        assertThat(result).isInstanceOf(InvocationResult.Success.class);
        assertThat(((InvocationResult.Success<String>) result).value()).isEqualTo("visible output");
    }

    @Test
    void invokeTextHandlesMissingInvocationComplete() {
        when(provider.invoke(any())).thenReturn(Multi.createFrom().items(
                new AgentEvent.TextDelta("response")
        ));

        var result = StructuredAgentInvoker.invokeText(provider,
                AgentSessionConfig.of("system", "user"));

        assertThat(result).isInstanceOf(InvocationResult.Success.class);
        var success = (InvocationResult.Success<String>) result;
        assertThat(success.value()).isEqualTo("response");
        assertThat(success.metadata()).isEqualTo(InvocationMetadata.EMPTY);
    }

    // --- invoke (typed JSON) ---

    record TestGoal(String goalName, String goalDescription, String formationReason) {}

    @Test
    void invokeTypedParsesJsonResponse() {
        when(provider.invoke(any())).thenReturn(Multi.createFrom().items(
                new AgentEvent.TextDelta("""
                        {"goalName": "explore-gaps", "goalDescription": "explore knowledge gaps", "formationReason": "high curiosity"}"""),
                new AgentEvent.InvocationComplete(15, 10, 0, 0, 0, 0.002, 300L, 250L, "s1", 1, false)
        ));

        var result = StructuredAgentInvoker.invoke(provider,
                AgentSessionConfig.of("system", "user"), TestGoal.class);

        assertThat(result).isInstanceOf(InvocationResult.Success.class);
        var success = (InvocationResult.Success<TestGoal>) result;
        assertThat(success.value().goalName()).isEqualTo("explore-gaps");
        assertThat(success.value().goalDescription()).isEqualTo("explore knowledge gaps");
        assertThat(success.metadata().inputTokens()).isEqualTo(15);
    }

    @Test
    void invokeTypedStripsMarkdownFences() {
        when(provider.invoke(any())).thenReturn(Multi.createFrom().items(
                new AgentEvent.TextDelta("```json\n"),
                new AgentEvent.TextDelta("""
                        {"goalName": "reconnect", "goalDescription": "reconnect with user", "formationReason": "affiliation"}"""),
                new AgentEvent.TextDelta("\n```"),
                new AgentEvent.InvocationComplete(10, 5, 0, 0, 0, null, 200L, 150L, "s1", 1, false)
        ));

        var result = StructuredAgentInvoker.invoke(provider,
                AgentSessionConfig.of("system", "user"), TestGoal.class);

        assertThat(result).isInstanceOf(InvocationResult.Success.class);
        assertThat(((InvocationResult.Success<TestGoal>) result).value().goalName()).isEqualTo("reconnect");
    }

    @Test
    void invokeTypedStripsUnlabelledFences() {
        when(provider.invoke(any())).thenReturn(Multi.createFrom().items(
                new AgentEvent.TextDelta("```\n{\"goalName\": \"test\", \"goalDescription\": \"d\", \"formationReason\": \"r\"}\n```"),
                new AgentEvent.InvocationComplete(10, 5, 0, 0, 0, null, 200L, 150L, "s1", 1, false)
        ));

        var result = StructuredAgentInvoker.invoke(provider,
                AgentSessionConfig.of("system", "user"), TestGoal.class);

        assertThat(result).isInstanceOf(InvocationResult.Success.class);
        assertThat(((InvocationResult.Success<TestGoal>) result).value().goalName()).isEqualTo("test");
    }

    @Test
    void invokeTypedReturnsParseErrorOnInvalidJson() {
        when(provider.invoke(any())).thenReturn(Multi.createFrom().items(
                new AgentEvent.TextDelta("not valid json at all"),
                new AgentEvent.InvocationComplete(10, 5, 0, 0, 0, null, 200L, 150L, "s1", 1, false)
        ));

        var result = StructuredAgentInvoker.invoke(provider,
                AgentSessionConfig.of("system", "user"), TestGoal.class);

        assertThat(result).isInstanceOf(InvocationResult.ParseError.class);
        var parseError = (InvocationResult.ParseError<TestGoal>) result;
        assertThat(parseError.rawResponse()).isEqualTo("not valid json at all");
        assertThat(parseError.parseError()).isNotBlank();
        assertThat(parseError.metadata().inputTokens()).isEqualTo(10);
    }

    @Test
    void invokeTypedReturnsAgentErrorOnBlankResponse() {
        when(provider.invoke(any())).thenReturn(Multi.createFrom().items(
                new AgentEvent.InvocationComplete(0, 0, 0, 0, 0, null, 100L, 80L, "s1", 1, false)
        ));

        var result = StructuredAgentInvoker.invoke(provider,
                AgentSessionConfig.of("system", "user"), TestGoal.class);

        assertThat(result).isInstanceOf(InvocationResult.AgentError.class);
    }

    @Test
    void invokeTypedReturnsAgentErrorOnProviderException() {
        when(provider.invoke(any())).thenReturn(
                Multi.createFrom().failure(new RuntimeException("timeout")));

        var result = StructuredAgentInvoker.invoke(provider,
                AgentSessionConfig.of("system", "user"), TestGoal.class);

        assertThat(result).isInstanceOf(InvocationResult.AgentError.class);
        assertThat(((InvocationResult.AgentError<TestGoal>) result).reason()).contains("timeout");
    }

    @Test
    void invokeTypedHandlesJsonWithSurroundingText() {
        when(provider.invoke(any())).thenReturn(Multi.createFrom().items(
                new AgentEvent.TextDelta("Here is the result:\n{\"goalName\": \"test\", \"goalDescription\": \"d\", \"formationReason\": \"r\"}\nDone."),
                new AgentEvent.InvocationComplete(10, 5, 0, 0, 0, null, 200L, 150L, "s1", 1, false)
        ));

        var result = StructuredAgentInvoker.invoke(provider,
                AgentSessionConfig.of("system", "user"), TestGoal.class);

        assertThat(result).isInstanceOf(InvocationResult.Success.class);
        assertThat(((InvocationResult.Success<TestGoal>) result).value().goalName()).isEqualTo("test");
    }

    // --- invokeRaw (JsonNode) ---

    @Test
    void invokeRawReturnsJsonNode() {
        when(provider.invoke(any())).thenReturn(Multi.createFrom().items(
                new AgentEvent.TextDelta("{\"agent\": \"worker-1\", \"reason\": \"best fit\"}"),
                new AgentEvent.InvocationComplete(10, 5, 0, 0, 0, null, 200L, 150L, "s1", 1, false)
        ));

        var result = StructuredAgentInvoker.invokeRaw(provider,
                AgentSessionConfig.of("system", "user"));

        assertThat(result).isInstanceOf(InvocationResult.Success.class);
        var node = ((InvocationResult.Success<com.fasterxml.jackson.databind.JsonNode>) result).value();
        assertThat(node.get("agent").asText()).isEqualTo("worker-1");
    }

    // --- stripFences ---

    @Test
    void stripFencesRemovesJsonFence() {
        assertThat(StructuredAgentInvoker.stripFences("```json\n{\"a\": 1}\n```"))
                .isEqualTo("{\"a\": 1}");
    }

    @Test
    void stripFencesRemovesPlainFence() {
        assertThat(StructuredAgentInvoker.stripFences("```\n{\"a\": 1}\n```"))
                .isEqualTo("{\"a\": 1}");
    }

    @Test
    void stripFencesLeavesPlainJsonAlone() {
        assertThat(StructuredAgentInvoker.stripFences("{\"a\": 1}"))
                .isEqualTo("{\"a\": 1}");
    }

    @Test
    void stripFencesHandlesNull() {
        assertThat(StructuredAgentInvoker.stripFences(null)).isNull();
    }

    @Test
    void stripFencesHandlesBlank() {
        assertThat(StructuredAgentInvoker.stripFences("  ")).isEqualTo("  ");
    }

    // --- extractJson ---

    @Test
    void extractJsonFindsEmbeddedObject() {
        assertThat(StructuredAgentInvoker.extractJson("prefix {\"key\": \"value\"} suffix"))
                .isEqualTo("{\"key\": \"value\"}");
    }

    @Test
    void extractJsonFindsEmbeddedArray() {
        assertThat(StructuredAgentInvoker.extractJson("text [1, 2, 3] more"))
                .isEqualTo("[1, 2, 3]");
    }

    @Test
    void extractJsonReturnsOriginalWhenNoJsonFound() {
        assertThat(StructuredAgentInvoker.extractJson("no json here"))
                .isEqualTo("no json here");
    }

    @Test
    void extractJsonHandlesNestedBraces() {
        var nested = "{\"outer\": {\"inner\": 1}}";
        assertThat(StructuredAgentInvoker.extractJson("before " + nested + " after"))
                .isEqualTo(nested);
    }
}
