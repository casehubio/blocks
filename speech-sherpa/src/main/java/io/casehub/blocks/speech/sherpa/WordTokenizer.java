package io.casehub.blocks.speech.sherpa;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public final class WordTokenizer {

    private static final Pattern CONTRACTION = Pattern.compile(
            "(?i)(n't|'re|'ve|'ll|'d|'m|'s)$");

    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile(
            "(?<=[.!?])\\s+");

    public static List<String> tokenize(String text) {
        if (text.isEmpty()) return List.of();
        var result = new ArrayList<String>();
        for (String rawToken : text.split("\\s+")) {
            if (rawToken.isEmpty()) continue;
            tokenizeWord(rawToken, result);
        }
        return result;
    }

    public static List<String> splitSentences(String text) {
        if (text.isBlank()) return List.of();
        var result = new ArrayList<String>();
        for (String part : SENTENCE_BOUNDARY.split(text)) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }

    private static void tokenizeWord(String word, List<String> result) {
        int start = 0;
        while (start < word.length() && isOpenPunct(word.charAt(start))) {
            result.add(String.valueOf(word.charAt(start)));
            start++;
        }

        int end = word.length();
        var trailing = new ArrayList<String>();
        while (end > start && isClosePunct(word.charAt(end - 1))) {
            trailing.add(String.valueOf(word.charAt(end - 1)));
            end--;
        }

        if (start < end) {
            String core = word.substring(start, end);
            var matcher = CONTRACTION.matcher(core);
            if (matcher.find()) {
                String prefix = core.substring(0, matcher.start());
                if (!prefix.isEmpty()) result.add(prefix);
                result.add(matcher.group());
            } else {
                result.add(core);
            }
        }

        Collections.reverse(trailing);
        result.addAll(trailing);
    }

    private static boolean isOpenPunct(char c) {
        return c == '(' || c == '[' || c == '{';
    }

    private static boolean isClosePunct(char c) {
        return c == '.' || c == ',' || c == '!' || c == '?'
                || c == ';' || c == ':' || c == ')' || c == ']' || c == '}';
    }

    private WordTokenizer() {
    }
}
