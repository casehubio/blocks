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


}
