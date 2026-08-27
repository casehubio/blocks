package io.casehub.blocks.speech.ws.protocol;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageCodecTest {

    @Test
    void encodesPartialMessage() {
        String json = MessageCodec.encode(new AvatarMessage.Partial("hello"));
        var obj = JsonParser.parseString(json).getAsJsonObject();
        assertThat(obj.get("type").getAsString()).isEqualTo("partial");
        assertThat(obj.get("text").getAsString()).isEqualTo("hello");
    }

    @Test
    void encodesTranscriptMessage() {
        String json = MessageCodec.encode(new AvatarMessage.Transcript("Hello world."));
        var obj = JsonParser.parseString(json).getAsJsonObject();
        assertThat(obj.get("type").getAsString()).isEqualTo("transcript");
        assertThat(obj.get("text").getAsString()).isEqualTo("Hello world.");
    }

    @Test
    void encodesResponseMessage() {
        String json = MessageCodec.encode(new AvatarMessage.Response("I see."));
        var obj = JsonParser.parseString(json).getAsJsonObject();
        assertThat(obj.get("type").getAsString()).isEqualTo("response");
        assertThat(obj.get("text").getAsString()).isEqualTo("I see.");
    }

    @Test
    void encodesPhonemesMessage() {
        var frames = List.of(
                new VisemeFrame("DD", 0, 80),
                new VisemeFrame("aa", 80, 160));
        String json = MessageCodec.encode(new AvatarMessage.Phonemes(frames));
        var obj = JsonParser.parseString(json).getAsJsonObject();
        assertThat(obj.get("type").getAsString()).isEqualTo("phonemes");
        assertThat(obj.getAsJsonArray("data")).hasSize(2);
        assertThat(obj.getAsJsonArray("data").get(0).getAsJsonObject()
                .get("viseme").getAsString()).isEqualTo("DD");
    }

    @Test
    void encodesErrorMessage() {
        String json = MessageCodec.encode(new AvatarMessage.Error("service down"));
        var obj = JsonParser.parseString(json).getAsJsonObject();
        assertThat(obj.get("type").getAsString()).isEqualTo("error");
        assertThat(obj.get("message").getAsString()).isEqualTo("service down");
    }

    @Test
    void encodesStartMessage() {
        String json = MessageCodec.encode(new AvatarMessage.Start(44100));
        var obj = JsonParser.parseString(json).getAsJsonObject();
        assertThat(obj.get("type").getAsString()).isEqualTo("start");
        assertThat(obj.get("sampleRate").getAsInt()).isEqualTo(44100);
    }

    @Test
    void decodesStartMessage() {
        var msg = MessageCodec.decodeClient("{\"type\":\"start\",\"sampleRate\":16000}");
        assertThat(msg).isInstanceOf(AvatarMessage.Start.class);
        assertThat(((AvatarMessage.Start) msg).sampleRate()).isEqualTo(16000);
    }

    @Test
    void decodesStartMessageWithDefaultSampleRate() {
        var msg = MessageCodec.decodeClient("{\"type\":\"start\"}");
        assertThat(((AvatarMessage.Start) msg).sampleRate()).isEqualTo(16000);
    }

    @Test
    void decodesStopMessage() {
        var msg = MessageCodec.decodeClient("{\"type\":\"stop\"}");
        assertThat(msg).isInstanceOf(AvatarMessage.Stop.class);
    }

    @Test
    void rejectsUnknownClientMessageType() {
        assertThatThrownBy(() -> MessageCodec.decodeClient("{\"type\":\"foo\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown client message type");
    }
}
