package io.casehub.blocks.speech.sherpa.correction;

import io.casehub.blocks.speech.CorrectionStrategy;

import java.util.List;

public final class SymSpellStrategy implements CorrectionStrategy {

    private final SymSpellIndex index;

    public SymSpellStrategy(SymSpellIndex index) {
        this.index = index;
    }

    @Override
    public List<Candidate> candidates(String word, CorrectionContext context) {
        return index.lookup(word).stream()
                .map(s -> new Candidate(s.word(),
                        1.0 - (s.distance() / 3.0),
                        "symspell"))
                .toList();
    }
}
