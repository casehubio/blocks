package io.casehub.blocks.channel.summary;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.spi.SummaryResult;
import io.casehub.qhorus.api.spi.SummaryUpdateContext;
import io.casehub.qhorus.api.spi.SummaryUpdateHook;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.stream.Collectors;

@Alternative
@Priority(1)
@ApplicationScoped
public class LlmChannelSummariser implements SummaryUpdateHook {

    private static final System.Logger LOG = System.getLogger(LlmChannelSummariser.class.getName());

    private static final String SYSTEM_PROMPT_EDIT = """
            You are a channel summariser. Given the current summary of a conversation \
            channel and a batch of new messages, produce an updated summary that \
            integrates the new information. You may rewrite any part of the existing \
            summary that the new messages change — discussions that are now resolved, \
            plans that are now confirmed, concerns that are now addressed. \
            Be concise. Use plain text.""";

    private static final String SYSTEM_PROMPT_APPEND = """
            You are a channel summariser. Given the current summary of a conversation \
            channel and a batch of new messages, append a brief update section \
            summarising the new messages. Do not modify the existing summary. \
            Be concise. Use plain text.""";

    private final AgentProvider agentProvider;
    private final SummaryMode mode;

    @Inject
    public LlmChannelSummariser(AgentProvider agentProvider,
                                 @ConfigProperty(name = "casehub.blocks.channel.summary.mode",
                                                  defaultValue = "EDIT")
                                 SummaryMode mode) {
        this.agentProvider = agentProvider;
        this.mode = mode;
    }

    @Override
    public SummaryResult update(SummaryUpdateContext context) {
        if (context.recentMessages() == null || context.recentMessages().isEmpty()) {
            return SummaryResult.ofText(context.currentSummary());
        }

        try {
            var userPrompt = buildUserPrompt(context);
            var systemPrompt = mode == SummaryMode.EDIT ? SYSTEM_PROMPT_EDIT : SYSTEM_PROMPT_APPEND;
            var config = AgentSessionConfig.of(systemPrompt, userPrompt);

            String text = agentProvider.invoke(config)
                    .filter(e -> e instanceof AgentEvent.TextDelta)
                    .map(e -> ((AgentEvent.TextDelta) e).text())
                    .collect().with(Collectors.joining())
                    .await().indefinitely();
            return SummaryResult.ofText(text);
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING,
                    "LLM summarisation failed for channel " + context.channelName(), e);
            throw e;
        }
    }

    private String buildUserPrompt(SummaryUpdateContext context) {
        var sb = new StringBuilder();
        sb.append("Channel: ").append(context.channelName()).append("\n\n");

        if (context.currentSummary() != null && !context.currentSummary().isBlank()) {
            sb.append("Current summary:\n").append(context.currentSummary()).append("\n\n");
        }

        sb.append("New messages (").append(context.recentMessages().size()).append("):\n");
        for (Message msg : context.recentMessages()) {
            sb.append("[").append(msg.sender()).append("] ").append(msg.content()).append("\n");
        }
        return sb.toString();
    }
}
