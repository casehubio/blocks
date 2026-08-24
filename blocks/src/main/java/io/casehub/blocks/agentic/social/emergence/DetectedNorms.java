package io.casehub.blocks.agentic.social.emergence;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record DetectedNorms(
        List<SocialNorm> norms,
        int observationsAnalysed,
        Instant analysedAt) {
    public DetectedNorms {
        norms = List.copyOf(norms);
        Objects.requireNonNull(analysedAt);
    }

    public List<SocialNorm> established() {
        return norms.stream()
                .filter(n -> n.strength() == NormStrength.ESTABLISHED)
                .toList();
    }
}
