package io.casehub.blocks.speech.sherpa;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class GectorTagApplier {

    public record Result(List<String> tokens, boolean changed) {
    }

    public static Result apply(List<String> tokens, int[] tagIds, GectorConfig config) {
        var vocabulary = config.tagVocabulary();
        int keepId = config.keepTagId();
        var result = new ArrayList<String>();
        boolean changed = false;

        for (int i = 0; i < tokens.size(); i++) {
            int tagId = tagIds[i];
            if (tagId == keepId) {
                result.add(tokens.get(i));
                continue;
            }

            String tag = vocabulary.get(tagId);
            String token = tokens.get(i);

            if (tag.equals("$DELETE")) {
                changed = true;
            } else if (tag.startsWith("$APPEND_")) {
                result.add(token);
                result.add(tag.substring("$APPEND_".length()));
                changed = true;
            } else if (tag.startsWith("$REPLACE_")) {
                result.add(tag.substring("$REPLACE_".length()));
                changed = true;
            } else if (tag.startsWith("$TRANSFORM_VERB_")) {
                String form = tag.substring("$TRANSFORM_VERB_".length());
                var wordForms = config.verbDictionary().get(token.toLowerCase());
                if (wordForms != null && wordForms.containsKey(form)) {
                    result.add(wordForms.get(form));
                    changed = true;
                } else {
                    result.add(token);
                }
            } else if (tag.equals("$TRANSFORM_CASE_CAPITAL")) {
                result.add(Character.toUpperCase(token.charAt(0)) + token.substring(1));
                changed = true;
            } else if (tag.equals("$TRANSFORM_CASE_LOWER")) {
                result.add(token.toLowerCase());
                changed = true;
            } else if (tag.equals("$TRANSFORM_CASE_UPPER")) {
                result.add(token.toUpperCase());
                changed = true;
            } else if (tag.equals("$TRANSFORM_AGREEMENT_SINGULAR")) {
                result.add(toSingular(token));
                changed = true;
            } else if (tag.equals("$TRANSFORM_AGREEMENT_PLURAL")) {
                result.add(toPlural(token));
                changed = true;
            } else if (tag.startsWith("$MERGE_")) {
                if (i + 1 < tokens.size()) {
                    String sep = tag.equals("$MERGE_HYPHEN") ? "-" : "";
                    result.add(token + sep + tokens.get(i + 1));
                    i++;
                    changed = true;
                } else {
                    result.add(token);
                }
            } else {
                result.add(token);
            }
        }

        return new Result(result, changed);
    }

    private static String toSingular(String word) {
        if (word.endsWith("ies")) return word.substring(0, word.length() - 3) + "y";
        if (word.endsWith("es")) return word.substring(0, word.length() - 2);
        if (word.endsWith("s")) return word.substring(0, word.length() - 1);
        return word;
    }

    private static String toPlural(String word) {
        if (word.endsWith("y") && word.length() > 1 && !isVowel(word.charAt(word.length() - 2)))
            return word.substring(0, word.length() - 1) + "ies";
        if (word.endsWith("s") || word.endsWith("x") || word.endsWith("z")
                || word.endsWith("ch") || word.endsWith("sh"))
            return word + "es";
        return word + "s";
    }

    private static boolean isVowel(char c) {
        return "aeiou".indexOf(Character.toLowerCase(c)) >= 0;
    }

    private GectorTagApplier() {
    }
}
