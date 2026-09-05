package io.casehub.blocks.speech.ws;

import io.casehub.blocks.speech.AssembledPrompt;
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

    @Inject
    jakarta.enterprise.inject.Instance<io.casehub.platform.agent.AgentProvider> agentProvider;


    @Inject
    jakarta.enterprise.inject.Instance<TtsModelRegistry> ttsRegistry;
    @Inject
    jakarta.enterprise.inject.Instance<CorrectionHooks>  correctionHooks;

    @Inject
    jakarta.enterprise.inject.Instance<io.casehub.blocks.speech.SpeakerEmbeddingExtractor> embeddingExtractor;

    @Inject
    jakarta.enterprise.inject.Instance<io.casehub.blocks.speech.SpeakerRegistry> speakerRegistryInstance;

    @Inject
    jakarta.enterprise.inject.Instance<io.casehub.blocks.speech.AvatarCognition> avatarCognition;

    private SpeechSession session;
    private @org.jspecify.annotations.Nullable Thread proactiveThread;

    @OnOpen
    public void onOpen(WebSocketConnection connection) {
        var assembler = new DefaultPromptAssembler(
                avatarConfig.systemPrompt().orElse(null));

        java.util.function.Function<AssembledPrompt, String> generator    = null;
        SpeechSession.StreamingResponseGenerator             streamingGen = null;
        if (agentProvider.isResolvable()) {
            var agent = agentProvider.get();
            generator = prompt -> {
                var config = prompt.model() != null
                             ? io.casehub.platform.agent.AgentSessionConfig.of(
                        prompt.systemPrompt(), prompt.userPrompt(), prompt.model())
                             : io.casehub.platform.agent.AgentSessionConfig.of(
                        prompt.systemPrompt(), prompt.userPrompt());
                return agent.invoke(config)
                            .filter(e -> e instanceof io.casehub.platform.agent.AgentEvent.TextDelta)
                            .map(e -> ((io.casehub.platform.agent.AgentEvent.TextDelta) e).text())
                            .collect().asList()
                            .map(parts -> String.join("", parts))
                            .await().atMost(java.time.Duration.ofSeconds(60));
            };

            streamingGen = (prompt, onSentence) -> {
                var config = prompt.model() != null
                             ? io.casehub.platform.agent.AgentSessionConfig.of(
                        prompt.systemPrompt(), prompt.userPrompt(), prompt.model())
                             : io.casehub.platform.agent.AgentSessionConfig.of(
                        prompt.systemPrompt(), prompt.userPrompt());
                var buffer   = new StringBuilder();
                var fullText = new StringBuilder();

                agent.invoke(config)
                     .filter(e -> e instanceof io.casehub.platform.agent.AgentEvent.TextDelta)
                     .map(e -> ((io.casehub.platform.agent.AgentEvent.TextDelta) e).text())
                     .onItem().invoke(delta -> {
                         fullText.append(delta);
                         buffer.append(delta);
                         int boundary = findSentenceBoundary(buffer);
                         while (boundary > 0) {
                             String sentence = buffer.substring(0, boundary).trim();
                             buffer.delete(0, boundary);
                             if (!sentence.isEmpty()) {
                                 onSentence.accept(sentence);
                             }
                             boundary = findSentenceBoundary(buffer);
                         }
                     })
                     .collect().last()
                     .await().atMost(java.time.Duration.ofSeconds(120));

                if (!buffer.isEmpty()) {
                    String remaining = buffer.toString().trim();
                    if (!remaining.isEmpty()) {
                        onSentence.accept(remaining);
                    }
                }
                return fullText.toString();
            };
        }

        CorrectionHooks hooks = correctionHooks.isResolvable() ? correctionHooks.get() : null;

        session = new SpeechSession(
                sttService, ttsService, cleanupConfig,
                generator,
                streamingGen,
                assembler,
                text -> connection.sendTextAndAwait(text),
                data -> connection.sendBinaryAndAwait(data),
                ttsRegistry.isResolvable() ? ttsRegistry.get().models() : java.util.Map.of(),
                hooks != null ? hooks.corrector() : null,
                hooks != null ? hooks.onResponse() : null,
                hooks != null ? hooks.vocabularyHintSupplier() : null);

        if (embeddingExtractor.isResolvable() && speakerRegistryInstance.isResolvable()) {
            session.withSpeakerServices(embeddingExtractor.get(), speakerRegistryInstance.get());
        }

        if (avatarCognition.isResolvable()
                && avatarConfig.agentId().isPresent()
                && avatarConfig.tenantId().isPresent()) {
            var cog = avatarCognition.get();
            var agentId = avatarConfig.agentId().get();
            var tenantId = avatarConfig.tenantId().get();
            var wrappedAssembler = cog.wrapAssembler(assembler, agentId, tenantId,
                    session::currentSubjectId);
            session = new SpeechSession(
                    sttService, ttsService, cleanupConfig,
                    generator, streamingGen, wrappedAssembler,
                    text -> connection.sendTextAndAwait(text),
                    data -> connection.sendBinaryAndAwait(data),
                    ttsRegistry.isResolvable() ? ttsRegistry.get().models() : java.util.Map.of(),
                    hooks != null ? hooks.corrector() : null,
                    hooks != null ? hooks.onResponse() : null,
                    hooks != null ? hooks.vocabularyHintSupplier() : null);
            if (embeddingExtractor.isResolvable() && speakerRegistryInstance.isResolvable()) {
                session.withSpeakerServices(embeddingExtractor.get(), speakerRegistryInstance.get());
            }
            session.withAvatarCognition(cog, agentId, tenantId);
            cog.initialize(agentId, tenantId);
            startProactiveTick(cog, agentId, tenantId, avatarConfig.proactiveTickIntervalSeconds());
        }
    }

    private void startProactiveTick(io.casehub.blocks.speech.AvatarCognition cog,
                                     String agentId, String tenantId, int intervalSeconds) {
        proactiveThread = Thread.ofVirtual().name("avatar-proactive").start(() -> {
            while (!session.isClosed()) {
                try {
                    Thread.sleep(java.time.Duration.ofSeconds(intervalSeconds));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (session.isClosed()) return;
                try {
                    cog.tick(agentId, tenantId, java.util.Set.of(session.currentSubjectId()));
                    String channelContext = "Channel: WebSocket speech session\nTurn count: "
                            + session.historySize() + "\nSpeaker: " + session.currentSubjectId();
                    String content = cog.evaluateProactive(agentId, tenantId, channelContext);
                    if (content != null) {
                        session.handleProactiveSpeech(content);
                    }
                } catch (Exception e) {
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        });
    }

    @OnTextMessage
    public void onText(String message) {
        AvatarMessage msg = MessageCodec.decodeClient(message);
        switch (msg) {
            case AvatarMessage.Start s -> session.handleStart(s);
            case AvatarMessage.Stop s -> session.handleStop();
            case AvatarMessage.Text t -> session.handleText(t.text(), t.llmModel(), t.ttsModel());
            case AvatarMessage.SpeakerIdentify si -> session.handleSpeakerIdentify(si.name());
            default -> {}
        }
    }

    @OnBinaryMessage
    public void onBinary(byte[] data) {
        var     floatBuf = java.nio.ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
        float[] samples  = new float[floatBuf.remaining()];
        floatBuf.get(samples);
        session.handleAudio(samples);
    }

    @OnClose
    public void onClose() {
        if (session != null) {
            session.close();
        }
        if (proactiveThread != null) {
            proactiveThread.interrupt();
        }
    }

    private static int findSentenceBoundary(StringBuilder buffer) {
        for (int i = 0; i < buffer.length() - 1; i++) {
            char c = buffer.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && Character.isWhitespace(buffer.charAt(i + 1))) {
                return i + 1;
            }
        }
        return -1;
    }

}
