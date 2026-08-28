package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.TextFilter;

import java.util.regex.Pattern;

public final class FillerRemovalFilter implements TextFilter {

    private static final Pattern FILLER_PATTERN = Pattern.compile(
            "\\b(?:um+|uh+|er+|hm+|ah+|oh+|eh+|mhm)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DISCOURSE_PATTERN = Pattern.compile(
            "\\b(?:you know|I mean|basically|actually|literally)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern LIKE_FILLER = Pattern.compile(
            "\\b(was|were|is|am|are|been|being|just|so)\\s+like\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern REPEATED_WORD = Pattern.compile(
            "\\b(\\w+)(\\s+\\1)+\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern MULTI_SPACE = Pattern.compile("\\s{2,}");

    @Override
    public String apply(String text) {
        String result = FILLER_PATTERN.matcher(text).replaceAll("");
        result = DISCOURSE_PATTERN.matcher(result).replaceAll("");
        result = LIKE_FILLER.matcher(result).replaceAll("$1");
        result = REPEATED_WORD.matcher(result).replaceAll("$1");
        return MULTI_SPACE.matcher(result).replaceAll(" ").trim();
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
