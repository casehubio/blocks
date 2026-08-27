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
import java.util.function.Function;

public class SpeechSession {

    private final           StreamingSpeechToTextService               sttService;
    private final           TextToSpeechService                        ttsService;
    private final           CleanupConfig                              cleanupConfig;
    private final @Nullable Function<AssembledPrompt, String>          responseGenerator;
    private final           PromptAssembler                            promptAssembler;
    private final           Consumer<String>                           textSink;
    private final           Consumer<byte[]>                           binarySink;
    private final           List<ConversationTurn>                     history = new ArrayList<>();
    private final           java.util.Map<String, TextToSpeechService> ttsModels;

    private @Nullable RecognitionStream stream;
    private           int               sampleRate = 16000;
    private volatile  boolean           polling;
    private @Nullable Thread            pollingThread;

    public SpeechSession(StreamingSpeechToTextService sttService,
                         TextToSpeechService ttsService,
                         CleanupConfig cleanupConfig,
                         @Nullable Function<AssembledPrompt, String> responseGenerator,
                         PromptAssembler promptAssembler,
                         Consumer<String> textSink,
                         Consumer<byte[]> binarySink) {
        this(sttService, ttsService, cleanupConfig, responseGenerator, promptAssembler,
             textSink, binarySink, java.util.Map.of());
    }

    public SpeechSession(StreamingSpeechToTextService sttService,
                         TextToSpeechService ttsService,
                         CleanupConfig cleanupConfig,
                         @Nullable Function<AssembledPrompt, String> responseGenerator,
                         PromptAssembler promptAssembler,
                         Consumer<String> textSink,
                         Consumer<byte[]> binarySink,
                         java.util.Map<String, TextToSpeechService> ttsModels) {
        this.sttService        = sttService;
        this.ttsService        = ttsService;
        this.cleanupConfig     = cleanupConfig;
        this.responseGenerator = responseGenerator;
        this.promptAssembler   = promptAssembler;
        this.textSink          = textSink;
        this.binarySink        = binarySink;
        this.ttsModels         = ttsModels;
    }

    public void handleStart(AvatarMessage.Start msg) {
        stopPolling();
        if (stream != null) {
            stream.close();
        }
        this.sampleRate = msg.sampleRate();
        stream          = sttService.startStream(null);
        startPolling();
    }

    public void handleAudio(float[] samples) {
        if (stream != null) {
            stream.acceptSamples(samples, sampleRate);
        }
    }

    public void handleStop() {
        stopPolling();
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

            String responseText = generateResponse(cleanText, null);
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

    public void handleText(String text) {
        handleText(text, null, null);
    }

    public void handleText(String text, @Nullable String llmModel, @Nullable String ttsModel) {
        long t0 = System.nanoTime();
        try {
            String cleanText   = cleanupConfig.apply(text);
            long   cleanupDone = System.nanoTime();

            send(new AvatarMessage.Transcript(cleanText));
            history.add(new ConversationTurn("user", cleanText));

            String responseText = generateResponse(cleanText, llmModel);
            long   llmDone      = System.nanoTime();

            if (responseText != null) {
                send(new AvatarMessage.Response(responseText));
                history.add(new ConversationTurn("assistant", responseText));

                TextToSpeechService tts = resolveTts(ttsModel);
                String[] sentences = responseText.split("(?<=[.!?])\\s+");
                for (String sentence : sentences) {
                    if (sentence.isBlank()) continue;
                    SynthesisResult synthesis = tts.synthesise(sentence,
                            new SynthesisOptions(null, null, "wav", true));
                    var visemeFrames = VisemeMapping.convert(synthesis.phonemes());
                    send(new AvatarMessage.Phonemes(visemeFrames));
                    binarySink.accept(synthesis.audioData());
                }
                long ttsDone = System.nanoTime();

                send(new AvatarMessage.Timing(
                        (cleanupDone - t0) / 1_000_000,
                        (llmDone - cleanupDone) / 1_000_000,
                        (ttsDone - llmDone) / 1_000_000,
                        (ttsDone - t0) / 1_000_000));
            }
        } catch (Exception e) {
            send(new AvatarMessage.Error(e.getMessage()));
        }
    }

    private TextToSpeechService resolveTts(@Nullable String modelKey) {
        if (modelKey != null && ttsModels.containsKey(modelKey)) {
            return ttsModels.get(modelKey);
        }
        return ttsService;
    }

    private void startPolling() {
        polling       = true;
        pollingThread = Thread.ofVirtual().name("stt-partial-poll").start(() -> {
            String lastPartial = "";
            while (polling && stream != null) {
                try {
                    String partial = stream.partialResult();
                    if (partial != null && !partial.isEmpty() && !partial.equals(lastPartial)) {
                        lastPartial = partial;
                        send(new AvatarMessage.Partial(partial));
                    }
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    private void stopPolling() {
        polling = false;
        if (pollingThread != null) {
            pollingThread.interrupt();
            try {
                pollingThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            pollingThread = null;
        }
    }

    private @Nullable String generateResponse(String userMessage, @Nullable String llmModel) {
        if (responseGenerator == null) {
            send(new AvatarMessage.Error("No LLM provider configured"));
            return null;
        }
        AssembledPrompt prompt = promptAssembler.assemble(userMessage, List.copyOf(history));
        if (llmModel != null) {
            prompt = new AssembledPrompt(prompt.systemPrompt(), prompt.userPrompt(), llmModel);
        }
        return responseGenerator.apply(prompt);
    }

    private void send(AvatarMessage msg) {
        textSink.accept(MessageCodec.encode(msg));
    }

    public void close() {
        stopPolling();
        if (stream != null) {
            stream.close();
            stream = null;
        }
    }
}
