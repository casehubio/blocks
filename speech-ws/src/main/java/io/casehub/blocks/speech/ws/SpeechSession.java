package io.casehub.blocks.speech.ws;

import io.casehub.blocks.speech.CleanupConfig;
import io.casehub.blocks.speech.RecognitionStream;
import io.casehub.blocks.speech.StreamingSpeechToTextService;
import io.casehub.blocks.speech.SynthesisOptions;
import io.casehub.blocks.speech.SynthesisResult;
import io.casehub.blocks.speech.TextToSpeechService;
import io.casehub.blocks.speech.TranscriptionResult;
import io.casehub.blocks.speech.ws.protocol.AvatarMessage;
import io.casehub.blocks.speech.ws.protocol.ConversationTurn;
import io.casehub.blocks.speech.ws.protocol.MessageCodec;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public class SpeechSession {

    private final StreamingSpeechToTextService sttService;
    private final TextToSpeechService ttsService;
    private final CleanupConfig cleanupConfig;
    private final @Nullable UnaryOperator<String> responseGenerator;
    private final PromptAssembler promptAssembler;
    private final Consumer<String> textSink;
    private final Consumer<byte[]> binarySink;
    private final List<ConversationTurn> history = new ArrayList<>();

    private @Nullable RecognitionStream stream;

    public SpeechSession(StreamingSpeechToTextService sttService,
                         TextToSpeechService ttsService,
                         CleanupConfig cleanupConfig,
                         @Nullable UnaryOperator<String> responseGenerator,
                         PromptAssembler promptAssembler,
                         Consumer<String> textSink,
                         Consumer<byte[]> binarySink) {
        this.sttService = sttService;
        this.ttsService = ttsService;
        this.cleanupConfig = cleanupConfig;
        this.responseGenerator = responseGenerator;
        this.promptAssembler = promptAssembler;
        this.textSink = textSink;
        this.binarySink = binarySink;
    }

    public void handleStart(AvatarMessage.Start msg) {
        if (stream != null) {
            stream.close();
        }
        stream = sttService.startStream(null);
    }

    public void handleAudio(float[] samples, int sampleRate) {
        if (stream != null) {
            stream.acceptSamples(samples, sampleRate);
        }
    }

    public void handleStop() {
        if (stream == null) {
            send(new AvatarMessage.Error("No active recording session"));
            return;
        }
        try {
            TranscriptionResult result = stream.finalResult();
            stream.close();
            stream = null;

            String cleanText = cleanupConfig.apply(result.text());
            send(new AvatarMessage.Transcript(cleanText));
            history.add(new ConversationTurn("user", cleanText));

            String responseText = generateResponse(cleanText);
            if (responseText != null) {
                send(new AvatarMessage.Response(responseText));
                history.add(new ConversationTurn("assistant", responseText));

                SynthesisResult synthesis = ttsService.synthesise(responseText,
                        new SynthesisOptions(null, null, "wav", true));

                var visemeFrames = VisemeMapping.convert(synthesis.phonemes());
                send(new AvatarMessage.Phonemes(visemeFrames));
                binarySink.accept(synthesis.audioData());
            }
        } catch (Exception e) {
            send(new AvatarMessage.Error(e.getMessage()));
        }
    }

    private @Nullable String generateResponse(String userMessage) {
        if (responseGenerator == null) {
            send(new AvatarMessage.Error("No LLM provider configured"));
            return null;
        }
        String prompt = promptAssembler.assemble(userMessage, List.copyOf(history));
        return responseGenerator.apply(prompt);
    }

    private void send(AvatarMessage msg) {
        textSink.accept(MessageCodec.encode(msg));
    }

    public void close() {
        if (stream != null) {
            stream.close();
            stream = null;
        }
    }
}
