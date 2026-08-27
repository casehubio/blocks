package io.casehub.blocks.speech.ws;

import io.casehub.blocks.speech.PhonemeTiming;
import io.casehub.blocks.speech.ws.protocol.VisemeFrame;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class VisemeMapping {

    static final long MIN_DURATION_MS = 80;

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

    private static final Map<String, Double> VISEME_WEIGHT = Map.ofEntries(
            Map.entry("aa", 1.0),
            Map.entry("O", 0.9),
            Map.entry("E", 0.8),
            Map.entry("U", 0.8),
            Map.entry("PP", 0.8),
            Map.entry("I", 0.7),
            Map.entry("FF", 0.6),
            Map.entry("SS", 0.6),
            Map.entry("CH", 0.6),
            Map.entry("RR", 0.5),
            Map.entry("TH", 0.5),
            Map.entry("DD", 0.5),
            Map.entry("kk", 0.5),
            Map.entry("nn", 0.4),
            Map.entry("sil", 0.0));

    private VisemeMapping() {}

    public static String toViseme(String ipaPhoneme) {
        if (ipaPhoneme == null || ipaPhoneme.isEmpty()) {return "sil";}
        return IPA_TO_VISEME.getOrDefault(ipaPhoneme, "sil");
    }

    public static double weightFor(String viseme) {
        return VISEME_WEIGHT.getOrDefault(viseme, 0.5);
    }

    public static List<VisemeFrame> convert(List<PhonemeTiming> phonemes) {
        if (phonemes.isEmpty()) {return List.of();}
        var raw = buildRawFrames(phonemes);
        return enforceMinDuration(raw);
    }

    private static List<VisemeFrame> buildRawFrames(List<PhonemeTiming> phonemes) {
        var    result        = new ArrayList<VisemeFrame>();
        String currentViseme = null;
        long   currentStart  = 0;
        long   currentEnd    = 0;
        for (PhonemeTiming pt : phonemes) {
            String viseme = toViseme(pt.phoneme());
            if (viseme.equals(currentViseme)) {
                currentEnd = pt.endMs();
            } else {
                if (currentViseme != null) {
                    result.add(new VisemeFrame(currentViseme, currentStart, currentEnd, weightFor(currentViseme)));
                }
                currentViseme = viseme;
                currentStart  = pt.startMs();
                currentEnd    = pt.endMs();
            }
        }
        if (currentViseme != null) {
            result.add(new VisemeFrame(currentViseme, currentStart, currentEnd, weightFor(currentViseme)));
        }
        return result;
    }

    private static List<VisemeFrame> enforceMinDuration(List<VisemeFrame> frames) {
        if (frames.size() <= 1) {
            if (frames.size() == 1 && frames.getFirst().durationMs() < MIN_DURATION_MS) {
                var f = frames.getFirst();
                return List.of(new VisemeFrame(f.viseme(), f.startMs(), f.startMs() + MIN_DURATION_MS, f.weight()));
            }
            return List.copyOf(frames);
        }
        var result = new ArrayList<VisemeFrame>(frames.size());
        for (int i = 0; i < frames.size(); i++) {
            var frame = frames.get(i);
            if (frame.durationMs() < MIN_DURATION_MS) {
                long maxEnd = (i + 1 < frames.size()) ? frames.get(i + 1).startMs() : frame.startMs() + MIN_DURATION_MS;
                long newEnd = Math.min(frame.startMs() + MIN_DURATION_MS, maxEnd);
                result.add(new VisemeFrame(frame.viseme(), frame.startMs(), newEnd, frame.weight()));
            } else {
                result.add(frame);
            }
        }
        return List.copyOf(result);
    }
}
