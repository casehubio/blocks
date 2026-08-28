package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.SynthesisOptions;
import io.casehub.blocks.speech.SynthesisResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class Audio8IntegrationTest {

    @Test
    void generateProducesCodecFrames() throws Exception {
        Path modelDir = ensureModelAvailable();
        var manifest = RuntimeManifest.load(modelDir.resolve("runtime_manifest.json"));
        var tokenizer = Audio8Tokenizer.load(modelDir.resolve("tokenizer").resolve("tokenizer.json"));
        var config = Audio8Config.defaults(modelDir, "0.1b");
        var ort = loadOrt();

        try (var loop = new DualARLoop(
                ort.createSession(modelDir.resolve(manifest.slowArFilename()), config.numThreads()),
                ort.createSession(modelDir.resolve(manifest.fastArFilename()), config.numThreads()),
                manifest)) {

            int[][] frames = loop.generate("Hello", "", null, config, tokenizer);

            assertThat(frames).isNotEmpty();
            assertThat(frames[0]).hasSize(manifest.numCodebooks());
            for (int[] frame : frames) {
                for (int code : frame) {
                    assertThat(code).isBetween(0, manifest.codebookSize() - 1);
                }
            }
        }
    }

    @Test
    void endToEndSynthesisProducesWav() throws Exception {
        Path modelDir = ensureModelAvailable();
        var manifest = RuntimeManifest.load(modelDir.resolve("runtime_manifest.json"));
        var tokenizer = Audio8Tokenizer.load(modelDir.resolve("tokenizer").resolve("tokenizer.json"));
        var config = Audio8Config.defaults(modelDir, "0.1b");
        var ort = loadOrt();

        try (var loop = new DualARLoop(
                ort.createSession(modelDir.resolve(manifest.slowArFilename()), config.numThreads()),
                ort.createSession(modelDir.resolve(manifest.fastArFilename()), config.numThreads()),
                manifest);
             var codec = new CodecDecoder(
                ort.createSession(modelDir.resolve(manifest.codecDecoderFilename()), config.numThreads()),
                manifest.sampleRate())) {

            int[][] frames = loop.generate("Hello world", "", null, config, tokenizer);
            assertThat(frames).isNotEmpty();

            float[] audio = codec.decode(frames);
            assertThat(audio).isNotEmpty();
            assertThat(audio.length).isGreaterThan(100);

            byte[] wav = WavWriter.encode(audio, manifest.sampleRate(), 1);
            assertThat(wav).isNotEmpty();
            assertThat(wav[0]).isEqualTo((byte) 'R');
            assertThat(wav[1]).isEqualTo((byte) 'I');
            assertThat(wav[2]).isEqualTo((byte) 'F');
            assertThat(wav[3]).isEqualTo((byte) 'F');
        }
    }

    @Test
    void compositionRootSynthesisProducesAudio() {
        assumeTrue(isOrtAvailable(), "onnxruntime not available");
        assumeTrue(isModelProvisionable(), "Audio8 model not available");

        try (var tts = Audio8TextToSpeech.withDefaults("0.1b")) {
            SynthesisResult result = tts.synthesise("Hello", SynthesisOptions.defaults());

            assertThat(result.audioData()).isNotEmpty();
            assertThat(result.audioFormat()).isEqualTo("wav");
        }
    }

    private static Path ensureModelAvailable() {
        assumeTrue(isOrtAvailable(), "onnxruntime not available");
        Path modelDir = Provisioner.audio8ModelDir("0.1b");
        assumeTrue(isModelProvisionable(), "Audio8 model not available — run Provisioner.ensureAudio8Model(\"0.1b\") first");
        Provisioner.ensureAudio8Model("0.1b");
        return modelDir;
    }

    private static OnnxRuntimeLibrary loadOrt() {
        return OnnxRuntimeLibrary.load();
    }

    private static boolean isOrtAvailable() {
        try {
            OnnxRuntimeLibrary.load();
            return true;
        } catch (UnsatisfiedLinkError | SherpaException e) {
            return false;
        }
    }

    private static boolean isModelProvisionable() {
        Path modelDir = Provisioner.audio8ModelDir("0.1b");
        Path manifest = modelDir.resolve("runtime_manifest.json");
        if (Files.exists(manifest)) return true;
        try {
            Path hfCache = Path.of(System.getProperty("user.home"),
                    ".cache", "huggingface", "hub",
                    "models--Audio8--audio8-TTS-0.1B-ONNX-INT8");
            return Files.isDirectory(hfCache);
        } catch (Exception e) {
            return false;
        }
    }
}
