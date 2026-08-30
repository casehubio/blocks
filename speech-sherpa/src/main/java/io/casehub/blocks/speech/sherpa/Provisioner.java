package io.casehub.blocks.speech.sherpa;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

final class Provisioner {

    private static final System.Logger LOG = System.getLogger("casehub-speech");
    private static final String DEFAULT_BASE_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/";
    private static final String TEMP_PREFIX = ".provisioning-";

    private static final Map<String, String> NATIVE_ASSETS = Map.of(
        "osx-arm64",   "sherpa-onnx-v" + SherpaLibrary.VERSION + "-osx-arm64-shared-lib.tar.bz2",
        "osx-x64",     "sherpa-onnx-v" + SherpaLibrary.VERSION + "-osx-x86_64-shared-lib.tar.bz2",
        "linux-x64",   "sherpa-onnx-v" + SherpaLibrary.VERSION + "-linux-x64-shared-lib.tar.bz2",
        "linux-arm64", "sherpa-onnx-v" + SherpaLibrary.VERSION + "-linux-aarch64-shared-cpu-lib.tar.bz2",
        "win-x64",     "sherpa-onnx-v" + SherpaLibrary.VERSION + "-win-x64-shared-MD-Release-lib.tar.bz2"
    );

    private static final Map<String, String> NATIVE_CHECKSUMS = Map.of(
        "osx-arm64",   "d628e43aed6b719be163549876f41c909b75df26b8f439a5af69de03896bc6f5",
        "osx-x64",     "0019dfc4b32d63c1392aa264aed2253c1e0c2fb09216f8e2cc269bbfb8bb49b5",
        "linux-x64",   "bbeb203da0f69e37235b50e168d61d1f64ad2de256490cc64ed5535957415a97",
        "linux-arm64", "3575bde0543da12fc626c814c14287455f70a22b72caa483c7398d5f20f4cb12",
        "win-x64",     "dca033829d3a7e74c127fc0d349a12257fb890fe5038a381ab1706e4b35cf0fa"
    );

    private static final Map<String, String> MODEL_CHECKSUMS = Map.of(
        "sherpa-onnx-whisper-tiny", "c46116994e539aa165266d96b325252728429c12535eb9d8b6a2b10f129e66b1",
        "vits-piper-en_US-lessac-medium", "9e3febfacf0abf4270172d2958bcec246032b7e88efc2720840cc80c93de334e",
        "vits-piper-en_US-lessac-high", "8619d204c7005866fe4f420181dfa79622af6a6222389f0b0818d2af31e0db0e",
        "vits-piper-en_US-amy-medium", "9a5d1fc497f85e8022b785bff5f8105203b1e33099ee6265203efc70b0cb0264",
        "vits-piper-en_US-ryan-high", "6a71edf4d308b9cb2eaeadc8d1f3c6bf96120ecb7fe52c29a2b6e139c59760ed",
        "vits-piper-en_GB-jenny_dioco-medium", "a0888024569bafbefc05a4b48ddf8419d8dbbf3205f4af37cf7c6f1a87cc20c5"
    );

    private static final Map<String, String> TTS_MODEL_EXPECTED_FILES = Map.of(
        "vits-piper-en_US-lessac-medium", "en_US-lessac-medium.onnx",
        "vits-piper-en_US-lessac-high", "en_US-lessac-high.onnx",
        "vits-piper-en_US-amy-medium", "en_US-amy-medium.onnx",
        "vits-piper-en_US-ryan-high", "en_US-ryan-high.onnx",
        "vits-piper-en_GB-jenny_dioco-medium", "en_GB-jenny_dioco-medium.onnx"
    );

