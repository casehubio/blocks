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
        var label    = emotionLabel(state.pleasure(), state.arousal(), state.dominance());
        var coloring = behavioralColoring(state.pleasure(), state.arousal(), state.dominance());
        return "Current emotional state:\nYou're feeling " + label + ".\n" + coloring;
    }

    public static String emotionLabel(double p, double a, double d) {
        String primary;
        if (p > 0.3 && a > 0.3) {primary = "excited and energized";} else if (p > 0.3 && a < -0.3) {
            primary = "content and serene";
        } else if (p > 0.3) {
            primary = "pleased";
        } else if (p < -0.3 && a > 0.3) {
            primary = "tense and agitated";
        } else if (p < -0.3 && a < -0.3) {
            primary = "subdued and low";
        } else if (p < -0.3) {
            primary = "displeased";
        } else if (a > 0.3) {
            primary = "alert and keyed up";
        } else if (a < -0.3) {primary = "calm and unhurried";} else {
            primary = "emotionally even";
        }

        if (d > 0.3) {return primary + ", with a sense of confidence";}
        if (d < -0.3) {return primary + ", with an undercurrent of uncertainty";}
        return primary;
    }

    public static String behavioralColoring(double p, double a, double d) {
        var cues = new java.util.ArrayList<String>();
        if (p > 0.3) {cues.add("more generous and open");}
        if (p < -0.3) {cues.add("more guarded and short-tempered");}
        if (a > 0.3) {cues.add("quicker to react and more impulsive");}
        if (a < -0.3) {cues.add("more measured and deliberate");}
        if (d > 0.3) {cues.add("more assertive and commanding");}
        if (d < -0.3) {cues.add("more hesitant and deferential");}
        if (cues.isEmpty()) {return "This doesn't noticeably color your behavior.";}
        return "This colors your responses — you're " + String.join(", ", cues) + ".";
    }


}
