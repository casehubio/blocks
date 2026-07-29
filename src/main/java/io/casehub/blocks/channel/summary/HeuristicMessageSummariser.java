package io.casehub.blocks.channel.summary;

import io.casehub.blocks.summarisation.ContentSummariser;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.spi.SummaryResult;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@DefaultBean
@ApplicationScoped
public class HeuristicMessageSummariser implements ContentSummariser<Message> {

    @Override
    public CompletionStage<SummaryResult> summarise(
            List<Message> messages, @Nullable SummaryResult previous) {
        if (messages.isEmpty()) {
            return CompletableFuture.completedFuture(
                    previous != null ? previous : SummaryResult.ofText(""));
        }
        var sb = new StringBuilder();
        if (previous != null && !previous.text().isBlank()) {
            sb.append(previous.text()).append("\n\n");
        }
        sb.append("--- Update (").append(messages.size()).append(" messages) ---\n");

        var participants = messages.stream()
                .map(Message::sender).filter(Objects::nonNull).distinct().toList();
        if (!participants.isEmpty()) {
            sb.append("Participants: ").append(String.join(", ", participants)).append('\n');
        }

        var first = messages.getFirst().createdAt();
        var last = messages.getLast().createdAt();
        if (first != null && last != null) {
            sb.append("Period: ").append(first).append(" — ").append(last).append('\n');
        }

        var topics = messages.stream()
                .map(Message::topic).filter(t -> t != null && !t.isBlank()).distinct().toList();
        if (!topics.isEmpty()) {
            sb.append("Topics: ").append(String.join(", ", topics)).append('\n');
        }

        var allParticipants = new LinkedHashSet<String>();
        var allTopics = new LinkedHashSet<String>();
        if (previous != null) {
            var prev = previous.annotations();
            if (prev.containsKey("participants")) {
                allParticipants.addAll(List.of(prev.get("participants").split(",")));
            }
            if (prev.containsKey("topics")) {
                allTopics.addAll(List.of(prev.get("topics").split(",")));
            }
        }
        allParticipants.addAll(participants);
        allTopics.addAll(topics);

        var annotations = new HashMap<>(
                previous != null ? previous.annotations() : Map.of());
        annotations.put("tier", "grouped");
        annotations.put("itemCount", String.valueOf(messages.size()));
        if (!allParticipants.isEmpty()) {
            annotations.put("participants", String.join(",", allParticipants));
        }
        if (!allTopics.isEmpty()) {
            annotations.put("topics", String.join(",", allTopics));
        }

        return CompletableFuture.completedFuture(
                new SummaryResult(sb.toString().stripTrailing(), annotations));
    }
}
