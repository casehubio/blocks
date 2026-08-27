package io.casehub.blocks.speech.ws.protocol;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class MessageCodec {

    private static final Gson GSON = new Gson();

    private MessageCodec() {}

    public static String encode(AvatarMessage msg) {
        var obj = new JsonObject();
        switch (msg) {
            case AvatarMessage.Partial p -> {
                obj.addProperty("type", "partial");
                obj.addProperty("text", p.text());
            }
            case AvatarMessage.Transcript t -> {
                obj.addProperty("type", "transcript");
                obj.addProperty("text", t.text());
            }
            case AvatarMessage.Response r -> {
                obj.addProperty("type", "response");
                obj.addProperty("text", r.text());
            }
            case AvatarMessage.Phonemes ph -> {
                obj.addProperty("type", "phonemes");
                obj.add("data", GSON.toJsonTree(ph.data()));
            }
            case AvatarMessage.Error e -> {
                obj.addProperty("type", "error");
                obj.addProperty("message", e.message());
            }
            case AvatarMessage.Start s -> {
                obj.addProperty("type", "start");
                obj.addProperty("sampleRate", s.sampleRate());
            }
            case AvatarMessage.Stop s -> obj.addProperty("type", "stop");
        }
        return GSON.toJson(obj);
    }

    public static AvatarMessage decodeClient(String json) {
        var obj = JsonParser.parseString(json).getAsJsonObject();
        String type = obj.get("type").getAsString();
        return switch (type) {
            case "start" -> new AvatarMessage.Start(
                    obj.has("sampleRate") ? obj.get("sampleRate").getAsInt() : 16000);
            case "stop" -> new AvatarMessage.Stop();
            default -> throw new IllegalArgumentException("Unknown client message type: " + type);
        };
    }
}
