package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.TextFilter;

public final class CasingFilter implements TextFilter {
    @Override
    public String apply(String text) {
        return text.toLowerCase();
    }

    @Override
    public String name() {
        return "casing";
    }

    @Override
    public int destructiveness() {
        return 0;
    }
}
