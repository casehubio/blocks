package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.agentic.social.MoodOrchestrator;
import io.casehub.blocks.speech.PromptContext;
import io.casehub.blocks.speech.PromptSection;
import io.casehub.neocortex.memory.mood.MoodState;
import org.jspecify.annotations.Nullable;

public class MoodPromptSection implements PromptSection {

    private final MoodOrchestrator mood;

    public MoodPromptSection(MoodOrchestrator mood) {
        this.mood = mood;
    }

    @Override
    public @Nullable String contribute(PromptContext context) {
        return mood.currentMood(context.agentId(), context.tenantId())
                .map(MoodPromptSection::render)
                .orElse(null);
    }

    private static String render(MoodState state) {
        var sb = new StringBuilder("Current emotional state:");
        sb.append("\n- Pleasure: ").append(String.format("%.2f", state.pleasure()))
          .append(" (").append(interpret(state.pleasure(), "positive", "negative", "neutral")).append(")");
        sb.append("\n- Arousal: ").append(String.format("%.2f", state.arousal()))
          .append(" (").append(interpret(state.arousal(), "energetic", "calm", "balanced")).append(")");
        sb.append("\n- Dominance: ").append(String.format("%.2f", state.dominance()))
          .append(" (").append(interpret(state.dominance(), "confident", "submissive", "balanced")).append(")");
        return sb.toString();
    }

    private static String interpret(double value, String high, String low, String neutral) {
        if (value > 0.3) return high;
        if (value < -0.3) return low;
        return neutral;
    }
}
