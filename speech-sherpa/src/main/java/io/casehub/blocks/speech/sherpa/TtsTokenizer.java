package io.casehub.blocks.speech.sherpa;

@FunctionalInterface
interface TtsTokenizer {
    int[] encode(String text);
}
