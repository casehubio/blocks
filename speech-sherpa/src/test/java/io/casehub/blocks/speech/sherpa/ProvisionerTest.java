package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProvisionerTest {

    @AfterEach
    void clearSystemProperties() {
        System.clearProperty("casehub.speech.auto-download");
        System.clearProperty("casehub.speech.download-url");
        System.clearProperty("casehub.speech.cache-dir");
    }

    // --- URL construction ---

    @Test
    void nativeLibUrl_osxArm64() {
        String url = Provisioner.nativeLibUrl("osx-arm64");
        assertThat(url).isEqualTo(
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.6/"
            + "sherpa-onnx-v1.13.6-osx-arm64-shared-lib.tar.bz2");
    }

    @Test
    void nativeLibUrl_osxX64() {
        String url = Provisioner.nativeLibUrl("osx-x64");
        assertThat(url).contains("osx-x86_64-shared-lib");
    }

    @Test
    void nativeLibUrl_linuxX64() {
        String url = Provisioner.nativeLibUrl("linux-x64");
        assertThat(url).contains("linux-x64-shared-lib");
    }

    @Test
    void nativeLibUrl_linuxArm64() {
        String url = Provisioner.nativeLibUrl("linux-arm64");
        assertThat(url).contains("linux-aarch64-shared-cpu-lib");
    }

    @Test
    void nativeLibUrl_winX64() {
        String url = Provisioner.nativeLibUrl("win-x64");
        assertThat(url).contains("win-x64-shared-MD-Release-lib");
    }

    @Test
    void nativeLibUrl_unknownPlatform_throws() {
        assertThatThrownBy(() -> Provisioner.nativeLibUrl("solaris-sparc"))
            .isInstanceOf(SherpaException.class)
            .hasMessageContaining("solaris-sparc");
    }

    @Test
    void modelUrl_whisperTiny() {
        String url = Provisioner.modelUrl("sherpa-onnx-whisper-tiny");
        assertThat(url).isEqualTo(
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/"
            + "sherpa-onnx-whisper-tiny.tar.bz2");
    }

    @Test
    void customDownloadUrl_overridesBase() {
        System.setProperty("casehub.speech.download-url", "https://mirror.internal/");
        String url = Provisioner.nativeLibUrl("osx-arm64");
        assertThat(url).startsWith("https://mirror.internal/v1.13.6/");
    }

    // --- Config ---

    @Test
    void isAutoDownloadEnabled_defaultFalse() {
        System.clearProperty("casehub.speech.auto-download");
        assertThat(Provisioner.isAutoDownloadEnabled()).isFalse();
    }

    @Test
    void isAutoDownloadEnabled_explicitlyEnabled() {
        System.setProperty("casehub.speech.auto-download", "true");
        assertThat(Provisioner.isAutoDownloadEnabled()).isTrue();
    }

    @Test
    void isAutoDownloadEnabled_explicitlyDisabled() {
        System.setProperty("casehub.speech.auto-download", "false");
        assertThat(Provisioner.isAutoDownloadEnabled()).isFalse();
    }

    @Test
    void defaultModelDir_pointsToWhisperTiny() {
        Path dir = Provisioner.defaultModelDir();
        assertThat(dir.toString()).endsWith("sherpa-onnx-whisper-tiny");
        assertThat(dir.toString()).contains(".casehub");
    }

    @Test
    void cacheBaseDir_overrideViaSysProp(@TempDir Path tmp) {
        System.setProperty("casehub.speech.cache-dir", tmp.toString());
        Path dir = Provisioner.cacheBaseDir();
        assertThat(dir).isEqualTo(tmp);
    }

    @Test
    void cacheBaseDir_defaultsToHomeDotCasehub() {
        System.clearProperty("casehub.speech.cache-dir");
        Path dir = Provisioner.cacheBaseDir();
        assertThat(dir.toString()).endsWith(".casehub");
    }

    // --- SHA-256 verification ---

    @Test
    void verifyChecksum_match(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("test.bin");
        Files.write(file, "hello world".getBytes());
        String expected = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9";
        assertThatCode(() -> Provisioner.verifyChecksum(file, expected))
            .doesNotThrowAnyException();
    }

    @Test
    void verifyChecksum_mismatch_throwsAndDeletes(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("test.bin");
        Files.write(file, "hello world".getBytes());
        assertThatThrownBy(() -> Provisioner.verifyChecksum(file, "0000000000000000000000000000000000000000000000000000000000000000"))
            .isInstanceOf(SherpaException.class)
            .hasMessageContaining("SHA-256 mismatch");
        assertThat(Files.exists(file)).isFalse();
    }

    // --- ensureModel refuses unknown models ---

    @Test
    void ensureModel_unknownModel_throws() {
        assertThatThrownBy(() -> Provisioner.ensureModel("sherpa-onnx-whisper-gigantic"))
            .isInstanceOf(SherpaException.class)
            .hasMessageContaining("No checksum registered");
    }

    // --- ensureNativeLibrary returns existing dir ---

    @Test
    void ensureNativeLibrary_existingDir_returnsImmediately(@TempDir Path tmp) throws Exception {
        System.setProperty("casehub.speech.cache-dir", tmp.toString());
        Path nativeDir = tmp.resolve("native").resolve("sherpa-onnx")
            .resolve(SherpaLibrary.VERSION).resolve(SherpaLibrary.platformId());
        Files.createDirectories(nativeDir);
        Files.writeString(nativeDir.resolve("marker"), "exists");
        Path result = Provisioner.ensureNativeLibrary();
        assertThat(result).isEqualTo(nativeDir);
    }

    // --- ensureModel returns existing dir ---

    @Test
    void ensureModel_existingDir_returnsImmediately(@TempDir Path tmp) throws Exception {
        System.setProperty("casehub.speech.cache-dir", tmp.toString());
        Path modelDir = tmp.resolve("models").resolve("sherpa-onnx").resolve("sherpa-onnx-whisper-tiny");
        Files.createDirectories(modelDir);
        Files.writeString(modelDir.resolve("tiny-tokens.txt"), "tokens");
        Path result = Provisioner.ensureModel("sherpa-onnx-whisper-tiny");
        assertThat(result).isEqualTo(modelDir);
    }

    // --- Integration: real download (requires network) ---

    @Test
    void integration_ensureNativeLibrary_downloadsAndExtracts(@TempDir Path tmp) throws Exception {
        if (!"true".equals(System.getProperty("runIntegrationTests"))) { return; }
        System.setProperty("casehub.speech.cache-dir", tmp.toString());
        Path result = Provisioner.ensureNativeLibrary();
        assertThat(result).isNotNull();
        assertThat(Files.isDirectory(result)).isTrue();
        assertThat(Files.list(result).count()).isGreaterThan(0);
    }

// --- TTS model ---

    @Test
    void ttsModelUrl_constructsCorrectUrl() {
        String url = Provisioner.ttsModelUrl("vits-piper-en_US-lessac-medium");
        assertThat(url).contains("tts-models");
        assertThat(url).endsWith("vits-piper-en_US-lessac-medium.tar.bz2");
    }

    @Test
    void espeakLibName_returnsForCurrentPlatform() {
        String name = Provisioner.espeakLibName();
        assertThat(name).isNotEmpty();
        assertThat(name).containsAnyOf("espeak-ng");
    }

    @Test
    void espeakCacheDir_includesVersion() {
        Path dir = Provisioner.espeakCacheDir();
        assertThat(dir.toString()).contains("espeak-ng");
        assertThat(dir.toString()).contains("1.52.0");
    }
}