    private static final Map<String, String> GECTOR_MODEL_EXPECTED_FILES = Map.of(
        "gector-deberta-base-5k", "model.onnx",
        "gector-deberta-large-5k", "model.onnx"
    );
    private static final Map<String, String> KOKORO_MODEL_EXPECTED_FILES = Map.of(
            "kokoro-en-v0_19", "model.onnx"
                                                                                 );
    private static final Map<String, String> STREAMING_STT_MODEL_EXPECTED_FILES = Map.of(
            "sherpa-onnx-streaming-zipformer-en-2023-06-26", "tokens.txt"
                                                                                        );


    private static final String ESPEAK_VERSION = "1.52.0";

    private static final Map<String, String> ESPEAK_EXPECTED_FILES = Map.of(
        "osx-arm64", "libespeak-ng.dylib",
        "osx-x64", "libespeak-ng.dylib",
        "linux-x64", "libespeak-ng.so",
        "linux-arm64", "libespeak-ng.so"
    );

    private static final Map<String, String> NATIVE_EXPECTED_FILES = Map.of(
        "osx-arm64",   "libsherpa-onnx-c-api.dylib",
        "osx-x64",     "libsherpa-onnx-c-api.dylib",
        "linux-x64",   "libsherpa-onnx-c-api.so",
        "linux-arm64", "libsherpa-onnx-c-api.so",
        "win-x64",     "sherpa-onnx-c-api.dll"
    );

    private static final Map<String, String> MODEL_EXPECTED_FILES = Map.of(
        "sherpa-onnx-whisper-tiny", "tiny-encoder.onnx"
    );

    private static final Object NATIVE_LOCK = new Object();
    private static final Object MODEL_LOCK = new Object();

    @FunctionalInterface
    interface Downloader {
        Path download(String url, Path parentDir) throws IOException, InterruptedException;
    }

    static Downloader downloader = Provisioner::httpDownload;

    private Provisioner() {}

    static boolean isAutoDownloadEnabled() {
        return "true".equalsIgnoreCase(
            System.getProperty("casehub.speech.auto-download"));
    }

    static Path cacheBaseDir() {
        String override = System.getProperty("casehub.speech.cache-dir");
        if (override != null) { return Path.of(override); }
        return Path.of(System.getProperty("user.home"), ".casehub");
    }

    static Path defaultModelDir() {
        return cacheBaseDir().resolve("models").resolve("sherpa-onnx")
            .resolve("sherpa-onnx-whisper-tiny");
    }

    static Path nativeCacheDir() {
        return cacheBaseDir().resolve("native").resolve("sherpa-onnx")
            .resolve(SherpaLibrary.VERSION).resolve(SherpaLibrary.platformId());
    }

    static String baseUrl() {
        return System.getProperty("casehub.speech.download-url", DEFAULT_BASE_URL);
    }

    static String nativeLibUrl(String platformId) {
        String asset = NATIVE_ASSETS.get(platformId);
        if (asset == null) {
            throw new SherpaException(
                "No native library available for platform: " + platformId
                + ". Supported: " + NATIVE_ASSETS.keySet());
        }
        return baseUrl() + "v" + SherpaLibrary.VERSION + "/" + asset;
    }

    static String modelUrl(String modelName) {
        return baseUrl() + "asr-models/" + modelName + ".tar.bz2";
    }

    static Path ensureNativeLibrary() {
        Path targetDir = nativeCacheDir();
        if (Files.isDirectory(targetDir)) { return targetDir; }

        synchronized (NATIVE_LOCK) {
            if (Files.isDirectory(targetDir)) { return targetDir; }
            String platformId = SherpaLibrary.platformId();
            String url = nativeLibUrl(platformId);
            String expectedHash = NATIVE_CHECKSUMS.get(platformId);
            if (expectedHash == null) {
                throw new SherpaException(
                    "No checksum registered for platform: " + platformId
                    + ". Cannot verify download integrity.");
            }
            String expectedFile = NATIVE_EXPECTED_FILES.get(platformId);
            return provision(url, targetDir, expectedHash, 2, expectedFile);
        }
    }

