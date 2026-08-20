package io.casehub.blocks.memory;

import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;

import java.time.Instant;
import java.util.Map;

public final class SurpriseScorer implements ImportanceScorer {

    @Override
    public double score(ScoredCbrCase<? extends CbrCase> memory, Instant now) {
        Map<String, FeatureValue> features = memory.cbrCase().features();
        if (features == null || features.isEmpty()) {return 0.5;}
        int distinctValues = 0;
        for (var fv : features.values()) {
            if (fv instanceof FeatureValue.StringVal sv) {
                distinctValues += sv.value().length();
            } else if (fv instanceof FeatureValue.NumberVal) {
                distinctValues += 1;
            } else if (fv instanceof FeatureValue.StringListVal sl) {
                distinctValues += sl.values().size();
            } else {
                distinctValues += 1;
            }
        }
        return Math.clamp(Math.log1p(distinctValues) / 10.0, 0.0, 1.0);
    }
}
