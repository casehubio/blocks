package io.casehub.blocks.speech.ws;

import io.casehub.blocks.speech.PhonemeTiming;
import io.casehub.blocks.speech.ws.protocol.VisemeFrame;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class VisemeMapping {

    private static final Map<String, String> IPA_TO_VISEME = Map.ofEntries(
            Map.entry("p", "PP"), Map.entry("b", "PP"), Map.entry("m", "PP"),
            Map.entry("f", "FF"), Map.entry("v", "FF"),
            Map.entry("θ", "TH"), Map.entry("ð", "TH"),
            Map.entry("t", "DD"), Map.entry("d", "DD"), Map.entry("l", "DD"),
            Map.entry("k", "kk"), Map.entry("g", "kk"), Map.entry("ŋ", "kk"),
            Map.entry("s", "SS"), Map.entry("z", "SS"),
            Map.entry("ʃ", "CH"), Map.entry("ʒ", "CH"),
            Map.entry("tʃ", "CH"), Map.entry("dʒ", "CH"),
            Map.entry("ɹ", "RR"), Map.entry("r", "RR"),
            Map.entry("n", "nn"),
            Map.entry("ɑ", "aa"), Map.entry("æ", "aa"),
            Map.entry("ʌ", "aa"), Map.entry("a", "aa"),
            Map.entry("ɛ", "E"), Map.entry("e", "E"), Map.entry("ə", "E"),
            Map.entry("ɪ", "I"), Map.entry("i", "I"),
            Map.entry("ɔ", "O"), Map.entry("o", "O"), Map.entry("ɒ", "O"),
            Map.entry("ʊ", "U"), Map.entry("u", "U"), Map.entry("w", "U"));

    private VisemeMapping() {}

    public static String toViseme(String ipaPhoneme) {
        if (ipaPhoneme == null || ipaPhoneme.isEmpty()) return "sil";
        return IPA_TO_VISEME.getOrDefault(ipaPhoneme, "sil");
    }

    public static List<VisemeFrame> convert(List<PhonemeTiming> phonemes) {
        if (phonemes.isEmpty()) return List.of();
        var result = new ArrayList<VisemeFrame>();
        String currentViseme = null;
        long currentStart = 0;
        long currentEnd = 0;
        for (PhonemeTiming pt : phonemes) {
            String viseme = toViseme(pt.phoneme());
            if (viseme.equals(currentViseme)) {
                currentEnd = pt.endMs();
            } else {
                if (currentViseme != null) {
                    result.add(new VisemeFrame(currentViseme, currentStart, currentEnd));
                }
                currentViseme = viseme;
                currentStart = pt.startMs();
                currentEnd = pt.endMs();
            }
        }
        if (currentViseme != null) {
            result.add(new VisemeFrame(currentViseme, currentStart, currentEnd));
        }
        return List.copyOf(result);
    }
}
