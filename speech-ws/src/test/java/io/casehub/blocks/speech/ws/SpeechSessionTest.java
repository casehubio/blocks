package io.casehub.blocks.speech.ws;

import io.casehub.blocks.speech.CleanupConfig;
import io.casehub.blocks.speech.PhonemeTiming;
import io.casehub.blocks.speech.RecognitionStream;
import io.casehub.blocks.speech.StreamingSpeechToTextService;
import io.casehub.blocks.speech.SynthesisOptions;
import io.casehub.blocks.speech.SynthesisResult;
import io.casehub.blocks.speech.TextToSpeechService;
import io.casehub.blocks.speech.TranscriptionResult;
import io.casehub.blocks.speech.ws.protocol.AvatarMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpeechSessionTest {

    private StreamingSpeechToTextService sttService;
    private TextToSpeechService          ttsService;
    private CleanupConfig                cleanupConfig;
    private RecognitionStream            recognitionStream;
    private List<String>                 sentTextMessages;
    private List<byte[]>                 sentBinaryMessages;
    private SpeechSession                session;

    @BeforeEach
    void setUp() {
        sttService        = mock(StreamingSpeechToTextService.class);
        ttsService        = mock(TextToSpeechService.class);
        cleanupConfig     = mock(CleanupConfig.class);
        recognitionStream = mock(RecognitionStream.class);

        when(sttService.startStream(any())).thenReturn(recognitionStream);
        when(cleanupConfig.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        when(recognitionStream.partialResult()).thenReturn("");

        sentTextMessages   = new ArrayList<>();
        sentBinaryMessages = new ArrayList<>();
        session            = new SpeechSession(
                sttService, ttsService, cleanupConfig,
                null,
                new DefaultPromptAssembler(null),
                sentTextMessages::add,
                sentBinaryMessages::add);
    }

    @Test
    void startCreatesRecognitionStream() {
        session.handleStart(new AvatarMessage.Start(16000));
        verify(sttService).startStream(any());
        session.close();
    }

    @Test
    void audioSamplesAreFedToRecognitionStream() {
        session.handleStart(new AvatarMessage.Start(16000));
        float[] samples = {0.1f, 0.2f, 0.3f};
        session.handleAudio(samples);
        verify(recognitionStream).acceptSamples(samples, 16000);
        session.close();
    }

    @Test
    void audioWithoutStartIsIgnored() {
        float[] samples = {0.1f, 0.2f};
        session.handleAudio(samples);
        verify(recognitionStream, never()).acceptSamples(any(), anyInt());
    }

    @Test
    void stopFinalizesAndSendsTranscript() {
        when(recognitionStream.finalResult()).thenReturn(
                new TranscriptionResult("hello world", "en", 0.95));

        session.handleStart(new AvatarMessage.Start(16000));
        session.handleStop();

        verify(recognitionStream).finalResult();
        verify(recognitionStream).close();
        assertThat(sentTextMessages).anySatisfy(json -> {
            assertThat(json).contains("\"type\":\"transcript\"");
            assertThat(json).contains("hello world");
        });
    }

    @Test
    void stopAppliesCleanupPipeline() {
        when(recognitionStream.finalResult()).thenReturn(
                new TranscriptionResult("um hello world", "en", 0.9));
        when(cleanupConfig.apply("um hello world")).thenReturn("Hello world.");

        session.handleStart(new AvatarMessage.Start(16000));
        session.handleStop();

        assertThat(sentTextMessages).anySatisfy(json -> {
            assertThat(json).contains("\"type\":\"transcript\"");
            assertThat(json).contains("Hello world.");
        });
    }

    @Test
    void stopAppliesRealCleanupFiltersToSttOutput() {
        when(recognitionStream.finalResult()).thenReturn(
                new TranscriptionResult("UM HELLO WORLD UH THIS IS A TEST", "en", 0.9));

        io.casehub.blocks.speech.TextFilter lowercaseFilter = new io.casehub.blocks.speech.TextFilter() {
            public String apply(String t) {return t.toLowerCase();}

            public String name()          {return "lowercase";}

            public int destructiveness()  {return 0;}
        };
        io.casehub.blocks.speech.TextFilter fillerFilter = new io.casehub.blocks.speech.TextFilter() {
            public String apply(String t) {return t.replaceAll("(?i)\\bum\\b|\\buh\\b", "").replaceAll("\\s+", " ").trim();}

            public String name()          {return "filler";}

            public int destructiveness()  {return 1;}
        };
        var realCleanup = io.casehub.blocks.speech.CleanupConfig.of(lowercaseFilter, fillerFilter);

        session = new SpeechSession(
                sttService, ttsService, realCleanup,
                null,
                new DefaultPromptAssembler(null),
                sentTextMessages::add,
                sentBinaryMessages::add);

        session.handleStart(new AvatarMessage.Start(16000));
        session.handleStop();

        assertThat(sentTextMessages).anySatisfy(json -> {
            assertThat(json).contains("\"type\":\"transcript\"");
            assertThat(json).contains("hello world this is a test");
        });
    }


    @Test
    void stopWithResponseGeneratorProducesFullPipeline() {
        when(recognitionStream.finalResult()).thenReturn(
                new TranscriptionResult("hello", "en", 0.9));
        var phonemes = List.of(
                new PhonemeTiming("h", 0, 50),
                new PhonemeTiming("ɛ", 50, 120));
        when(ttsService.synthesise(any(), any())).thenReturn(
                new SynthesisResult(new byte[]{1, 2, 3}, "wav", phonemes));

        session = new SpeechSession(
                sttService, ttsService, cleanupConfig,
                assembled -> "I hear you.",
                new DefaultPromptAssembler(null),
                sentTextMessages::add,
                sentBinaryMessages::add);

        session.handleStart(new AvatarMessage.Start(16000));
        session.handleStop();

        assertThat(sentTextMessages).anySatisfy(json ->
                                                        assertThat(json).contains("\"type\":\"transcript\""));
        assertThat(sentTextMessages).anySatisfy(json ->
                                                        assertThat(json).contains("\"type\":\"response\""));
        assertThat(sentTextMessages).anySatisfy(json ->
                                                        assertThat(json).contains("\"type\":\"phonemes\""));
        assertThat(sentBinaryMessages).hasSize(1);
        assertThat(sentBinaryMessages.get(0)).containsExactly(1, 2, 3);
    }

    @Test
    void stopWithoutResponseGeneratorSendsError() {
        when(recognitionStream.finalResult()).thenReturn(
                new TranscriptionResult("hello", "en", 0.9));

        session.handleStart(new AvatarMessage.Start(16000));
        session.handleStop();

        assertThat(sentTextMessages).anySatisfy(json ->
                                                        assertThat(json).contains("\"type\":\"error\""));
        assertThat(sentBinaryMessages).isEmpty();
    }

    @Test
    void stopWithoutStartSendsError() {
        session.handleStop();
        assertThat(sentTextMessages).anySatisfy(json ->
                                                        assertThat(json).contains("\"type\":\"error\""));
    }

    @Test
    void ttsRequestsPhonemes() {
        when(recognitionStream.finalResult()).thenReturn(
                new TranscriptionResult("test", "en", 0.9));
        when(ttsService.synthesise(any(), any())).thenReturn(
                new SynthesisResult(new byte[]{}, "wav", List.of()));

        session = new SpeechSession(
                sttService, ttsService, cleanupConfig,
                assembled -> "reply",
                new DefaultPromptAssembler(null),
                sentTextMessages::add,
                sentBinaryMessages::add);

        session.handleStart(new AvatarMessage.Start(16000));
        session.handleStop();

        var captor = org.mockito.ArgumentCaptor.forClass(SynthesisOptions.class);
        verify(ttsService).synthesise(eq("reply"), captor.capture());
        assertThat(captor.getValue().includePhonemes()).isTrue();
    }

    @Test
    void closeReleasesRecognitionStream() {
        session.handleStart(new AvatarMessage.Start(16000));
        session.close();
        verify(recognitionStream).close();
    }

    @Test
    void closeWithoutStartIsHarmless() {
        session.close();
    }

    // --- Partial streaming tests ---

    @Test
    void startPollingSendsPartialMessages() throws InterruptedException {
        var callCount = new java.util.concurrent.atomic.AtomicInteger(0);
        when(recognitionStream.partialResult()).thenAnswer(inv -> {
            int n = callCount.incrementAndGet();
            if (n <= 2) {return "";}
            if (n <= 4) {return "hello";}
            return "hello world";
        });

        session.handleStart(new AvatarMessage.Start(16000));
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (callCount.get() < 6 && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        session.handleStop();

        assertThat(sentTextMessages).anySatisfy(json -> {
            assertThat(json).contains("\"type\":\"partial\"");
            assertThat(json).contains("hello");
        });
    }

    @Test
    void partialDeduplicatesUnchangedText() throws InterruptedException {
        var callCount = new java.util.concurrent.atomic.AtomicInteger(0);
        when(recognitionStream.partialResult()).thenAnswer(inv -> {
            callCount.incrementAndGet();
            return "same text";
        });

        session.handleStart(new AvatarMessage.Start(16000));
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (callCount.get() < 5 && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        session.handleStop();

        long partialCount = sentTextMessages.stream()
                                            .filter(json -> json.contains("\"type\":\"partial\""))
                                            .count();
        assertThat(partialCount).isEqualTo(1);
    }

    @Test
    void stopStopsPolling() throws InterruptedException {
        when(recognitionStream.partialResult()).thenReturn("");
        when(recognitionStream.finalResult()).thenReturn(
                new TranscriptionResult("done", "en", 0.9));

        session.handleStart(new AvatarMessage.Start(16000));
        Thread.sleep(150);
        session.handleStop();
        Thread.sleep(200);

        int messagesAfterStop = sentTextMessages.size();
        Thread.sleep(300);
        assertThat(sentTextMessages).hasSize(messagesAfterStop);
    }

    // --- sampleRate tests ---

    @Test
    void handleAudioUsesClientDeclaredSampleRate() {
        session.handleStart(new AvatarMessage.Start(48000));
        float[] samples = {0.5f};
        session.handleAudio(samples);
        verify(recognitionStream).acceptSamples(samples, 48000);
        session.close();
    }

    @Test
    void responseGeneratorReceivesAssembledPrompt() {
        when(recognitionStream.finalResult()).thenReturn(
                new TranscriptionResult("test input", "en", 0.9));
        when(ttsService.synthesise(any(), any())).thenReturn(
                new SynthesisResult(new byte[]{}, "wav", List.of()));

        var capturedPrompt = new AssembledPrompt[1];
        session = new SpeechSession(
                sttService, ttsService, cleanupConfig,
                assembled -> {
                    capturedPrompt[0] = assembled;
                    return "reply";
                },
                new DefaultPromptAssembler("Custom system prompt"),
                sentTextMessages::add,
                sentBinaryMessages::add);

        session.handleStart(new AvatarMessage.Start(16000));
        session.handleStop();

        assertThat(capturedPrompt[0]).isNotNull();
        assertThat(capturedPrompt[0].systemPrompt()).isEqualTo("Custom system prompt");
        assertThat(capturedPrompt[0].userPrompt()).contains("test input");
    }

    @Test
    void handleTextBypassesSttAndProducesFullPipeline() {
        var phonemes = List.of(new PhonemeTiming("h", 0, 50));
        when(ttsService.synthesise(any(), any())).thenReturn(
                new SynthesisResult(new byte[]{1, 2}, "wav", phonemes));

        session = new SpeechSession(
                sttService, ttsService, cleanupConfig,
                assembled -> "LLM says hello.",
                new DefaultPromptAssembler(null),
                sentTextMessages::add,
                sentBinaryMessages::add);

        session.handleText("Hi there");

        verify(sttService, never()).startStream(any());
        assertThat(sentTextMessages).anySatisfy(json ->
                                                        assertThat(json).contains("\"type\":\"transcript\""));
        assertThat(sentTextMessages).anySatisfy(json -> {
            assertThat(json).contains("\"type\":\"response\"");
            assertThat(json).contains("LLM says hello.");
        });
        assertThat(sentTextMessages).anySatisfy(json ->
                                                        assertThat(json).contains("\"type\":\"phonemes\""));
        assertThat(sentBinaryMessages).hasSize(1);
    }

    @Test
    void handleTextWithoutResponseGeneratorSendsError() {
        session.handleText("Hi");

        assertThat(sentTextMessages).anySatisfy(json ->
                                                        assertThat(json).contains("\"type\":\"error\""));
        assertThat(sentBinaryMessages).isEmpty();
    }

    @Test
    void handleTextUsesNamedTtsModelFromRegistry() {
        var altTts = mock(TextToSpeechService.class);
        when(altTts.synthesise(any(), any())).thenReturn(
                new SynthesisResult(new byte[]{9, 8}, "wav", List.of()));

        session = new SpeechSession(
                sttService, ttsService, cleanupConfig,
                assembled -> "response",
                new DefaultPromptAssembler(null),
                sentTextMessages::add,
                sentBinaryMessages::add,
                java.util.Map.of("sherpa:lessac-medium", altTts));

        session.handleText("Hello", null, "sherpa:lessac-medium");

        verify(altTts, atLeastOnce()).synthesise(any(), any());
        verify(ttsService, never()).synthesise(any(), any());
        assertThat(sentBinaryMessages).isNotEmpty();
    }

    @Test
    void handleTextFallsBackToDefaultTtsForUnknownModel() {
        var altTts = mock(TextToSpeechService.class);
        when(ttsService.synthesise(any(), any())).thenReturn(
                new SynthesisResult(new byte[]{1}, "wav", List.of()));

        session = new SpeechSession(
                sttService, ttsService, cleanupConfig,
                assembled -> "response",
                new DefaultPromptAssembler(null),
                sentTextMessages::add,
                sentBinaryMessages::add,
                java.util.Map.of("sherpa:lessac-medium", altTts));

        session.handleText("Hello", null, "unknown-model");

        verify(ttsService, atLeastOnce()).synthesise(any(), any());
        verify(altTts, never()).synthesise(any(), any());
    }

    @Test
    void handleTextFallsBackToDefaultTtsForNullModel() {
        var altTts = mock(TextToSpeechService.class);
        when(ttsService.synthesise(any(), any())).thenReturn(
                new SynthesisResult(new byte[]{1}, "wav", List.of()));

        session = new SpeechSession(
                sttService, ttsService, cleanupConfig,
                assembled -> "response",
                new DefaultPromptAssembler(null),
                sentTextMessages::add,
                sentBinaryMessages::add,
                java.util.Map.of("sherpa:lessac-medium", altTts));

        session.handleText("Hello", null, null);

        verify(ttsService, atLeastOnce()).synthesise(any(), any());
        verify(altTts, never()).synthesise(any(), any());
    }


// --- Correction pipeline regression tests ---

    @Test
    void handleTextAppliesCorrectorBeforeCleanup() {
        var corrected = new ArrayList<String>();
        session = new SpeechSession(
                sttService, ttsService, cleanupConfig,
                null, new DefaultPromptAssembler(null),
                sentTextMessages::add, sentBinaryMessages::add, java.util.Map.of(),
                text -> {
                    corrected.add(text);
                    return text.replace("limaric", "limerick");
                },
                null, null);

        session.handleText("tell me a limaric");

        assertThat(corrected).as("corrector should not be called for typed text").isEmpty();
        verify(cleanupConfig).apply("tell me a limaric");
    }

    @Test
    void handleStopAppliesCorrectorToSttOutput() {
        when(recognitionStream.finalResult()).thenReturn(
                new TranscriptionResult("tell me a limaric", "en", 0.9));

        var corrected = new ArrayList<String>();
        session = new SpeechSession(
                sttService, ttsService, cleanupConfig,
                null, new DefaultPromptAssembler(null),
                sentTextMessages::add, sentBinaryMessages::add, java.util.Map.of(),
                text -> {
                    corrected.add(text);
                    return text.replace("limaric", "limerick");
                },
                null, null);

        session.handleStart(new AvatarMessage.Start(16000));
        session.handleStop();

        assertThat(corrected).containsExactly("tell me a limaric");
        verify(cleanupConfig).apply("tell me a limerick");
    }

    @Test
    void onResponseCalledAfterLlmResponse() {
        var responses = new ArrayList<String>();
        session = new SpeechSession(
                sttService, ttsService, cleanupConfig,
                prompt -> "I am a helpful avatar.",
                new DefaultPromptAssembler(null),
                sentTextMessages::add, sentBinaryMessages::add, java.util.Map.of(),
                null,
                responses::add,
                null);

        when(ttsService.synthesise(any(), any())).thenReturn(
                new SynthesisResult(new byte[44], "en", List.of()));

        session.handleText("hello");

        assertThat(responses).containsExactly("I am a helpful avatar.");
    }

    @Test
    void vocabularyHintPassedToStartStream() {
        session = new SpeechSession(
                sttService, ttsService, cleanupConfig,
                null, new DefaultPromptAssembler(null),
                sentTextMessages::add, sentBinaryMessages::add, java.util.Map.of(),
                null, null,
                () -> "limerick poetry nantucket");

        session.handleStart(new AvatarMessage.Start(16000));

        var captor = org.mockito.ArgumentCaptor.forClass(
                io.casehub.blocks.speech.TranscriptionOptions.class);
        verify(sttService).startStream(captor.capture());
        assertThat(captor.getValue().vocabularyHint()).isEqualTo("limerick poetry nantucket");
        session.close();
    }

    @Test
    void nullCorrectorLeavesTextUnchanged() {
        session = new SpeechSession(
                sttService, ttsService, cleanupConfig,
                null, new DefaultPromptAssembler(null),
                sentTextMessages::add, sentBinaryMessages::add, java.util.Map.of(),
                null, null, null);

        session.handleText("limaric");

        verify(cleanupConfig).apply("limaric");
    }


// --- Streaming generator path tests (the path the demo actually uses) ---

    @Test
    void stopWithStreamingGeneratorSendsTranscriptAndResponse() {
        when(recognitionStream.finalResult()).thenReturn(
                new TranscriptionResult("hello streaming", "en", 0.9));
        when(ttsService.synthesise(any(), any())).thenReturn(
                new SynthesisResult(new byte[]{1, 2, 3}, "wav", List.of()));

        SpeechSession.StreamingResponseGenerator streamGen = (prompt, onSentence) -> {
            onSentence.accept("Streamed reply.");
            return "Streamed reply.";
        };

        session = new SpeechSession(
                sttService, ttsService, cleanupConfig,
                null,
                streamGen,
                new DefaultPromptAssembler(null),
                sentTextMessages::add,
                sentBinaryMessages::add,
                java.util.Map.of(),
                null, null, null);

        session.handleStart(new AvatarMessage.Start(16000));
        session.handleStop();

        assertThat(sentTextMessages).anySatisfy(json ->
                                                        assertThat(json).contains("\"type\":\"transcript\""));
        assertThat(sentTextMessages).anySatisfy(json -> {
            assertThat(json).contains("\"type\":\"response\"");
            assertThat(json).contains("Streamed reply.");
        });
        assertThat(sentBinaryMessages).isNotEmpty();
    }

    @Test
    void stopWithStreamingGeneratorSendsTimingMessage() {
        when(recognitionStream.finalResult()).thenReturn(
                new TranscriptionResult("timing test", "en", 0.9));
        when(ttsService.synthesise(any(), any())).thenReturn(
                new SynthesisResult(new byte[]{1}, "wav", List.of()));

        SpeechSession.StreamingResponseGenerator streamGen = (prompt, onSentence) -> {
            onSentence.accept("Reply.");
            return "Reply.";
        };

        session = new SpeechSession(
                sttService, ttsService, cleanupConfig,
                null,
                streamGen,
                new DefaultPromptAssembler(null),
                sentTextMessages::add,
                sentBinaryMessages::add,
                java.util.Map.of(),
                null, null, null);

        session.handleStart(new AvatarMessage.Start(16000));
        session.handleStop();

        assertThat(sentTextMessages).anySatisfy(json ->
                                                        assertThat(json).contains("\"type\":\"timing\""));
    }

    @Test
    void stopWithStreamingGeneratorPrefersStreamingOverNonStreaming() {
        when(recognitionStream.finalResult()).thenReturn(
                new TranscriptionResult("which path", "en", 0.9));
        when(ttsService.synthesise(any(), any())).thenReturn(
                new SynthesisResult(new byte[]{1}, "wav", List.of()));

        var streamingCalled    = new java.util.concurrent.atomic.AtomicBoolean(false);
        var nonStreamingCalled = new java.util.concurrent.atomic.AtomicBoolean(false);

        SpeechSession.StreamingResponseGenerator streamGen = (prompt, onSentence) -> {
            streamingCalled.set(true);
            onSentence.accept("Streamed.");
            return "Streamed.";
        };
        java.util.function.Function<AssembledPrompt, String> nonStreamGen = prompt -> {
            nonStreamingCalled.set(true);
            return "Non-streamed.";
        };

        session = new SpeechSession(
                sttService, ttsService, cleanupConfig,
                nonStreamGen,
                streamGen,
                new DefaultPromptAssembler(null),
                sentTextMessages::add,
                sentBinaryMessages::add,
                java.util.Map.of(),
                null, null, null);

        session.handleStart(new AvatarMessage.Start(16000));
        session.handleStop();

        assertThat(streamingCalled.get()).as("streaming generator should be used").isTrue();
        assertThat(nonStreamingCalled.get()).as("non-streaming generator should NOT be used").isFalse();
    }

    @Test
    void stopWithStreamingGeneratorSynthesisesEachSentence() {
        when(recognitionStream.finalResult()).thenReturn(
                new TranscriptionResult("multi sentence", "en", 0.9));
        when(ttsService.synthesise(any(), any())).thenReturn(
                new SynthesisResult(new byte[]{1}, "wav", List.of()));

        SpeechSession.StreamingResponseGenerator streamGen = (prompt, onSentence) -> {
            onSentence.accept("First sentence.");
            onSentence.accept("Second sentence.");
            return "First sentence. Second sentence.";
        };

        session = new SpeechSession(
                sttService, ttsService, cleanupConfig,
                null,
                streamGen,
                new DefaultPromptAssembler(null),
                sentTextMessages::add,
                sentBinaryMessages::add,
                java.util.Map.of(),
                null, null, null);

        session.handleStart(new AvatarMessage.Start(16000));
        session.handleStop();

        // TTS should be called once per sentence callback
        org.mockito.Mockito.verify(ttsService, org.mockito.Mockito.times(2)).synthesise(any(), any());
        assertThat(sentBinaryMessages).hasSize(2);
    }

    @Test
    void stopWithStreamingGeneratorUsesSelectedTtsModel() {
        when(recognitionStream.finalResult()).thenReturn(
                new TranscriptionResult("model test", "en", 0.9));

        var altTts = mock(TextToSpeechService.class);
        when(altTts.synthesise(any(), any())).thenReturn(
                new SynthesisResult(new byte[]{9}, "wav", List.of()));

        SpeechSession.StreamingResponseGenerator streamGen = (prompt, onSentence) -> {
            onSentence.accept("Reply.");
            return "Reply.";
        };

        session = new SpeechSession(
                sttService, ttsService, cleanupConfig,
                null,
                streamGen,
                new DefaultPromptAssembler(null),
                sentTextMessages::add,
                sentBinaryMessages::add,
                java.util.Map.of("kokoro:af_heart", altTts),
                null, null, null);

        session.handleStart(new AvatarMessage.Start(16000, null, "kokoro:af_heart"));
        session.handleStop();

        verify(altTts, atLeastOnce()).synthesise(any(), any());
        verify(ttsService, never()).synthesise(any(), any());
    }


    @Test
    void handleTextWithStreamingGeneratorProducesFullPipeline() {
        when(ttsService.synthesise(any(), any())).thenReturn(
                new SynthesisResult(new byte[]{1, 2}, "wav", List.of()));

        SpeechSession.StreamingResponseGenerator streamGen = (prompt, onSentence) -> {
            onSentence.accept("Streamed text reply.");
            return "Streamed text reply.";
        };

        session = new SpeechSession(
                sttService, ttsService, cleanupConfig,
                null,
                streamGen,
                new DefaultPromptAssembler(null),
                sentTextMessages::add,
                sentBinaryMessages::add,
                java.util.Map.of(),
                null, null, null);

        session.handleText("Hello from text");

        verify(sttService, never()).startStream(any());
        assertThat(sentTextMessages).anySatisfy(json ->
                                                        assertThat(json).contains("\"type\":\"transcript\""));
        assertThat(sentTextMessages).anySatisfy(json -> {
            assertThat(json).contains("\"type\":\"response\"");
            assertThat(json).contains("Streamed text reply.");
        });
        assertThat(sentTextMessages).anySatisfy(json ->
                                                        assertThat(json).contains("\"type\":\"timing\""));
        assertThat(sentBinaryMessages).isNotEmpty();
    }

    @Test
    void handleTextWithStreamingGeneratorSendsTimingWithLlmAndTtsSplit() {
        when(ttsService.synthesise(any(), any())).thenReturn(
                new SynthesisResult(new byte[]{1}, "wav", List.of()));

        SpeechSession.StreamingResponseGenerator streamGen = (prompt, onSentence) -> {
            onSentence.accept("Reply.");
            return "Reply.";
        };

        session = new SpeechSession(
                sttService, ttsService, cleanupConfig,
                null,
                streamGen,
                new DefaultPromptAssembler(null),
                sentTextMessages::add,
                sentBinaryMessages::add,
                java.util.Map.of(),
                null, null, null);

        session.handleText("Timing check");

        String timingJson = sentTextMessages.stream()
                                            .filter(json -> json.contains("\"type\":\"timing\""))
                                            .findFirst()
                                            .orElseThrow(() -> new AssertionError("No timing message sent"));

        // timing message should have cleanup, llm, tts, and total fields
        assertThat(timingJson).contains("\"cleanupMs\":");
        assertThat(timingJson).contains("\"llmMs\":");
        assertThat(timingJson).contains("\"ttsMs\":");
        assertThat(timingJson).contains("\"totalMs\":");
    }

// --- Full demo configuration: streaming + speaker ID + cleanup ---

    @Test
    void stopWithStreamingAndSpeakerServicesSendsFullPipeline() {
        when(recognitionStream.finalResult()).thenReturn(
                new TranscriptionResult("hello with speaker", "en", 0.9));
        when(ttsService.synthesise(any(), any())).thenReturn(
                new SynthesisResult(new byte[]{1, 2}, "wav", List.of()));

        SpeechSession.StreamingResponseGenerator streamGen = (prompt, onSentence) -> {
            onSentence.accept("Response with speaker.");
            return "Response with speaker.";
        };

        var extractor = mock(io.casehub.blocks.speech.SpeakerEmbeddingExtractor.class);
        var registry  = mock(io.casehub.blocks.speech.SpeakerRegistry.class);
        when(extractor.extract(any(), eq(16000))).thenReturn(
                new io.casehub.blocks.speech.SpeakerEmbedding(new float[192], 192));
        when(registry.identify(any(), org.mockito.ArgumentMatchers.anyDouble()))
                .thenReturn(java.util.Optional.of(
                        new io.casehub.blocks.speech.SpeakerMatch("Mark", 0.85)));

        session = new SpeechSession(
                sttService, ttsService, cleanupConfig,
                null,
                streamGen,
                new DefaultPromptAssembler(null),
                sentTextMessages::add,
                sentBinaryMessages::add,
                java.util.Map.of(),
                null, null, null);
        session.withSpeakerServices(extractor, registry);

        session.handleStart(new AvatarMessage.Start(16000));
        // Feed enough audio for speaker identification (>= MIN_SAMPLES_FOR_EMBEDDING)
        float[] audio = new float[24000]; // 1.5s at 16kHz
        java.util.Arrays.fill(audio, 0.1f);
        session.handleAudio(audio);
        session.handleStop();

        // Should have transcript, speaker identified, response, and timing
        assertThat(sentTextMessages).anySatisfy(json ->
                                                        assertThat(json).contains("\"type\":\"transcript\""));
        assertThat(sentTextMessages).anySatisfy(json ->
                                                        assertThat(json).contains("\"type\":\"speakerIdentified\""));
        assertThat(sentTextMessages).anySatisfy(json -> {
            assertThat(json).contains("\"type\":\"response\"");
            assertThat(json).contains("Response with speaker.");
        });
        assertThat(sentTextMessages).anySatisfy(json ->
                                                        assertThat(json).contains("\"type\":\"timing\""));
    }

}