    static Path ensureModel(String modelName) {
        String expectedHash = MODEL_CHECKSUMS.get(modelName);
        if (expectedHash == null) {
            throw new SherpaException(
                "No checksum registered for model: " + modelName
                + ". Known models: " + MODEL_CHECKSUMS.keySet());
        }
        Path modelsParent = cacheBaseDir().resolve("models").resolve("sherpa-onnx");
        Path targetDir = modelsParent.resolve(modelName);
        if (Files.isDirectory(targetDir)) { return targetDir; }

        synchronized (MODEL_LOCK) {
            if (Files.isDirectory(targetDir)) { return targetDir; }
            String url = modelUrl(modelName);
            String expectedFile = MODEL_EXPECTED_FILES.get(modelName);
            return provision(url, targetDir, expectedHash, 1, expectedFile);
        }
    }

    static Path espeakCacheDir() {
        return cacheBaseDir().resolve("native").resolve("espeak-ng")
                             .resolve(ESPEAK_VERSION).resolve(SherpaLibrary.platformId());
    }

    static String espeakLibName() {
        String platformId = SherpaLibrary.platformId();
        String name       = ESPEAK_EXPECTED_FILES.get(platformId);
        if (name == null) {
            throw new SherpaException("No espeak-ng library available for platform: " + platformId);
        }
        return name;
    }

    static Path ensureEspeak() {
        Path targetDir   = espeakCacheDir();
        Path expectedLib = targetDir.resolve(espeakLibName());
        if (Files.exists(expectedLib)) {return targetDir;}

        // espeak-ng can also be found on the system path (e.g. Homebrew)
        Path systemLib = findSystemEspeak();
        if (systemLib != null) {return systemLib.getParent();}

        throw new SherpaException(
                "espeak-ng library not found. Install via package manager (brew install espeak-ng) "
                + "or place " + espeakLibName() + " in " + targetDir);
    }

    static Path findSystemEspeak() {
        String libName = espeakLibName();
        for (String dir : new String[]{"/opt/homebrew/lib", "/usr/local/lib", "/usr/lib"}) {
            Path candidate = Path.of(dir).resolve(libName);
            if (Files.exists(candidate)) {return candidate;}
        }
        return null;
    }

    static Path ttsModelDir(String modelName) {
        return cacheBaseDir().resolve("models").resolve("sherpa-onnx").resolve(modelName);
    }

    static String ttsModelUrl(String modelName) {
        return baseUrl() + "tts-models/" + modelName + ".tar.bz2";
    }

    static Path ensureTtsModel(String modelName) {
        String expectedHash = MODEL_CHECKSUMS.get(modelName);
        if (expectedHash == null) {
            throw new SherpaException(
                    "No checksum registered for TTS model: " + modelName
                    + ". Known models: " + MODEL_CHECKSUMS.keySet());
        }
        Path   targetDir    = ttsModelDir(modelName);
        String expectedFile = TTS_MODEL_EXPECTED_FILES.get(modelName);

        if (Files.isDirectory(targetDir) && expectedFile != null && Files.exists(targetDir.resolve(expectedFile))) {
            ModelPatcher.patch(targetDir);
            return targetDir;
        }

        synchronized (MODEL_LOCK) {
            if (Files.isDirectory(targetDir) && expectedFile != null && Files.exists(targetDir.resolve(expectedFile))) {
                ModelPatcher.patch(targetDir);
                return targetDir;
            }
            String url    = ttsModelUrl(modelName);
            Path   result = provision(url, targetDir, expectedHash, 1, expectedFile);
            ModelPatcher.patch(result);
            return result;
        }
    }

    static Path gectorModelDir(String modelName) {
        return cacheBaseDir().resolve("models").resolve("gector").resolve(modelName);
    }

    static String gectorModelUrl(String modelName) {
        String override = System.getProperty("casehub.speech.gector-url");
        if (override != null) {
            return override + modelName + ".tar.bz2";
        }
        return "https://github.com/casehubio/speech-models/releases/download/gector-v1/" + modelName + ".tar.bz2";}

