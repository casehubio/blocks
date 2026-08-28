package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.PhonemeAligner;
import io.casehub.blocks.speech.PhonemeTiming;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class EspeakPhonemeAligner implements PhonemeAligner {

    @FunctionalInterface
    interface Phonemizer {
        String phonemize(String text, String voice);
    }

    private final Phonemizer phonemizer;
    private final String voice;

    EspeakPhonemeAligner(Phonemizer phonemizer) {
        this(phonemizer, "en");
    }

    EspeakPhonemeAligner(Phonemizer phonemizer, String voice) {
        this.phonemizer = phonemizer;
        this.voice = voice;
    }

    public static EspeakPhonemeAligner withEspeak(EspeakLibrary espeak) {
        return new EspeakPhonemeAligner(espeak::textToPhonemes);
    }

    @Override
    public List<PhonemeTiming> align(String text, byte[] audioData, int sampleRate) {
        if (text == null || text.isBlank()) return List.of();

        String ipa = phonemizer.phonemize(text, voice);
        if (ipa == null || ipa.isBlank()) return List.of();

        List<String> phonemes = splitIpaPhonemes(ipa);
        if (phonemes.isEmpty()) return List.of();

        long audioDurationMs = estimateAudioDurationMs(audioData, sampleRate);
        if (audioDurationMs <= 0) return List.of();

        long perPhonemeMs = audioDurationMs / phonemes.size();
        long remainder = audioDurationMs % phonemes.size();

        var result = new ArrayList<PhonemeTiming>(phonemes.size());
        long cursor = 0;
        for (int i = 0; i < phonemes.size(); i++) {
            long duration = perPhonemeMs + (i < remainder ? 1 : 0);
            result.add(new PhonemeTiming(phonemes.get(i), cursor, cursor + duration));
            cursor += duration;
        }
        return List.copyOf(result);
    }

    static List<String> splitIpaPhonemes(String ipa) {
        var phonemes = new ArrayList<String>();
        int i = 0;
        while (i < ipa.length()) {
            int cp = ipa.codePointAt(i);
            int len = Character.charCount(cp);
            if (Character.isWhitespace(cp)) {
                i += len;
                continue;
            }
            int start = i;
            i += len;
            while (i < ipa.length()) {
                int next = ipa.codePointAt(i);
                if (Character.isWhitespace(next) || isIpaBaseCharacter(next)) {
                    break;
                }
                i += Character.charCount(next);
            }
            phonemes.add(ipa.substring(start, i));
        }
        return phonemes;
    }

    private static boolean isIpaBaseCharacter(int cp) {
        return Character.isLetter(cp) || cp == 'ʔ' || cp == 'ʕ' || cp == 'ɾ'
                || cp == 'ɹ' || cp == 'ɐ' || cp == 'ə' || cp == 'ɛ'
                || cp == 'ɪ' || cp == 'ʊ' || cp == 'ɔ' || cp == 'ʃ'
                || cp == 'ʒ' || cp == 'θ' || cp == 'ð' || cp == 'ŋ'
                || cp == 'ɑ' || cp == 'æ';
    }

    private static long estimateAudioDurationMs(byte[] wavData, int sampleRate) {
        try {
            WavData wav = WavReader.parse(wavData);
            return (long) (wav.samples().length * 1000.0 / wav.sampleRate());
        } catch (IOException e) {
            int numSamples = wavData.length / 2;
            return (long) (numSamples * 1000.0 / sampleRate);
        }
    }
}
