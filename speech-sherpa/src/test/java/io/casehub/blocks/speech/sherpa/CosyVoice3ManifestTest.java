package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CosyVoice3ManifestTest {

    private static final String MANIFEST_JSON = """
            {
              "header": {
                "name": "cosyvoice3",
                "sampleRate": 24000,
                "stageModels": {
                  "generator": ["text_embedding_fp32.onnx", "llm_backbone_initial_fp16.onnx"],
                  "decoder": ["flow.decoder.estimator.fp16.onnx", "hift_decoder_fp32.onnx"]
                },
                "provider": {
                  "preferred": "cpu",
                  "stageOverrides": {"decoder": "cpu"}
                },
                "metadata": {"version": "3.0"}
              },
              "hiddenDim": 896,
              "speechVocabSize": 6561,
              "sosId": 6561,
              "eosId": 6562,
              "taskId": 6563,
              "numLlmLayers": 24,
              "kvHeadDim": 64,
              "tokenMelRatio": 2,
              "flowSteps": 10,
              "melBins": 80,
              "hiftNFft": 16,
              "hiftHopLength": 4,
              "speakerEmbedDim": 192,
              "tokenizerDir": "tokenizer",
              "defaultPrompts": {
                "en_female.wav": "Hello, how can I help?"
              }
            }
            """;

    @Test void parsesHeaderFields() {
        var manifest = CosyVoice3Manifest.parse(MANIFEST_JSON);
        assertThat(manifest.header().name()).isEqualTo("cosyvoice3");
        assertThat(manifest.header().sampleRate()).isEqualTo(24000);
    }

    @Test void parsesStageModels() {
        var manifest = CosyVoice3Manifest.parse(MANIFEST_JSON);
        assertThat(manifest.header().stageModels()).containsKeys("generator", "decoder");
        assertThat(manifest.header().stageModels().get("generator"))
                .containsExactly("text_embedding_fp32.onnx", "llm_backbone_initial_fp16.onnx");
    }

    @Test void parsesProviderConfig() {
        var manifest = CosyVoice3Manifest.parse(MANIFEST_JSON);
        assertThat(manifest.header().provider()).isNotNull();
        assertThat(manifest.header().provider().preferred()).isEqualTo("cpu");
        assertThat(manifest.header().provider().stageOverrides()).containsEntry("decoder", "cpu");
    }

    @Test void parsesMetadata() {
        var manifest = CosyVoice3Manifest.parse(MANIFEST_JSON);
        assertThat(manifest.header().metadata()).containsEntry("version", "3.0");
    }

    @Test void parsesHyperparameters() {
        var manifest = CosyVoice3Manifest.parse(MANIFEST_JSON);
        assertThat(manifest.hiddenDim()).isEqualTo(896);
        assertThat(manifest.speechVocabSize()).isEqualTo(6561);
        assertThat(manifest.sosId()).isEqualTo(6561);
        assertThat(manifest.eosId()).isEqualTo(6562);
        assertThat(manifest.taskId()).isEqualTo(6563);
        assertThat(manifest.numLlmLayers()).isEqualTo(24);
        assertThat(manifest.kvHeadDim()).isEqualTo(64);
        assertThat(manifest.tokenMelRatio()).isEqualTo(2);
        assertThat(manifest.flowSteps()).isEqualTo(10);
        assertThat(manifest.melBins()).isEqualTo(80);
        assertThat(manifest.hiftNFft()).isEqualTo(16);
        assertThat(manifest.hiftHopLength()).isEqualTo(4);
        assertThat(manifest.speakerEmbedDim()).isEqualTo(192);
        assertThat(manifest.tokenizerDir()).isEqualTo("tokenizer");
    }

    @Test void parsesDefaultPrompts() {
        var manifest = CosyVoice3Manifest.parse(MANIFEST_JSON);
        assertThat(manifest.defaultPrompts()).containsEntry("en_female.wav", "Hello, how can I help?");
    }

    @Test void implementsSealedInterface() {
        TtsPipelineManifest manifest = CosyVoice3Manifest.parse(MANIFEST_JSON);
        assertThat(manifest.header().name()).isEqualTo("cosyvoice3");
    }

    @Test
    void parsesWithoutProvider() {
        String json = """
                      {
                        "header": {
                          "name": "cosyvoice3",
                          "sampleRate": 24000,
                          "stageModels": {
                            "generator": ["model.onnx"]
                          }
                        },
                        "hiddenDim": 896, "speechVocabSize": 6561,
                        "sosId": 6561, "eosId": 6562, "taskId": 6563,
                        "numLlmLayers": 24, "kvHeadDim": 64, "tokenMelRatio": 2,
                        "flowSteps": 10, "melBins": 80, "hiftNFft": 16,
                        "hiftHopLength": 4, "speakerEmbedDim": 192,
                        "tokenizerDir": "tokenizer"
                      }
                      """;
        var manifest = CosyVoice3Manifest.parse(json);
        assertThat(manifest.header().provider()).isNull();
    }
}
