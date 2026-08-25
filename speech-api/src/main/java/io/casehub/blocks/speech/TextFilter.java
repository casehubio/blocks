package io.casehub.blocks.speech;

public interface TextFilter {
    String apply(String text);

    String name();

    int destructiveness();
}
