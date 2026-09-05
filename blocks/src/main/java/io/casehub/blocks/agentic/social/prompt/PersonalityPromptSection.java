package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.speech.PromptContext;
import io.casehub.blocks.speech.PromptSection;
import io.casehub.eidos.api.DispositionValue;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

public class PersonalityPromptSection implements PromptSection {

    private final @Nullable List<DispositionValue> profile;

    public PersonalityPromptSection(@Nullable List<DispositionValue> profile) {
        this.profile = profile;
    }

    @Override
    public @Nullable String contribute(PromptContext context) {
        if (profile == null || profile.isEmpty()) {
            return null;
        }
        var sorted = profile.stream()
                .sorted(Comparator.comparingDouble(DispositionValue::weight).reversed())
                .toList();
        var sb = new StringBuilder("Your personality traits:");
        for (var trait : sorted) {
            sb.append("\n- ").append(trait.term())
              .append(" (strength: ").append(String.format("%.1f", trait.weight())).append(")");
        }
        return sb.toString();
    }
}
