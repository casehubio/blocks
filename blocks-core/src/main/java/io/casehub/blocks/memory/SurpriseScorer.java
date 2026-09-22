package io.casehub.blocks.memory;

import io.casehub.neocortex.memory.cbr.CbrRecord;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.CbrMatch;
import io.casehub.neocortex.memory.experience.ContentScorer;
import io.casehub.neocortex.memory.experience.ScoreableContent;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public final class SurpriseScorer implements ContentScorer, ConfidenceScorer {

    @Override
    public double score(ScoreableContent content) {
        Map<String, String> metadata = content.metadata();
        if (metadata == null || metadata.isEmpty()) {return 0.5;}
        int distinctValues = 0;
        for (var value : metadata.values()) {
            distinctValues += value.length();
        }
        return Math.clamp(Math.log1p(distinctValues) / 10.0, 0.0, 1.0);
    }

    @Override
    public double score(CbrMatch<? extends CbrRecord> memory, Instant now) {
        return score(toScoreableContent(memory, now));
    }

    static ScoreableContent toScoreableContent(CbrMatch<? extends CbrRecord> memory, Instant now) {
        CbrRecord c    = memory.cbrRecord();
        String  text = c.problem();
        if (c.solution() != null) {
            text = text + " " + c.solution();
        }
        Map<String, String> metadata = new HashMap<>();
        if (c.features() != null) {
            for (var entry : c.features().entrySet()) {
                metadata.put(entry.getKey(), featureValueToString(entry.getValue()));
            }
        }
        Instant timestamp = memory.storedAt() != null ? memory.storedAt() : now;
        return new ScoreableContent(text != null ? text : "", metadata, timestamp);
    }

    private static String featureValueToString(FeatureValue fv) {
        if (fv instanceof FeatureValue.StringVal sv) {return sv.value();}
        if (fv instanceof FeatureValue.NumberVal nv) {return String.valueOf(nv.value());}
        if (fv instanceof FeatureValue.StringListVal sl) {return String.join(",", sl.values());}
        return fv.toString();
    }
}
