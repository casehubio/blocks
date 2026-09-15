package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.agentic.social.UserModelOrchestrator;
import io.casehub.blocks.agentic.social.UserProfile;
import io.casehub.blocks.speech.PromptContext;
import io.casehub.blocks.speech.PromptSection;
import org.jspecify.annotations.Nullable;

public class UserModelPromptSection implements PromptSection {

    private final UserModelOrchestrator userModel;

    public UserModelPromptSection(UserModelOrchestrator userModel) {
        this.userModel = userModel;
    }

    @Override
    public @Nullable String contribute(PromptContext context) {
        if (context.subjectId() == null) {
            return null;
        }
        var profile = userModel.currentProfile(context.agentId(), context.subjectId(), context.tenantId());
        if (profile == null) {
            return null;
        }
        return render(profile);
    }

    private static String render(UserProfile profile) {
        var sb = new StringBuilder("User profile (" + profile.subjectId() + "):");
        sb.append("\n- Familiarity: ").append(String.format("%.1f", profile.familiarityScore()));
        sb.append("\n- Relationship stage: ").append(profile.relationshipStage());
        sb.append("\n- Interactions: ").append(profile.totalInteractions());
        if (profile.communicationStyle() != null) {
            sb.append("\n- Communication style: ").append(profile.communicationStyle());
        }
        if (profile.topicsOfInterest() != null) {
            sb.append("\n- Topics of interest: ").append(profile.topicsOfInterest());
        }
        if (profile.preferences() != null) {
            sb.append("\n- Preferences: ").append(profile.preferences());
        }
        return sb.toString();
    }
}
