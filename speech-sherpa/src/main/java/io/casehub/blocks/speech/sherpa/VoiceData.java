package io.casehub.blocks.speech.sherpa;

public sealed interface VoiceData permits VoiceData.CodecVoiceData, VoiceData.EmbeddingVoiceData {

    record CodecVoiceData(int[] codecTokens) implements VoiceData {}

    record EmbeddingVoiceData(float[] speakerEmbedding, int[] speechTokens,
                              float[] promptMel, String promptText) implements VoiceData {}
}
