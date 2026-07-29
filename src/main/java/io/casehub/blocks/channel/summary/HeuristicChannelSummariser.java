package io.casehub.blocks.channel.summary;

import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.spi.SummaryResult;
import io.casehub.qhorus.api.spi.SummaryUpdateContext;
import io.casehub.qhorus.api.spi.SummaryUpdateHook;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@DefaultBean
@ApplicationScoped
public class HeuristicChannelSummariser implements SummaryUpdateHook {

    @Override
    public SummaryResult update(SummaryUpdateContext context) {
        if (context.recentMessages() == null || context.recentMessages().isEmpty()) {
            return SummaryResult.ofText(context.currentSummary());
        }
        return SummaryResult.ofText(appendDelta(context));
    }

    private String appendDelta(SummaryUpdateContext context) {
        var messages = context.recentMessages();
        var sb = new StringBuilder();

        if (context.currentSummary() != null && !context.currentSummary().isBlank()) {
            sb.append(context.currentSummary()).append("\n\n");
        }

        sb.append("--- Update (").append(messages.size()).append(" messages) ---\n");

        var participants = messages.stream()
                .map(Message::sender)
                .filter(s -> s != null)
                .distinct()
                .toList();
        if (!participants.isEmpty()) {
            sb.append("Participants: ").append(String.join(", ", participants)).append("\n");
        }

        var first = messages.getFirst().createdAt();
        var last = messages.getLast().createdAt();
        if (first != null && last != null) {
            sb.append("Period: ").append(first).append(" — ").append(last).append("\n");
        }

        var topics = messages.stream()
                .map(Message::topic)
                .filter(t -> t != null && !t.isBlank())
                .distinct()
                .toList();
        if (!topics.isEmpty()) {
            sb.append("Topics: ").append(String.join(", ", topics)).append("\n");
        }

        return sb.toString().stripTrailing();
    }
}
