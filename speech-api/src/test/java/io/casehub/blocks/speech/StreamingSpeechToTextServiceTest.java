package io.casehub.blocks.speech;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StreamingSpeechToTextServiceTest {

    @Test
    void stubStreamAcceptsSamplesAndReturnsResult() {
        StreamingSpeechToTextService service = options -> new RecognitionStream() {
            private String accumulated = "";

            @Override
            public void acceptSamples(float[] samples, int sampleRate) {
                accumulated += "[" + samples.length + " samples]";
            }

            @Override
            public boolean isEndpointDetected() {
                return !accumulated.isEmpty();
            }

            @Override
            public String partialResult() {
                return accumulated;
            }

            @Override
            public TranscriptionResult finalResult() {
                return new TranscriptionResult(accumulated, "en", 1.0);
            }

            @Override
            public void close() {}
        };

        try (RecognitionStream stream = service.startStream(TranscriptionOptions.defaults())) {
            stream.acceptSamples(new float[160], 16000);
            assertThat(stream.partialResult()).contains("160 samples");
            assertThat(stream.isEndpointDetected()).isTrue();

            stream.acceptSamples(new float[320], 16000);
            TranscriptionResult result = stream.finalResult();
            assertThat(result.text()).contains("160 samples").contains("320 samples");
        }
    }

    @Test
    void streamIsAutoCloseable() {
        StreamingSpeechToTextService service = options -> new RecognitionStream() {
            @Override public void acceptSamples(float[] samples, int sampleRate) {}
            @Override public boolean isEndpointDetected() { return false; }
            @Override public String partialResult() { return ""; }
            @Override public TranscriptionResult finalResult() { return new TranscriptionResult("", "en", 0.0); }
            @Override public void close() {}
        };

        RecognitionStream stream = service.startStream(TranscriptionOptions.defaults());
        stream.close();
    }
}
