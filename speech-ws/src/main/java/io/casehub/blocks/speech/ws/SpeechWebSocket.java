package io.casehub.blocks.speech.ws;

import io.casehub.blocks.speech.CleanupConfig;
import io.casehub.blocks.speech.StreamingSpeechToTextService;
import io.casehub.blocks.speech.TextToSpeechService;
import io.casehub.blocks.speech.ws.protocol.AvatarMessage;
import io.casehub.blocks.speech.ws.protocol.MessageCodec;
import io.quarkus.websockets.next.OnBinaryMessage;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.inject.Inject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@WebSocket(path = "/ws/avatar")
public class SpeechWebSocket {

    @Inject
    StreamingSpeechToTextService sttService;

    @Inject
    TextToSpeechService ttsService;

    @Inject
    CleanupConfig cleanupConfig;

    @Inject
    AvatarConfig avatarConfig;

    private SpeechSession session;

    @OnOpen
    public void onOpen(WebSocketConnection connection) {
        var assembler = new DefaultPromptAssembler(
                avatarConfig.systemPrompt().orElse(null));
        session = new SpeechSession(
                sttService, ttsService, cleanupConfig,
                null,
                assembler,
                text -> connection.sendTextAndAwait(text),
                data -> connection.sendBinaryAndAwait(data));
    }

    @OnTextMessage
    public void onText(String message) {
        AvatarMessage msg = MessageCodec.decodeClient(message);
        switch (msg) {
            case AvatarMessage.Start s -> session.handleStart(s);
            case AvatarMessage.Stop s -> session.handleStop();
            default -> { }
        }
    }

    @OnBinaryMessage
    public void onBinary(ByteBuffer data) {
        var floatBuf = data.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
        float[] samples = new float[floatBuf.remaining()];
        floatBuf.get(samples);
        session.handleAudio(samples, avatarConfig.sampleRate());
    }

    @OnClose
    public void onClose() {
        if (session != null) {
            session.close();
        }
    }
}
