package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VoiceRegistryTest {

    @Test
    void registerReturnsUniqueIds(@TempDir Path tempDir) throws Exception {
        var registry = new VoiceRegistry(audio -> new int[]{1, 2, 3});
        Path wav1 = writeTestWav(tempDir, "v1.wav");
        Path wav2 = writeTestWav(tempDir, "v2.wav");

        String id1 = registry.register(wav1);
        String id2 = registry.register(wav2);

        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void getVoiceCodesReturnsRegisteredCodes(@TempDir Path tempDir) throws Exception {
        int[] expectedCodes = {10, 20, 30, 40};
        var registry = new VoiceRegistry(audio -> expectedCodes);
        Path wav = writeTestWav(tempDir, "voice.wav");

        String id = registry.register(wav);
        int[] codes = registry.getVoiceCodes(id);

        assertThat(codes).containsExactly(10, 20, 30, 40);
    }

    @Test
    void getVoiceCodesReturnsCopyNotReference(@TempDir Path tempDir) throws Exception {
        int[] sourceCodes = {1, 2, 3};
        var registry = new VoiceRegistry(audio -> sourceCodes);
        Path wav = writeTestWav(tempDir, "voice.wav");

        String id = registry.register(wav);
        int[] codes1 = registry.getVoiceCodes(id);
        int[] codes2 = registry.getVoiceCodes(id);

        assertThat(codes1).isNotSameAs(codes2);
        assertThat(codes1).containsExactly(codes2);
    }

    @Test
    void getVoiceCodesThrowsForUnknownId() {
        var registry = new VoiceRegistry(audio -> new int[0]);
        assertThatThrownBy(() -> registry.getVoiceCodes("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void releaseRemovesVoice(@TempDir Path tempDir) throws Exception {
        var registry = new VoiceRegistry(audio -> new int[]{1});
        Path wav = writeTestWav(tempDir, "voice.wav");

        String id = registry.register(wav);
        registry.release(id);

        assertThat(registry.registeredVoices()).doesNotContain(id);
    }

    @Test
    void registeredVoicesReturnsAllIds(@TempDir Path tempDir) throws Exception {
        var registry = new VoiceRegistry(audio -> new int[]{1});
        Path wav1 = writeTestWav(tempDir, "v1.wav");
        Path wav2 = writeTestWav(tempDir, "v2.wav");

        String id1 = registry.register(wav1);
        String id2 = registry.register(wav2);

        assertThat(registry.registeredVoices()).containsExactlyInAnyOrder(id1, id2);
    }

    @Test
    void closeRemovesAllVoices(@TempDir Path tempDir) throws Exception {
        var registry = new VoiceRegistry(audio -> new int[]{1});
        Path wav = writeTestWav(tempDir, "voice.wav");

        registry.register(wav);
        registry.close();

        assertThat(registry.registeredVoices()).isEmpty();
    }

    @Test
    void registerThrowsForNonExistentFile() {
        var registry = new VoiceRegistry(audio -> new int[]{1});
        assertThatThrownBy(() -> registry.register(Path.of("/nonexistent/voice.wav")))
                .isInstanceOf(SherpaException.class);
    }

    private static Path writeTestWav(Path dir, String name) throws Exception {
        Path wav = dir.resolve(name);
        byte[] pcm = new byte[100];
        Files.write(wav, WavWriter.encode(new float[50], 22050, 1));
        return wav;
    }
}
