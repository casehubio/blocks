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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import org.jspecify.annotations.Nullable;

import java.util.stream.Collectors;

public final class StructuredAgentInvoker {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private StructuredAgentInvoker() {}

    public static InvocationResult<String> invokeText(AgentProvider provider,
                                                       AgentSessionConfig config) {
        try {
            var textBuilder = new StringBuilder();
            var metadata = new InvocationMetadata[1];

            provider.invoke(config)
                    .subscribe().asStream()
                    .forEach(event -> {
                        if (event instanceof AgentEvent.TextDelta td) {
                            textBuilder.append(td.text());
                        } else if (event instanceof AgentEvent.InvocationComplete ic) {
                            metadata[0] = toMetadata(ic);
                        }
                    });

            var text = textBuilder.toString();
            var meta = metadata[0] != null ? metadata[0] : InvocationMetadata.EMPTY;

            if (text.isBlank()) {
                return new InvocationResult.AgentError<>("Empty response");
            }

            return new InvocationResult.Success<>(text, meta);
        } catch (Exception e) {
            return new InvocationResult.AgentError<>(e.getMessage());
        }
    }

    public static <T> InvocationResult<T> invoke(AgentProvider provider,
                                                  AgentSessionConfig config,
                                                  Class<T> responseType) {
        var textResult = invokeText(provider, config);
        if (textResult instanceof InvocationResult.AgentError<String> err) {
            return new InvocationResult.AgentError<>(err.reason());
        }

        var success = (InvocationResult.Success<String>) textResult;
        var raw = success.value();
        var cleaned = stripFences(raw);
        var json = extractJson(cleaned);

        try {
            T parsed = MAPPER.readValue(json, responseType);
            return new InvocationResult.Success<>(parsed, success.metadata());
        } catch (Exception e) {
            return new InvocationResult.ParseError<>(raw, e.getMessage(), success.metadata());
        }
    }

    public static InvocationResult<JsonNode> invokeRaw(AgentProvider provider,
                                                        AgentSessionConfig config) {
        var textResult = invokeText(provider, config);
        if (textResult instanceof InvocationResult.AgentError<String> err) {
            return new InvocationResult.AgentError<>(err.reason());
        }

        var success = (InvocationResult.Success<String>) textResult;
        var raw = success.value();
        var cleaned = stripFences(raw);
        var json = extractJson(cleaned);

        try {
            JsonNode node = MAPPER.readTree(json);
            return new InvocationResult.Success<>(node, success.metadata());
        } catch (Exception e) {
            return new InvocationResult.ParseError<>(raw, e.getMessage(), success.metadata());
        }
    }

    public static @Nullable String stripFences(@Nullable String text) {
        if (text == null || text.isBlank()) return text;
        var trimmed = text.trim();
        if (!trimmed.startsWith("```")) return text;

        int firstNewline = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstNewline >= 0 && lastFence > firstNewline) {
            return trimmed.substring(firstNewline + 1, lastFence).trim();
        }
        return text;
    }

    public static String extractJson(String text) {
        if (text == null) return text;
        var trimmed = text.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) return trimmed;

        int objStart = text.indexOf('{');
        int arrStart = text.indexOf('[');
        int start;
        char open, close;

        if (objStart >= 0 && (arrStart < 0 || objStart < arrStart)) {
            start = objStart;
            open = '{';
            close = '}';
        } else if (arrStart >= 0) {
            start = arrStart;
            open = '[';
            close = ']';
        } else {
            return text;
        }

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) continue;

            if (c == open) depth++;
            else if (c == close) {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }

        return text;
    }

    private static InvocationMetadata toMetadata(AgentEvent.InvocationComplete ic) {
        return new InvocationMetadata(
                ic.inputTokens(), ic.outputTokens(), ic.thinkingTokens(),
                ic.cacheReadTokens(), ic.cacheWriteTokens(),
                ic.totalCostUsd(), ic.durationMs(), ic.apiDurationMs());
    }

    public sealed interface InvocationResult<T> {
        record Success<T>(T value, InvocationMetadata metadata) implements InvocationResult<T> {}
        record ParseError<T>(String rawResponse, String parseError,
                             InvocationMetadata metadata) implements InvocationResult<T> {}
        record AgentError<T>(String reason) implements InvocationResult<T> {}
    }

    public record InvocationMetadata(
            int inputTokens, int outputTokens, int thinkingTokens,
            int cacheReadTokens, int cacheWriteTokens,
            @Nullable Double totalCostUsd, long durationMs, long apiDurationMs) {

        public static final InvocationMetadata EMPTY =
                new InvocationMetadata(0, 0, 0, 0, 0, null, 0L, 0L);
    }
}