    static Path ensureGectorModel(String modelName) {
        String expectedFile = GECTOR_MODEL_EXPECTED_FILES.get(modelName);
        if (expectedFile == null) {
            throw new SherpaException(
                    "Unknown GECToR model: " + modelName
                    + ". Known models: " + GECTOR_MODEL_EXPECTED_FILES.keySet());
        }
        Path targetDir = gectorModelDir(modelName);

        if (Files.isDirectory(targetDir) && Files.exists(targetDir.resolve(expectedFile))) {
            return targetDir;
        }

        synchronized (MODEL_LOCK) {
            if (Files.isDirectory(targetDir) && Files.exists(targetDir.resolve(expectedFile))) {
                return targetDir;
            }
            String url = gectorModelUrl(modelName);
            return provision(url, targetDir, null, 1, expectedFile);
        }
    }

    private static final String PUNCT_MODEL_NAME = "sherpa-onnx-punct-ct-transformer-zh-en-vocab272727-2024-04-12";

    static Path punctModelDir() {
        return cacheBaseDir().resolve("models").resolve("sherpa-onnx").resolve(PUNCT_MODEL_NAME);
    }

    static Path ensurePunctuationModel() {
        Path   targetDir    = punctModelDir();
        String expectedFile = "model.onnx";

        if (Files.isDirectory(targetDir) && Files.exists(targetDir.resolve(expectedFile))) {
            return targetDir;
        }

        synchronized (MODEL_LOCK) {
            if (Files.isDirectory(targetDir) && Files.exists(targetDir.resolve(expectedFile))) {
                return targetDir;
            }
            String url = baseUrl() + "punctuation-models/" + PUNCT_MODEL_NAME + ".tar.bz2";
            return provision(url, targetDir, null, 1, expectedFile);
        }
    }

    private static final String WHISPER_VERSION = "1.7.3";

    private static final Map<String, String> WHISPER_NATIVE_ASSETS = Map.of(
            "osx-arm64", "whisper-v" + WHISPER_VERSION + "-bin-macos-arm64.tar.gz",
            "osx-x64", "whisper-v" + WHISPER_VERSION + "-bin-macos-x64.tar.gz",
            "linux-x64", "whisper-v" + WHISPER_VERSION + "-bin-ubuntu-x64.tar.gz",
            "linux-arm64", "whisper-v" + WHISPER_VERSION + "-bin-ubuntu-arm64.tar.gz"
                                                                           );

    private static final Map<String, String> WHISPER_NATIVE_EXPECTED_FILES = Map.of(
            "osx-arm64", "libwhisper.dylib",
            "osx-x64", "libwhisper.dylib",
            "linux-x64", "libwhisper.so",
            "linux-arm64", "libwhisper.so"
                                                                                   );

    private static final Map<String, String> WHISPER_MODEL_EXPECTED_FILES = Map.of(
            "ggml-base.en", "ggml-base.en.bin",
            "ggml-small.en", "ggml-small.en.bin",
            "ggml-tiny.en", "ggml-tiny.en.bin"
                                                                                  );

    static String whisperVersion() {return WHISPER_VERSION;}

    static Path whisperNativeCacheDir() {
        return cacheBaseDir().resolve("native").resolve("whisper")
                             .resolve(WHISPER_VERSION).resolve(SherpaLibrary.platformId());
    }

    static Path ensureWhisperNativeLib() {
        String platformId   = SherpaLibrary.platformId();
        String expectedFile = WHISPER_NATIVE_EXPECTED_FILES.get(platformId);
        if (expectedFile == null) {
            throw new SherpaException("No whisper native lib available for platform: " + platformId);
        }
        Path targetDir = whisperNativeCacheDir();
        if (Files.isDirectory(targetDir) && Files.exists(targetDir.resolve(expectedFile))) {
            return targetDir;
        }
        synchronized (NATIVE_LOCK) {
            if (Files.isDirectory(targetDir) && Files.exists(targetDir.resolve(expectedFile))) {
                return targetDir;
            }
            String asset = WHISPER_NATIVE_ASSETS.get(platformId);
            String url   = "https://github.com/ggerganov/whisper.cpp/releases/download/v" + WHISPER_VERSION + "/" + asset;
            return provision(url, targetDir, null, 1, expectedFile);
        }
    }

