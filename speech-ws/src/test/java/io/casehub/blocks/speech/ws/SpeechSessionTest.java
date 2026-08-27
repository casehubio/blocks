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
import static org.mockito.Mockito.*;

class SpeechSessionTest {

    private StreamingSpeechToTextService sttService;
    private TextToSpeechService ttsService;
    private CleanupConfig cleanupConfig;
    private RecognitionStream recognitionStream;
    private List<String> sentTextMessages;
    private List<byte[]> sentBinaryMessages;
    private SpeechSession session;

    @BeforeEach
    void setUp() {
        sttService = mock(StreamingSpeechToTextService.class);
        ttsService = mock(TextToSpeechService.class);
        cleanupConfig = mock(CleanupConfig.class);
        recognitionStream = mock(RecognitionStream.class);

        when(sttService.startStream(any())).thenReturn(recognitionStream);
        when(cleanupConfig.apply(any())).thenAnswer(inv -> inv.getArgument(0));

        sentTextMessages = new ArrayList<>();
        sentBinaryMessages = new ArrayList<>();
        session = new SpeechSession(
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
    }

    @Test
    void audioSamplesAreFedToRecognitionStream() {
        session.handleStart(new AvatarMessage.Start(16000));
        float[] samples = {0.1f, 0.2f, 0.3f};
        session.handleAudio(samples, 16000);
        verify(recognitionStream).acceptSamples(samples, 16000);
    }

    @Test
    void audioWithoutStartIsIgnored() {
        float[] samples = {0.1f, 0.2f};
        session.handleAudio(samples, 16000);
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
                prompt -> "I hear you.",
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
                prompt -> "reply",
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
}
