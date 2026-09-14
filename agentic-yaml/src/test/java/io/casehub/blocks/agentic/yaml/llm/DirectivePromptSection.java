package io.casehub.blocks.agentic.yaml.llm;

import io.casehub.blocks.speech.PromptContext;
import io.casehub.blocks.speech.PromptSection;
import org.jspecify.annotations.Nullable;

class DirectivePromptSection implements PromptSection {

    private final PromptSection delegate;
    private final String directive;

    DirectivePromptSection(PromptSection delegate, String directive) {
        this.delegate = delegate;
        this.directive = directive;
    }

    @Override
    public @Nullable String contribute(PromptContext context) {
        var content = delegate.contribute(context);
        if (content == null || content.isBlank()) return null;
        return directive + "\n" + content;
    }

    static PromptSection wrap(PromptSection section) {
        var name = section.getClass().getSimpleName();
        var directive = switch (name) {
            case "MoodPromptSection" ->
                    "Your emotional state shapes how you speak — warmth shows in generosity of thought, " +
                    "arousal in the pace and intensity of your words, dominance in whether you assert or defer. " +
                    "Let these feelings color your response naturally:";
            case "DrivePromptSection" ->
                    "These motivations pull at you right now. The strongest drive should steer what you " +
                    "choose to talk about — high curiosity means you ask probing questions, high affiliation " +
                    "means you seek connection, high competence means you demonstrate mastery:";
            case "MentalModelPromptSection" ->
                    "This is what you have come to believe about the person you are speaking with, based on " +
                    "what they have said and done. Reference these beliefs naturally — build on shared values, " +
                    "probe where you sense their desires, respond to their intentions:";
            case "UserModelPromptSection" ->
                    "This is your sense of who this person is and how your relationship has developed. " +
                    "Adjust your tone and depth accordingly — strangers get careful formality, " +
                    "familiar companions get directness and warmth:";
            case "NarrativePromptSection" ->
                    "These are the significant moments from your shared history — the episodes that " +
                    "defined your relationship and the themes that emerged. Draw on these memories " +
                    "naturally in conversation — reference past moments, build on established themes:";
            case "GoalPromptSection" ->
                    "These goals have emerged from your inner motivations. Actively work toward them " +
                    "in the conversation — steer discussion toward goal-relevant topics, ask questions " +
                    "that advance your goals, share insights that serve them:";
            case "StrategyPromptSection" ->
                    "These are interaction strategies you have learned work well. Apply them:";
            default -> "Consider this context in your response:";
        };
        return new DirectivePromptSection(section, directive);
    }
}
