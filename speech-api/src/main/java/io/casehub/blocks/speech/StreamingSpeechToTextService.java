package io.casehub.blocks.speech;

public interface StreamingSpeechToTextService {
    RecognitionStream startStream(TranscriptionOptions options);
}
