package io.casehub.blocks.speech.ws;

import io.casehub.blocks.speech.CleanupConfig;
import io.casehub.blocks.speech.RecognitionStream;
import io.casehub.blocks.speech.StreamingSpeechToTextService;
import io.casehub.blocks.speech.SynthesisOptions;
import io.casehub.blocks.speech.SynthesisResult;
import io.casehub.blocks.speech.TextToSpeechService;
import io.casehub.blocks.speech.SpeakerEmbedding;
import io.casehub.blocks.speech.SpeakerEmbeddingExtractor;
import io.casehub.blocks.speech.SpeakerRegistry;
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
    @FunctionalInterface
    public interface StreamingResponseGenerator {
        String generate(AssembledPrompt prompt, java.util.function.Consumer<String> onSentence);
    }

    private static final System.Logger LOG = System.getLogger("speech-session");


    private final           StreamingSpeechToTextService               sttService;
    private final           TextToSpeechService                        ttsService;
    private final           CleanupConfig                              cleanupConfig;
    private final @Nullable Function<AssembledPrompt, String>          responseGenerator;
    private final @Nullable StreamingResponseGenerator                 streamingGenerator;
    private final           PromptAssembler                            promptAssembler;
    private final           Consumer<String>                           textSink;
    private final           Consumer<byte[]>                           binarySink;
    private final           List<ConversationTurn>                     history = new ArrayList<>();
    private final           java.util.Map<String, TextToSpeechService> ttsModels;
    private final java.util.function.UnaryOperator<String>   corrector;
    private final java.util.function.Consumer<String>        onResponse;
    private final java.util.function.Supplier<String>        vocabularyHintSupplier;


    private @Nullable RecognitionStream stream;
    private           int               sampleRate = 16000;
    private volatile  boolean           polling;
    private @Nullable Thread            pollingThread;
    private @Nullable String            activeLlmModel;
    private @Nullable String            activeTtsModel;
    private static final int            RING_BUFFER_SECONDS       = 5;
    private static final int            RING_BUFFER_SIZE          = 16000 * RING_BUFFER_SECONDS;
    private static final int            MIN_SAMPLES_FOR_EMBEDDING = 16000 * 3 / 2;

    private @Nullable SpeakerEmbeddingExtractor embeddingExtractor;
    private @Nullable SpeakerRegistry           speakerRegistry;
    private final     float[]                   ringBuffer    = new float[RING_BUFFER_SIZE];
    private           int                       ringBufferPos = 0;
    private @Nullable SpeakerEmbedding          pendingEmbedding;
    private           boolean                   enrollmentPending;
    private @Nullable String                    explicitEnrollmentName;


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
        this(sttService, ttsService, cleanupConfig, responseGenerator, promptAssembler,
             textSink, binarySink, ttsModels, null, null, null);
    }

    public SpeechSession(StreamingSpeechToTextService sttService,
                         TextToSpeechService ttsService,
                         CleanupConfig cleanupConfig,
                         @Nullable Function<AssembledPrompt, String> responseGenerator,
                         PromptAssembler promptAssembler,
                         Consumer<String> textSink,
                         Consumer<byte[]> binarySink,
                         java.util.Map<String, TextToSpeechService> ttsModels,
                         java.util.function.UnaryOperator<String> corrector,
                         java.util.function.Consumer<String> onResponse,
                         java.util.function.Supplier<String> vocabularyHintSupplier) {
        this(sttService, ttsService, cleanupConfig, responseGenerator, null,
             promptAssembler, textSink, binarySink, ttsModels, corrector, onResponse, vocabularyHintSupplier);
    }

    public SpeechSession(StreamingSpeechToTextService sttService,
                         TextToSpeechService ttsService,
                         CleanupConfig cleanupConfig,
                         @Nullable Function<AssembledPrompt, String> responseGenerator,
                         @Nullable StreamingResponseGenerator streamingGenerator,
                         PromptAssembler promptAssembler,
                         Consumer<String> textSink,
                         Consumer<byte[]> binarySink,
                         java.util.Map<String, TextToSpeechService> ttsModels,
                         java.util.function.UnaryOperator<String> corrector,
                         java.util.function.Consumer<String> onResponse,
                         java.util.function.Supplier<String> vocabularyHintSupplier) {
        this.sttService             = sttService;
        this.ttsService             = ttsService;
        this.cleanupConfig          = cleanupConfig;
        this.responseGenerator      = responseGenerator;
        this.streamingGenerator     = streamingGenerator;
        this.promptAssembler        = promptAssembler;
        this.textSink               = textSink;
        this.binarySink             = binarySink;
        this.ttsModels              = ttsModels;
        this.corrector              = corrector;
        this.onResponse             = onResponse;
        this.vocabularyHintSupplier = vocabularyHintSupplier;
    }


    public void handleStart(AvatarMessage.Start msg) {
        stopPolling();
        if (stream != null) {
            stream.close();
        }
        this.sampleRate     = msg.sampleRate();
        this.activeLlmModel = msg.llmModel();
        this.activeTtsModel = msg.ttsModel();
        var opts = io.casehub.blocks.speech.TranscriptionOptions.defaults();
        if (vocabularyHintSupplier != null) {
            String hint = vocabularyHintSupplier.get();
            if (hint != null && !hint.isEmpty()) {
                opts = opts.withVocabularyHint(hint);
            }
        }
        stream = sttService.startStream(opts);
        startPolling();}

    public SpeechSession withSpeakerServices(
            @Nullable SpeakerEmbeddingExtractor embeddingExtractor,
            @Nullable SpeakerRegistry speakerRegistry) {
        this.embeddingExtractor = embeddingExtractor;
        this.speakerRegistry    = speakerRegistry;
        return this;
    }


    public void handleAudio(float[] samples) {
        if (stream != null) {
            stream.acceptSamples(samples, sampleRate);
        }
        if (embeddingExtractor != null && ringBufferPos < RING_BUFFER_SIZE) {
            int toCopy = Math.min(samples.length, RING_BUFFER_SIZE - ringBufferPos);
            System.arraycopy(samples, 0, ringBuffer, ringBufferPos, toCopy);
            ringBufferPos += toCopy;
        }
    }

    public void handleStop() {
        stopPolling();
        if (stream == null) {
            send(new AvatarMessage.Error("No active recording session"));
            return;
        }
        try {
            long t0 = System.nanoTime();

            LOG.log(System.Logger.Level.INFO, "[STT] finalResult() called");
            TranscriptionResult result = stream.finalResult();
            stream.close();
            stream = null;
            long sttDone = System.nanoTime();

            String raw = result.text();
            LOG.log(System.Logger.Level.INFO, "[STT] raw: \"{0}\"", raw);

            String cleanText   = ensureTrailingPunctuation(cleanupConfig.apply(raw));
            long   cleanupDone = System.nanoTime();
            LOG.log(System.Logger.Level.INFO, "[STT] after cleanup ({0} filters): \"{1}\"",
                    cleanupConfig.filters().size(), cleanText);
            send(new AvatarMessage.Transcript(cleanText));

            String speakerLabel = identifySpeaker();
            history.add(new ConversationTurn("user", cleanText, speakerLabel));

            TextToSpeechService tts           = resolveTts(activeTtsModel);
            int[]               sentenceCount = {0};

            if (streamingGenerator != null) {
                AssembledPrompt prompt = promptAssembler.assemble(cleanText, List.copyOf(history));
                if (activeLlmModel != null) {
                    prompt = new AssembledPrompt(prompt.systemPrompt(), prompt.userPrompt(), activeLlmModel);
                }
                String responseText = streamingGenerator.generate(prompt, sentence -> {
                    sentenceCount[0]++;
                    synthesiseAndSend(tts, sentence, sentenceCount[0]);
                });
                long ttsDone = System.nanoTime();

                send(new AvatarMessage.Response(responseText));
                history.add(new ConversationTurn("assistant", responseText));
                if (onResponse != null) {onResponse.accept(responseText);}

                long sttMs      = (sttDone - t0) / 1_000_000;
                long cleanupMs  = (cleanupDone - sttDone) / 1_000_000;
                long pipelineMs = (ttsDone - cleanupDone) / 1_000_000;
                long totalMs    = (ttsDone - t0) / 1_000_000;

                LOG.log(System.Logger.Level.INFO, "[TIMING] STT: {0}ms | Cleanup: {1}ms | LLM→TTS(streamed): {2}ms | sentences: {3} | Total: {4}ms",
                        sttMs, cleanupMs, pipelineMs, sentenceCount[0], totalMs);
                send(new AvatarMessage.Timing(cleanupMs, pipelineMs, 0, totalMs));
            } else {
                String responseText = generateResponse(cleanText, activeLlmModel);
                long   llmDone      = System.nanoTime();

                if (responseText != null) {
                    send(new AvatarMessage.Response(responseText));
                    history.add(new ConversationTurn("assistant", responseText));
                    if (onResponse != null) {onResponse.accept(responseText);}

                    String[] sentences = responseText.split("(?<=[.!?])\\s+");
                    for (int i = 0; i < sentences.length; i++) {
                        if (sentences[i].isBlank()) {continue;}
                        sentenceCount[0]++;
                        synthesiseAndSend(tts, sentences[i], sentenceCount[0]);
                    }
                    long ttsDone = System.nanoTime();

                    long sttMs     = (sttDone - t0) / 1_000_000;
                    long cleanupMs = (cleanupDone - sttDone) / 1_000_000;
                    long llmMs     = (llmDone - cleanupDone) / 1_000_000;
                    long ttsMs     = (ttsDone - llmDone) / 1_000_000;
                    long totalMs   = (ttsDone - t0) / 1_000_000;

                    LOG.log(System.Logger.Level.INFO, "[TIMING] STT: {0}ms | Cleanup: {1}ms | LLM: {2}ms | TTS: {3}ms | sentences: {4} | Total: {5}ms",
                            sttMs, cleanupMs, llmMs, ttsMs, sentenceCount[0], totalMs);
                    send(new AvatarMessage.Timing(cleanupMs, llmMs, ttsMs, totalMs));
                }
            }
        } catch (Exception e) {
            send(new AvatarMessage.Error(e.getMessage()));
        }}


    private @Nullable String identifySpeaker() {
        if (embeddingExtractor == null || speakerRegistry == null || ringBufferPos < MIN_SAMPLES_FOR_EMBEDDING) {
            ringBufferPos = 0;
            return null;
        }
        try {
            float[] audioForEmbedding = java.util.Arrays.copyOf(ringBuffer, ringBufferPos);
            var     embedding         = embeddingExtractor.extract(audioForEmbedding, 16000);

            if (explicitEnrollmentName != null) {
                speakerRegistry.register(explicitEnrollmentName, embedding);
                send(new AvatarMessage.SpeakerIdentified(explicitEnrollmentName, 1.0));
                String name = explicitEnrollmentName;
                explicitEnrollmentName = null;
                ringBufferPos          = 0;
                return name;
            }

            var match = speakerRegistry.identify(embedding, 0.7);
            if (match.isPresent()) {
                send(new AvatarMessage.SpeakerIdentified(match.get().name(), match.get().confidence()));
                ringBufferPos = 0;
                return match.get().name();
            }

            pendingEmbedding = embedding;
            if (!enrollmentPending) {
                enrollmentPending = true;
                send(new AvatarMessage.SpeakerPrompt("I don't recognise your voice — what's your name?"));
            }
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "Speaker identification failed: " + e.getMessage());
        }
        ringBufferPos = 0;
        return null;
    }

    public void handleSpeakerIdentify(String name) {
        if (pendingEmbedding != null && speakerRegistry != null) {
            speakerRegistry.register(name, pendingEmbedding);
            send(new AvatarMessage.SpeakerIdentified(name, 1.0));
            pendingEmbedding  = null;
            enrollmentPending = false;
        } else if (explicitEnrollmentName == null) {
            explicitEnrollmentName = name;
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

            TextToSpeechService tts           = resolveTts(ttsModel);
            int[]               sentenceCount = {0};

            if (streamingGenerator != null) {
                AssembledPrompt prompt = promptAssembler.assemble(cleanText, List.copyOf(history));
                if (llmModel != null) {
                    prompt = new AssembledPrompt(prompt.systemPrompt(), prompt.userPrompt(), llmModel);
                }
                long[] llmDoneRef = {0};
                String responseText = streamingGenerator.generate(prompt, sentence -> {
                    if (llmDoneRef[0] == 0) {
                        llmDoneRef[0] = System.nanoTime();
                    }
                    sentenceCount[0]++;
                    synthesiseAndSend(tts, sentence, sentenceCount[0]);
                });
                long llmDone = llmDoneRef[0] > 0 ? llmDoneRef[0] : System.nanoTime();
                long ttsDone = System.nanoTime();

                send(new AvatarMessage.Response(responseText));
                history.add(new ConversationTurn("assistant", responseText));
                if (onResponse != null) {onResponse.accept(responseText);}

                logTiming("Cleanup", cleanupDone - t0, "LLM→TTS(streamed)", ttsDone - cleanupDone, sentenceCount[0], ttsDone - t0);
                send(new AvatarMessage.Timing(
                        (cleanupDone - t0) / 1_000_000,
                        (llmDone - cleanupDone) / 1_000_000,
                        (ttsDone - llmDone) / 1_000_000,
                        (ttsDone - t0) / 1_000_000));
            } else {
                String responseText = generateResponse(cleanText, llmModel);
                long   llmDone      = System.nanoTime();

                if (responseText != null) {
                    send(new AvatarMessage.Response(responseText));
                    history.add(new ConversationTurn("assistant", responseText));
                    if (onResponse != null) {onResponse.accept(responseText);}

                    String[] sentences = responseText.split("(?<=[.!?])\\s+");
                    for (int i = 0; i < sentences.length; i++) {
                        if (sentences[i].isBlank()) {continue;}
                        sentenceCount[0]++;
                        synthesiseAndSend(tts, sentences[i], sentenceCount[0]);
                    }
                    long ttsDone = System.nanoTime();

                    logTiming("Cleanup", cleanupDone - t0, "LLM", llmDone - cleanupDone, "TTS", ttsDone - llmDone, sentenceCount[0], ttsDone - t0);
                    send(new AvatarMessage.Timing(
                            (cleanupDone - t0) / 1_000_000,
                            (llmDone - cleanupDone) / 1_000_000,
                            (ttsDone - llmDone) / 1_000_000,
                            (ttsDone - t0) / 1_000_000));
                }
            }
        } catch (Exception e) {
            send(new AvatarMessage.Error(e.getMessage()));
        }}


    private static String ensureTrailingPunctuation(String text) {
        if (text.isEmpty()) {return text;}
        char last = text.charAt(text.length() - 1);
        if (last == '.' || last == '!' || last == '?' || last == '"' || last == '\'' || last == ')') {
            return text;
        }
        return text + ".";
    }

    private void synthesiseAndSend(TextToSpeechService tts, String sentence, int index) {
        long            start     = System.nanoTime();
        SynthesisResult synthesis = tts.synthesise(sentence, new SynthesisOptions(null, null, "wav", true));
        long            elapsed   = (System.nanoTime() - start) / 1_000_000;
        LOG.log(System.Logger.Level.INFO, "[TTS] sentence {0}: {1}ms — \"{2}\"",
                index, elapsed,
                sentence.length() > 60 ? sentence.substring(0, 60) + "..." : sentence);
        var visemeFrames = VisemeMapping.convert(synthesis.phonemes());
        send(new AvatarMessage.Phonemes(visemeFrames));
        binarySink.accept(synthesis.audioData());
    }

    private void logTiming(String label1, long ns1, String label2, long ns2, int sentences, long totalNs) {
        LOG.log(System.Logger.Level.INFO, "[TIMING] {0}: {1}ms | {2}: {3}ms | sentences: {4} | Total: {5}ms",
                label1, ns1 / 1_000_000, label2, ns2 / 1_000_000, sentences, totalNs / 1_000_000);
    }

    private void logTiming(String l1, long ns1, String l2, long ns2, String l3, long ns3, int sentences, long totalNs) {
        LOG.log(System.Logger.Level.INFO, "[TIMING] {0}: {1}ms | {2}: {3}ms | {4}: {5}ms | sentences: {6} | Total: {7}ms",
                l1, ns1 / 1_000_000, l2, ns2 / 1_000_000, l3, ns3 / 1_000_000, sentences, totalNs / 1_000_000);
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
