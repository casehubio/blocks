package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.TextFilter;

import java.util.regex.Pattern;

public final class FillerRemovalFilter implements TextFilter {

    private static final Pattern FILLER_PATTERN = Pattern.compile(
            "\\b(?:um+|uh+|er+|hm+|ah+|oh+|eh+|mhm)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern MULTI_SPACE = Pattern.compile("\\s{2,}");

    @Override
    public String apply(String text) {
        String stripped = FILLER_PATTERN.matcher(text).replaceAll("");
        return MULTI_SPACE.matcher(stripped).replaceAll(" ").trim();
    }

    @Override
    public String name() {
        return "filler-removal";
    }

    @Override
    public int destructiveness() {
        return 1;
    }
}
