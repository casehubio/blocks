package io.casehub.blocks.speech.sherpa.correction;

import io.casehub.blocks.speech.CorrectionStrategy;

import java.util.List;

public final class PhoneticStrategy implements CorrectionStrategy {

    private static final double PHONETIC_CONFIDENCE = 0.6;

    private final PhoneticIndex index;

    public PhoneticStrategy(PhoneticIndex index) {
        this.index = index;
    }

    @Override
    public List<Candidate> candidates(String word, CorrectionContext context) {
        return index.lookup(word).stream()
                .map(match -> new Candidate(match, PHONETIC_CONFIDENCE, "phonetic"))
                .toList();
    }
}
