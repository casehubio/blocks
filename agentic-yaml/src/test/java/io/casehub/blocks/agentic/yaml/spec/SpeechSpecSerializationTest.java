package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.blocks.agentic.yaml.registry.Audio8ConfigRegistry;
import io.casehub.blocks.agentic.yaml.registry.KokoroConfigRegistry;
import io.casehub.blocks.agentic.yaml.registry.SherpaConfigRegistry;
import io.casehub.blocks.speech.SynthesisOptions;
import io.casehub.blocks.speech.TranscriptionOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpeechSpecSerializationTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new JavaTimeModule());
    }

    @Nested
    class TranscriptionOptionsSpecs {

        @Test
        void deserialises_all_fields() throws Exception {
            var yaml = """
                    audioFormat: wav
                    languageHint: en
                    modelSize: base.en
                    vocabularyHint: medical
                    """;
            var opts = mapper.readValue(yaml, TranscriptionOptions.class);
            assertThat(opts.audioFormat()).isEqualTo("wav");
            assertThat(opts.languageHint()).isEqualTo("en");
            assertThat(opts.modelSize()).isEqualTo("base.en");
            assertThat(opts.vocabularyHint()).isEqualTo("medical");
        }

        @Test
        void deserialises_without_optional_fields() throws Exception {
            var yaml = """
                    audioFormat: wav
                    modelSize: base.en
                    """;
            var opts = mapper.readValue(yaml, TranscriptionOptions.class);
            assertThat(opts.audioFormat()).isEqualTo("wav");
            assertThat(opts.modelSize()).isEqualTo("base.en");
            assertThat(opts.languageHint()).isNull();
        }
    }

    @Nested
    class SynthesisOptionsSpecs {

        @Test
        void deserialises_all_fields() throws Exception {
            var yaml = """
                    voice: af_heart
                    language: en
                    audioFormat: wav
                    includePhonemes: true
                    """;
            var opts = mapper.readValue(yaml, SynthesisOptions.class);
            assertThat(opts.voice()).isEqualTo("af_heart");
            assertThat(opts.language()).isEqualTo("en");
            assertThat(opts.audioFormat()).isEqualTo("wav");
            assertThat(opts.includePhonemes()).isTrue();
        }

        @Test
        void deserialises_defaults() throws Exception {
            var yaml = """
                    audioFormat: wav
                    includePhonemes: false
                    """;
            var opts = mapper.readValue(yaml, SynthesisOptions.class);
            assertThat(opts.audioFormat()).isEqualTo("wav");
            assertThat(opts.includePhonemes()).isFalse();
        }
    }

    @Nested
    class SherpaConfigSpecs {

        @Test
        void deserialises_with_all_fields() throws Exception {
            var yaml = """
                    modelDir: /models/whisper
                    numThreads: 2
                    provider: cpu
                    punctuationModelDir: /models/punctuation
                    """;
            var spec = mapper.readValue(yaml, SherpaConfigSpec.class);
            assertThat(spec.modelDir()).isEqualTo("/models/whisper");
            assertThat(spec.numThreads()).isEqualTo(2);
            assertThat(spec.provider()).isEqualTo("cpu");
            assertThat(spec.punctuationModelDir()).isEqualTo("/models/punctuation");
        }

        @Test
        void deserialises_without_optional_fields() throws Exception {
            var yaml = """
                    modelDir: /models/whisper
                    numThreads: 2
                    provider: cpu
                    """;
            var spec = mapper.readValue(yaml, SherpaConfigSpec.class);
            assertThat(spec.punctuationModelDir()).isNull();
        }

        @Test
        void rejects_null_model_dir() {
            assertThatThrownBy(() -> new SherpaConfigSpec(null, 2, "cpu", null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void registry_resolves() {
            var spec = new SherpaConfigSpec("/models/whisper", 2, "cpu", null);
            var registry = new SherpaConfigRegistry();
            var result = registry.resolve(spec);
            assertThat(result.modelDir()).isEqualTo(Path.of("/models/whisper"));
            assertThat(result.numThreads()).isEqualTo(2);
            assertThat(result.provider()).isEqualTo("cpu");
        }

        @Test
        void registry_resolves_with_punctuation() {
            var spec = new SherpaConfigSpec("/models/whisper", 2, "cpu",
                    "/models/punctuation");
            var registry = new SherpaConfigRegistry();
            var result = registry.resolve(spec);
            assertThat(result.punctuationModelDir()).isEqualTo(Path.of("/models/punctuation"));
        }
    }

    @Nested
    class KokoroConfigSpecs {

        @Test
        void deserialises_all_fields() throws Exception {
            var yaml = """
                    modelDir: /models/kokoro
                    voiceId: 5
                    lengthScale: 1.2
                    numThreads: 2
                    provider: cpu
                    """;
            var spec = mapper.readValue(yaml, KokoroConfigSpec.class);
            assertThat(spec.modelDir()).isEqualTo("/models/kokoro");
            assertThat(spec.voiceId()).isEqualTo(5);
            assertThat(spec.lengthScale()).isEqualTo(1.2f);
        }

        @Test
        void registry_resolves() {
            var spec = new KokoroConfigSpec("/models/kokoro", 0, 1.0f, 2, "cpu");
            var registry = new KokoroConfigRegistry();
            var result = registry.resolve(spec);
            assertThat(result.modelDir()).isEqualTo(Path.of("/models/kokoro"));
            assertThat(result.voiceId()).isEqualTo(0);
        }
    }

    @Nested
    class Audio8ConfigSpecs {

        @Test
        void deserialises_all_fields() throws Exception {
            var yaml = """
                    modelDir: /models/audio8
                    variant: 0.1b-int8
                    numThreads: 2
                    provider: cpu
                    maxTokens: 2048
                    temperature: 0.6
                    topP: 0.9
                    topK: 50
                    """;
            var spec = mapper.readValue(yaml, Audio8ConfigSpec.class);
            assertThat(spec.modelDir()).isEqualTo("/models/audio8");
            assertThat(spec.variant()).isEqualTo("0.1b-int8");
            assertThat(spec.maxTokens()).isEqualTo(2048);
        }

        @Test
        void registry_resolves() {
            var spec = new Audio8ConfigSpec("/models/audio8", "0.1b-int8",
                    2, "cpu", 2048, 0.6f, 0.9f, 50);
            var registry = new Audio8ConfigRegistry();
            var result = registry.resolve(spec);
            assertThat(result.modelDir()).isEqualTo(Path.of("/models/audio8"));
            assertThat(result.variant()).isEqualTo("0.1b-int8");
        }
    }


    @Nested
    class CleanupConfigSpecs {

        @Test
        void deserialises_with_max_destructiveness() throws Exception {
            var yaml = "maxDestructiveness: 3";
            var spec = mapper.readValue(yaml, CleanupConfigSpec.class);
            assertThat(spec.maxDestructiveness()).isEqualTo(3);
        }

        @Test
        void rejects_negative_destructiveness() {
            assertThatThrownBy(() -> new CleanupConfigSpec(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void defaults_to_max_int_when_unset() throws Exception {
            var yaml = "{}";
            var spec = mapper.readValue(yaml, CleanupConfigSpec.class);
            assertThat(spec.maxDestructiveness()).isEqualTo(Integer.MAX_VALUE);
        }
    }

}
