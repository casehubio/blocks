package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VoiceDataTest {

    @Test void codecVoiceDataStoresTokens() {
        var vd = new VoiceData.CodecVoiceData(new int[]{1, 2, 3});
        assertThat(vd.codecTokens()).containsExactly(1, 2, 3);
        assertThat(vd).isInstanceOf(VoiceData.class);
    }

    @Test void embeddingVoiceDataStoresAllFields() {
        var vd = new VoiceData.EmbeddingVoiceData(
                new float[]{0.1f, 0.2f}, new int[]{10, 20},
                new float[]{0.5f, 0.6f}, "hello world");
        assertThat(vd.speakerEmbedding()).containsExactly(0.1f, 0.2f);
        assertThat(vd.speechTokens()).containsExactly(10, 20);
        assertThat(vd.promptMel()).containsExactly(0.5f, 0.6f);
        assertThat(vd.promptText()).isEqualTo("hello world");
    }

    @Test void exhaustiveSwitchCoversAllVariants() {
        VoiceData codec = new VoiceData.CodecVoiceData(new int[]{1});
        VoiceData embed = new VoiceData.EmbeddingVoiceData(
                new float[]{0.1f}, new int[]{10}, new float[]{0.5f}, "hi");
        assertThat(describe(codec)).isEqualTo("codec:1");
        assertThat(describe(embed)).isEqualTo("embedding:1");
    }

    private String describe(VoiceData vd) {
        return switch (vd) {
            case VoiceData.CodecVoiceData c -> "codec:" + c.codecTokens().length;
            case VoiceData.EmbeddingVoiceData e -> "embedding:" + e.speechTokens().length;
        };
    }
}
