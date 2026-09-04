package io.casehub.blocks.speech.ws;

import io.casehub.blocks.speech.CleanupConfig;
import io.casehub.blocks.speech.RecognitionStream;
import io.casehub.blocks.speech.SpeakerEmbedding;
import io.casehub.blocks.speech.SpeakerEmbeddingExtractor;
import io.casehub.blocks.speech.SpeakerMatch;
import io.casehub.blocks.speech.SpeakerRegistry;
import io.casehub.blocks.speech.StreamingSpeechToTextService;
import io.casehub.blocks.speech.TextToSpeechService;
import io.casehub.blocks.speech.TranscriptionResult;
import io.casehub.blocks.speech.ws.protocol.AvatarMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpeechSessionSpeakerTest {

    private StreamingSpeechToTextService sttService;
    private TextToSpeechService ttsService;
    private CleanupConfig cleanupConfig;
    private RecognitionStream recognitionStream;
    private SpeakerEmbeddingExtractor extractor;
    private SpeakerRegistry registry;
    private List<String> sentMessages;
    private SpeechSession session;

    @BeforeEach
    void setUp() {
        sttService = mock(StreamingSpeechToTextService.class);
        ttsService = mock(TextToSpeechService.class);
        cleanupConfig = mock(CleanupConfig.class);
        recognitionStream = mock(RecognitionStream.class);
        extractor = mock(SpeakerEmbeddingExtractor.class);
        registry = mock(SpeakerRegistry.class);

        when(sttService.startStream(any())).thenReturn(recognitionStream);
        when(cleanupConfig.apply(any())).thenAnswer(inv -> inv.getArgument(0));
        when(recognitionStream.partialResult()).thenReturn("");

        sentMessages = new ArrayList<>();
        session = new SpeechSession(
                sttService, ttsService, cleanupConfig,
                null,
                new DefaultPromptAssembler(null),
                sentMessages::add,
                data -> {});
        session.withSpeakerServices(extractor, registry);
    }

    @Test
    void identifiesKnownSpeaker() {
        var embedding = new SpeakerEmbedding(new float[]{1, 0, 0}, 3);
        when(extractor.extract(any(), anyInt())).thenReturn(embedding);
        when(registry.identify(any(), anyDouble()))
                .thenReturn(Optional.of(new SpeakerMatch("Mark", 0.95)));
        when(recognitionStream.finalResult())
                .thenReturn(new TranscriptionResult("hello", "en", 0.9));

        session.handleStart(new AvatarMessage.Start(16000));
        session.handleAudio(new float[16000 * 2]);
        session.handleStop();

        assertThat(sentMessages).anySatisfy(json -> {
            assertThat(json).contains("\"type\":\"speakerIdentified\"");
            assertThat(json).contains("\"name\":\"Mark\"");
        });
    }

    @Test
    void sendsPromptForUnknownSpeaker() {
        var embedding = new SpeakerEmbedding(new float[]{1, 0, 0}, 3);
        when(extractor.extract(any(), anyInt())).thenReturn(embedding);
        when(registry.identify(any(), anyDouble())).thenReturn(Optional.empty());
        when(recognitionStream.finalResult())
                .thenReturn(new TranscriptionResult("hello", "en", 0.9));

        session.handleStart(new AvatarMessage.Start(16000));
        session.handleAudio(new float[16000 * 2]);
        session.handleStop();

        assertThat(sentMessages).anySatisfy(json -> {
            assertThat(json).contains("\"type\":\"speakerPrompt\"");
        });
    }

    @Test
    void enrollsAfterSpeakerIdentifyResponse() {
        var embedding = new SpeakerEmbedding(new float[]{1, 0, 0}, 3);
        when(extractor.extract(any(), anyInt())).thenReturn(embedding);
        when(registry.identify(any(), anyDouble())).thenReturn(Optional.empty());
        when(recognitionStream.finalResult())
                .thenReturn(new TranscriptionResult("hello", "en", 0.9));

        session.handleStart(new AvatarMessage.Start(16000));
        session.handleAudio(new float[16000 * 2]);
        session.handleStop();

        session.handleSpeakerIdentify("Mark");

        verify(registry).register(eq("Mark"), any());
        assertThat(sentMessages).anySatisfy(json -> {
            assertThat(json).contains("\"type\":\"speakerIdentified\"");
            assertThat(json).contains("\"name\":\"Mark\"");
        });
    }

    @Test
    void skipsSpeakerIdForShortAudio() {
        when(recognitionStream.finalResult())
                .thenReturn(new TranscriptionResult("hi", "en", 0.9));

        session.handleStart(new AvatarMessage.Start(16000));
        session.handleAudio(new float[8000]);
        session.handleStop();

        verify(extractor, never()).extract(any(), anyInt());
        assertThat(sentMessages).noneSatisfy(json ->
                assertThat(json).contains("\"type\":\"speakerIdentified\""));
    }

    @Test
    void explicitEnrollmentRegistersOnStop() {
        var embedding = new SpeakerEmbedding(new float[]{1, 0, 0}, 3);
        when(extractor.extract(any(), anyInt())).thenReturn(embedding);
        when(recognitionStream.finalResult())
                .thenReturn(new TranscriptionResult("hello", "en", 0.9));

        session.handleStart(new AvatarMessage.Start(16000));
        session.handleSpeakerIdentify("Sarah");
        session.handleAudio(new float[16000 * 2]);
        session.handleStop();

        verify(registry).register(eq("Sarah"), any());
        assertThat(sentMessages).anySatisfy(json -> {
            assertThat(json).contains("\"type\":\"speakerIdentified\"");
            assertThat(json).contains("\"name\":\"Sarah\"");
        });
    }

    @Test
    void suppressesDuplicatePromptForUnknownSpeaker() {
        var embedding = new SpeakerEmbedding(new float[]{1, 0, 0}, 3);
        when(extractor.extract(any(), anyInt())).thenReturn(embedding);
        when(registry.identify(any(), anyDouble())).thenReturn(Optional.empty());
        when(recognitionStream.finalResult())
                .thenReturn(new TranscriptionResult("hello", "en", 0.9));

        session.handleStart(new AvatarMessage.Start(16000));
        session.handleAudio(new float[16000 * 2]);
        session.handleStop();

        session.handleStart(new AvatarMessage.Start(16000));
        session.handleAudio(new float[16000 * 2]);
        session.handleStop();

        long promptCount = sentMessages.stream()
                .filter(json -> json.contains("\"type\":\"speakerPrompt\""))
                .count();
        assertThat(promptCount).isEqualTo(1);
    }

    @Test
    void worksWithoutSpeakerServices() {
        var plainSession = new SpeechSession(
                sttService, ttsService, cleanupConfig,
                null,
                new DefaultPromptAssembler(null),
                sentMessages::add,
                data -> {});

        when(recognitionStream.finalResult())
                .thenReturn(new TranscriptionResult("hello", "en", 0.9));

        plainSession.handleStart(new AvatarMessage.Start(16000));
        plainSession.handleAudio(new float[16000 * 2]);
        plainSession.handleStop();

        assertThat(sentMessages).noneSatisfy(json ->
                assertThat(json).contains("\"type\":\"speakerIdentified\""));
        assertThat(sentMessages).noneSatisfy(json ->
                assertThat(json).contains("\"type\":\"speakerPrompt\""));
    }
}
