package io.casehub.blocks.speech.ws.protocol;

import java.util.List;

public sealed interface AvatarMessage {

    record Start(int sampleRate) implements AvatarMessage {}

    record Stop() implements AvatarMessage {}

    record Partial(String text) implements AvatarMessage {}

    record Transcript(String text) implements AvatarMessage {}

    record Response(String text) implements AvatarMessage {}

    record Phonemes(List<VisemeFrame> data) implements AvatarMessage {
        public Phonemes { data = List.copyOf(data); }
    }

    record Error(String message) implements AvatarMessage {}
}
