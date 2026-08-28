package io.casehub.blocks.speech.sherpa.correction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PhoneticIndex {

    private final DoubleMetaphone dm = new DoubleMetaphone();
    private final Map<String, Set<String>> codeToWords = new HashMap<>();

    public List<String> lookup(String word) {
        String[] codes = dm.compute(word);
        var result = new HashSet<String>();
        for (String code : codes) {
            if (code != null && !code.isEmpty()) {
                Set<String> matches = codeToWords.get(code);
                if (matches != null) {
                    result.addAll(matches);
                }
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(result));
    }

    public void addWord(String word) {
        String lower = word.toLowerCase();
        String[] codes = dm.compute(lower);
        for (String code : codes) {
            if (code != null && !code.isEmpty()) {
                codeToWords.computeIfAbsent(code, k -> new HashSet<>()).add(lower);
            }
        }
    }

    public static PhoneticIndex fromWords(List<String> words) {
        var index = new PhoneticIndex();
        for (String word : words) {
            index.addWord(word);
        }
        return index;
    }

    public static PhoneticIndex fromSymSpellIndex(SymSpellIndex symSpellIndex) {
        return fromWords(new ArrayList<>(symSpellIndex.dictionary()));
    }
}
