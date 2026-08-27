package io.casehub.blocks.speech.ws.protocol;

import java.util.List;

public sealed interface AvatarMessage {
    record Start(int sampleRate) implements AvatarMessage {}
    record Stop() implements AvatarMessage {}
    record Text(String text, @org.jspecify.annotations.Nullable String llmModel, @org.jspecify.annotations.Nullable String ttsModel) implements AvatarMessage { public Text(String text) { this(text, null, null); } }
    record Partial(String text) implements AvatarMessage {}
    record Transcript(String text) implements AvatarMessage {}
    record Response(String text) implements AvatarMessage {}
    record Phonemes(List<VisemeFrame> data) implements AvatarMessage { public Phonemes { data = List.copyOf(data); } }
    record Timing(long cleanupMs, long llmMs, long ttsMs, long totalMs) implements AvatarMessage {}
    record Error(String message) implements AvatarMessage {}
}
