package io.casehub.blocks.memory;

import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import io.casehub.neocortex.memory.experience.ContentScorer;
import io.casehub.neocortex.memory.experience.ScoreableContent;

import java.time.Instant;
import java.util.Set;

public final class ArousalScorer implements ContentScorer, ConfidenceScorer {

    private static final Set<String> HIGH_AROUSAL = Set.of(
            "critical", "emergency", "urgent", "failure", "crisis", "error",
            "escalation", "breach", "violation", "fatal", "severe", "alarm",
            "panic", "catastrophe", "danger", "threat", "attack", "outage",
            "incident", "alert", "warning", "shutdown", "corrupt", "exploit");

    @Override
    public double score(ScoreableContent content) {
        var text  = content.text().toLowerCase();
        var words = text.split("\\W+");
        if (words.length == 0) {return 0.0;}
        int hits = 0;
        for (var word : words) {
            if (HIGH_AROUSAL.contains(word)) {hits++;}
        }
        return Math.clamp((double) hits / words.length * 5.0, 0.0, 1.0);
    }

    @Override
    public double score(ScoredCbrCase<? extends CbrCase> memory, Instant now) {
        return score(SurpriseScorer.toScoreableContent(memory, now));
    }
}
