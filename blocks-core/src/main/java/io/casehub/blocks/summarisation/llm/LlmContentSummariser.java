package io.casehub.blocks.summarisation.llm;

import io.casehub.blocks.agent.StructuredAgentInvoker;
import io.casehub.blocks.agent.StructuredAgentInvoker.InvocationResult;
import io.casehub.blocks.summarisation.ContentSummariser;
import io.casehub.blocks.summarisation.SummaryMode;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.qhorus.api.spi.SummaryResult;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class LlmContentSummariser<T> implements ContentSummariser<T, SummaryResult> {

    private static final String EDIT_PROMPT = """
            You are a content summariser. Given the current summary and a batch of \
            new items, produce an updated summary that integrates the new information. \
            You may rewrite any part of the existing summary that the new items change \
            — topics that are now resolved, plans that are now confirmed, concerns \
            that are now addressed. Be concise. Use plain text.""";

    private static final String APPEND_PROMPT = """
            You are a content summariser. Given the current summary and a batch of \
            new items, append a brief update section summarising the new items. \
            Do not modify the existing summary. Be concise. Use plain text.""";

    private final AgentProvider agentProvider;
    private final Function<T, String> renderer;
    private final SummaryMode mode;
    private final @Nullable String preamble;

    public LlmContentSummariser(AgentProvider agentProvider,
                                 Function<T, String> renderer,
                                 SummaryMode mode,
                                 @Nullable String preamble) {
        this.agentProvider = agentProvider;
        this.renderer = renderer;
        this.mode = mode;
        this.preamble = preamble;
    }

    public LlmContentSummariser(AgentProvider agentProvider,
                                 Function<T, String> renderer,
                                 SummaryMode mode) {
        this(agentProvider, renderer, mode, null);
    }

    @Override
    public CompletionStage<SummaryResult> summarise(
            List<T> items, @Nullable SummaryResult previous) {
        String systemPrompt = mode == SummaryMode.EDIT ? EDIT_PROMPT : APPEND_PROMPT;
        String userPrompt = buildPrompt(items, previous);
        var config = AgentSessionConfig.of(systemPrompt, userPrompt);

        var result = StructuredAgentInvoker.invokeText(agentProvider, config);
        if (result instanceof InvocationResult.AgentError<String> err) {
            return CompletableFuture.failedFuture(
                    new RuntimeException("LLM summarisation failed: " + err.reason()));
        }
        var text = ((InvocationResult.Success<String>) result).value();
        var annotations = new HashMap<>(
                previous != null ? previous.annotations() : Map.of());
        annotations.put("tier", "synthesised");
        annotations.put("itemCount", String.valueOf(items.size()));
        return CompletableFuture.completedFuture(new SummaryResult(text, annotations));
    }

    private String buildPrompt(List<T> items, @Nullable SummaryResult previous) {
        var sb = new StringBuilder();
        if (preamble != null && !preamble.isBlank()) {
            sb.append(preamble).append("\n\n");
        }
        if (previous != null && !previous.text().isBlank()) {
            sb.append("Current summary:\n").append(previous.text()).append("\n\n");
        }
        sb.append("New items (").append(items.size()).append("):\n");
        for (T item : items) {
            sb.append(renderer.apply(item)).append('\n');
        }
        return sb.toString();
    }
}
