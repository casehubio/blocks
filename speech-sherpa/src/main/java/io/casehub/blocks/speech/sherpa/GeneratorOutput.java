package io.casehub.blocks.speech.sherpa;

public sealed interface GeneratorOutput
        permits GeneratorOutput.SpeechTokenOutput, GeneratorOutput.CodecFrameOutput {

    record SpeechTokenOutput(int[] speechTokens) implements GeneratorOutput {}

    record CodecFrameOutput(int[][] codecFrames) implements GeneratorOutput {}
}