    static Path whisperModelDir(String modelName) {
        return cacheBaseDir().resolve("models").resolve("whisper").resolve(modelName);
    }

    static Path ensureWhisperModel(String modelName) {
        String expectedFile = WHISPER_MODEL_EXPECTED_FILES.get(modelName);
        if (expectedFile == null) {
            throw new SherpaException("Unknown Whisper model: " + modelName
                                      + ". Known models: " + WHISPER_MODEL_EXPECTED_FILES.keySet());
        }
        Path targetDir = whisperModelDir(modelName);
        Path modelFile = targetDir.resolve(expectedFile);

        if (Files.exists(modelFile)) {
            return targetDir;
        }

        synchronized (MODEL_LOCK) {
            if (Files.exists(modelFile)) {
                return targetDir;
            }
            String url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/" + expectedFile;
            try {
                Files.createDirectories(targetDir);
                Path downloaded = downloadWithRetry(url, targetDir, 3);
                if (!downloaded.getFileName().toString().equals(expectedFile)) {
                    Files.move(downloaded, modelFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new SherpaException("Failed to download Whisper model: " + url
                                          + ". Download manually to " + modelFile, e);
            }
            return targetDir;
        }
    }


    static Path kokoroModelDir(String modelName) {
        return cacheBaseDir().resolve("models").resolve("sherpa-onnx").resolve(modelName);
    }

    static String kokoroModelUrl(String modelName) {
        return baseUrl() + "tts-models/" + modelName + ".tar.bz2";
    }

    static Path ensureKokoroModel(String modelName) {
        String expectedFile = KOKORO_MODEL_EXPECTED_FILES.get(modelName);
        if (expectedFile == null) {
            throw new SherpaException(
                    "Unknown Kokoro model: " + modelName
                    + ". Known models: " + KOKORO_MODEL_EXPECTED_FILES.keySet());
        }
        Path targetDir = kokoroModelDir(modelName);

        if (Files.isDirectory(targetDir) && Files.exists(targetDir.resolve(expectedFile))) {
            return targetDir;
        }

        synchronized (MODEL_LOCK) {
            if (Files.isDirectory(targetDir) && Files.exists(targetDir.resolve(expectedFile))) {
                return targetDir;
            }
            String url = kokoroModelUrl(modelName);
            return provision(url, targetDir, null, 1, expectedFile);
        }
    }

    static Path streamingSttModelDir(String modelName) {
        return cacheBaseDir().resolve("models").resolve("sherpa-onnx").resolve(modelName);
    }

    static Path ensureStreamingSttModel(String modelName) {
        String expectedFile = STREAMING_STT_MODEL_EXPECTED_FILES.get(modelName);
        if (expectedFile == null) {
            throw new SherpaException(
                    "Unknown streaming STT model: " + modelName
                    + ". Known models: " + STREAMING_STT_MODEL_EXPECTED_FILES.keySet());
        }
        Path targetDir = streamingSttModelDir(modelName);

        if (Files.isDirectory(targetDir) && Files.exists(targetDir.resolve(expectedFile))) {
            return targetDir;
        }

        synchronized (MODEL_LOCK) {
            if (Files.isDirectory(targetDir) && Files.exists(targetDir.resolve(expectedFile))) {
                return targetDir;
            }
            String url = modelUrl(modelName);
            return provision(url, targetDir, null, 1, expectedFile);
        }
    }

    // --- Audio8 TTS models (HuggingFace) ---

    private static final Map<String, String> AUDIO8_REPOS = Map.of(
            "0.1b", "Audio8/audio8-TTS-0.1B-ONNX-INT8",
            "0.6b", "Audio8/Audio8-TTS-Preview-0.6B-ONNX-INT4"
                                                                  );

    private static final Map<String, List<String>> AUDIO8_MODEL_FILES = Map.of(
            "0.1b", List.of(
                    "slow_ar_int8.onnx", "slow_ar_int8.onnx.data",
                    "fast_ar_int8.onnx", "fast_ar_int8.onnx.data",
                    "codec_decoder_fp16.onnx", "codec_decoder_fp16.onnx.data",
                    "registration/codec_encoder_fp16.onnx", "registration/codec_encoder_fp16.onnx.data",
                    "tokenizer/tokenizer.json", "runtime_manifest.json",
                    "reference_codes.npy"
                           ),
            "0.6b", List.of(
                    "slow_ar_int4.onnx", "slow_ar_int4.onnx.data",
                    "fast_ar_int4.onnx", "fast_ar_int4.onnx.data",
                    "codec_decoder_fp16.onnx", "codec_decoder_fp16.onnx.data",
                    "registration/codec_encoder_fp16.onnx", "registration/codec_encoder_fp16.onnx.data",
                    "tokenizer/tokenizer.json", "runtime_manifest.json"
                           )
                                                                              );
    private static final String                    COSYVOICE3_REPO    = "ayousanz/cosy-voice3-onnx";

    private static final List<String> COSYVOICE3_MODEL_FILES = List.of(
            "campplus.onnx",
            "speech_tokenizer_v3.onnx",
            "text_embedding_fp32.onnx",
            "llm_backbone_initial_fp16.onnx",
            "llm_backbone_decode_fp16.onnx",
            "llm_decoder_fp16.onnx",
            "llm_speech_embedding_fp16.onnx",
            "flow_token_embedding_fp16.onnx",
            "flow_pre_lookahead_fp16.onnx",
            "flow_speaker_projection_fp16.onnx",
            "flow.decoder.estimator.fp16.onnx",
            "hift_f0_predictor_fp32.onnx",
            "hift_source_generator_fp32.onnx",
            "hift_decoder_fp32.onnx",
            "tokenizer/vocab.json",
            "tokenizer/merges.txt"
                                                                      );


    static Path audio8ModelDir(String variant) {
        return cacheBaseDir().resolve("models").resolve("audio8-tts").resolve(variant);
    }

    static Path ensureAudio8Model(String variant) {
        List<String> files = AUDIO8_MODEL_FILES.get(variant);
        if (files == null) {
            throw new SherpaException(
                    "Unknown Audio8 variant: " + variant
                    + ". Known variants: " + AUDIO8_MODEL_FILES.keySet());
        }
        Path targetDir = audio8ModelDir(variant);

        if (Files.isDirectory(targetDir) && files.stream().allMatch(f -> Files.exists(targetDir.resolve(f)))) {
            return targetDir;
        }

        synchronized (MODEL_LOCK) {
            if (Files.isDirectory(targetDir) && files.stream().allMatch(f -> Files.exists(targetDir.resolve(f)))) {
                return targetDir;
            }
            String repo = AUDIO8_REPOS.get(variant);
            provisionFromHuggingFace(repo, files, targetDir);
            return targetDir;
        }
    }

    static Path cosyVoice3ModelDir() {
        return cacheBaseDir().resolve("models").resolve("cosyvoice3");
    }

    static Path ensureCosyVoice3Model() {
        Path targetDir = cosyVoice3ModelDir();
        if (Files.isDirectory(targetDir) && COSYVOICE3_MODEL_FILES.stream()
                                                                  .allMatch(f -> Files.exists(targetDir.resolve(f)))) {
            return targetDir;
        }
        synchronized (MODEL_LOCK) {
            if (Files.isDirectory(targetDir) && COSYVOICE3_MODEL_FILES.stream()
                                                                      .allMatch(f -> Files.exists(targetDir.resolve(f)))) {
                return targetDir;
            }
            provisionFromHuggingFace(COSYVOICE3_REPO, COSYVOICE3_MODEL_FILES, targetDir);
            writeCosyVoice3Manifest(targetDir);
            return targetDir;
        }
    }

    private static void writeCosyVoice3Manifest(Path targetDir) {
        Path manifestPath = targetDir.resolve("pipeline_manifest.json");
        if (Files.exists(manifestPath)) {return;}
        String json = """
                      {
                        "header": {
                          "name": "cosyvoice3",
                          "sampleRate": 24000,
                          "stageModels": {
                            "voice_encoder": ["campplus.onnx", "speech_tokenizer_v3.onnx"],
                            "generator": ["text_embedding_fp32.onnx", "llm_speech_embedding_fp16.onnx",
                                          "llm_backbone_initial_fp16.onnx", "llm_backbone_decode_fp16.onnx",
                                          "llm_decoder_fp16.onnx"],
                            "decoder": ["flow_token_embedding_fp16.onnx", "flow_pre_lookahead_fp16.onnx",
                                        "flow_speaker_projection_fp16.onnx", "flow.decoder.estimator.fp16.onnx",
                                        "hift_f0_predictor_fp32.onnx", "hift_source_generator_fp32.onnx",
                                        "hift_decoder_fp32.onnx"]
                          },
                          "metadata": {}
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
                        "defaultPrompts": {}
                      }
                      """;
        try {
            Files.writeString(manifestPath, json);
        } catch (IOException e) {
            throw new SherpaException("Failed to write CosyVoice3 manifest", e);
        }
    }


    private static void provisionFromHuggingFace(String repo, List<String> files, Path targetDir) {
        try {
            Files.createDirectories(targetDir);
            for (String file : files) {
                Path filePath = targetDir.resolve(file);
                if (Files.exists(filePath)) {continue;}
                Files.createDirectories(filePath.getParent());
                String url = "https://huggingface.co/" + repo + "/resolve/main/" + file;
                LOG.log(System.Logger.Level.INFO, "Downloading {0}...", url);
                Path downloaded = downloadWithRetry(url, filePath.getParent(), 3);
                Files.move(downloaded, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
            LOG.log(System.Logger.Level.INFO, "Model provisioned at {0}", targetDir);
        } catch (SherpaException e) {
            throw e;
        } catch (Exception e) {
            throw new SherpaException("Failed to provision model from HuggingFace: " + repo
                                      + ". Download manually to " + targetDir, e);
        }
    }


    private static Path provision(String url, Path targetDir, String expectedHash,
                                  int stripComponents, String expectedFile) {
        Path parentDir = targetDir.getParent();
        try {
            Files.createDirectories(parentDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create directory: " + parentDir, e);
        }

        Path lockFile = parentDir.resolve(".provisioning.lock");
        try (FileChannel channel = FileChannel.open(lockFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {

            if (Files.isDirectory(targetDir)) { return targetDir; }

            cleanOrphanedTempDirs(parentDir);

            LOG.log(System.Logger.Level.INFO, "Downloading {0}...", url);
            Path archive = downloadWithRetry(url, parentDir, 1);
            try {
                verifyChecksum(archive, expectedHash);
                Path tempExtractDir = Files.createTempDirectory(parentDir, TEMP_PREFIX);
                try {
                    extract(archive, tempExtractDir, stripComponents);
                    Files.move(tempExtractDir, targetDir, StandardCopyOption.ATOMIC_MOVE);
                } catch (Exception e) {
                    deleteRecursively(tempExtractDir);
                    throw e;
                }
            } finally {
                Files.deleteIfExists(archive);
            }

            if (expectedFile != null && !Files.exists(targetDir.resolve(expectedFile))) {
                deleteRecursively(targetDir);
                throw new SherpaException(
                    "Extraction completed but expected file not found: " + expectedFile
                    + " in " + targetDir + ". The archive structure may have changed.");
            }

            LOG.log(System.Logger.Level.INFO, "Done.");
            return targetDir;

        } catch (SherpaException e) {
            throw e;
        } catch (IOException e) {
            throw new SherpaException("Failed to provision from " + url
                + ". Download manually to " + targetDir, e);
        }
    }

    private static Path downloadWithRetry(String url, Path parentDir, int retries) {
        try {
            return downloader.download(url, parentDir);
        } catch (IOException | InterruptedException e) {
            if (retries > 0 && isTransient(e)) {
                try { Thread.sleep(2000); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new SherpaException("Download interrupted: " + url, ie);
                }
                return downloadWithRetry(url, parentDir, retries - 1);
            }
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new SherpaException("Failed to download " + url
                + ". Download manually.", e);
        }
    }

    private static boolean isTransient(Exception e) {
        if (e instanceof java.net.ConnectException) return true;
        if (e instanceof java.net.SocketTimeoutException) return true;
        if (e instanceof IOException && e.getMessage() != null
            && e.getMessage().contains("Connection reset")) return true;
        if (e instanceof HttpDownloadException hde && hde.statusCode() >= 500) return true;
        return false;
    }

    private static Path httpDownload(String url, Path parentDir) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

        Path tempFile = Files.createTempFile(parentDir, TEMP_PREFIX, ".tar.bz2");
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();
            HttpResponse<InputStream> response = client.send(request,
                HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                Files.deleteIfExists(tempFile);
                throw new HttpDownloadException(response.statusCode(),
                    "HTTP " + response.statusCode() + " downloading " + url);
            }
            try (InputStream in = response.body();
                 var out = Files.newOutputStream(tempFile)) {
                in.transferTo(out);
            }
            return tempFile;
        } catch (Exception e) {
            Files.deleteIfExists(tempFile);
            throw e;
        }
    }

    static void verifyChecksum(Path file, String expectedHex) {
        if (expectedHex == null) return;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buf = new byte[8192];
                int read;
                while ((read = in.read(buf)) != -1) {
                    digest.update(buf, 0, read);
                }
            }
            String actualHex = HexFormat.of().formatHex(digest.digest());
            if (!actualHex.equalsIgnoreCase(expectedHex)) {
                Files.deleteIfExists(file);
                throw new SherpaException("SHA-256 mismatch for " + file.getFileName()
                    + "\n  Expected: " + expectedHex
                    + "\n  Actual:   " + actualHex);
            }
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 not available", e);
        } catch (SherpaException e) {
            throw e;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to verify checksum: " + file, e);
        }
    }

    private static void extract(Path archive, Path targetDir, int stripComponents) {
        try {
            Files.createDirectories(targetDir);
            ProcessBuilder pb = new ProcessBuilder(
                "tar", "xf", archive.toString(),
                "--strip-components=" + stripComponents,
                "-C", targetDir.toString());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output;
            try (InputStream is = process.getInputStream()) {
                output = new String(is.readAllBytes());
            }
            boolean finished = process.waitFor(5, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new SherpaException("tar extraction timed out after 5 minutes");
            }
            if (process.exitValue() != 0) {
                throw new SherpaException("tar extraction failed (exit "
                    + process.exitValue() + "): " + output);
            }
        } catch (SherpaException e) {
            throw e;
        } catch (IOException e) {
            throw new SherpaException("tar not found or extraction failed. "
                + "Install tar or download manually.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SherpaException("tar extraction interrupted", e);
        }
    }

    private static void cleanOrphanedTempDirs(Path parentDir) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(parentDir,
                TEMP_PREFIX + "*")) {
            for (Path orphan : stream) {
                deleteRecursively(orphan);
            }
        } catch (IOException ignored) {}
    }

    private static void deleteRecursively(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        } catch (IOException ignored) {}
    }

    static final class HttpDownloadException extends IOException {
        private final int statusCode;

        HttpDownloadException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        int statusCode() { return statusCode; }
    }
}
