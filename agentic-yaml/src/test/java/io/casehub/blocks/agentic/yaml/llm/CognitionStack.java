package io.casehub.blocks.agentic.yaml.llm;

import io.casehub.blocks.agentic.social.MentalModelOrchestrator;
import io.casehub.blocks.agentic.social.MentalModelSnapshot;
import io.casehub.blocks.agentic.social.MentalModelStore;
import io.casehub.blocks.agentic.social.MoodOrchestrator;
import io.casehub.blocks.agentic.social.UserModelOrchestrator;
import io.casehub.blocks.agentic.social.UserProfile;
import io.casehub.blocks.agentic.social.UserProfileStore;
import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.social.drive.DriveComposer;
import io.casehub.blocks.agentic.social.drive.DriveIntensity;
import io.casehub.blocks.agentic.social.drive.DriveOrchestrator;
import io.casehub.blocks.agentic.social.drive.DriveSource;
import io.casehub.blocks.agentic.social.narrative.NarrativeOrchestrator;
import io.casehub.blocks.agentic.social.narrative.NarrativeState;
import io.casehub.blocks.agentic.social.narrative.NarrativeStore;
import io.casehub.blocks.agentic.social.prompt.DrivePromptSection;
import io.casehub.blocks.agentic.social.prompt.MentalModelPromptSection;
import io.casehub.blocks.agentic.social.prompt.MoodPromptSection;
import io.casehub.blocks.agentic.social.prompt.NarrativePromptSection;
import io.casehub.blocks.agentic.social.prompt.UserModelPromptSection;
import io.casehub.blocks.agentic.yaml.compiler.CompiledCognition;
import io.casehub.blocks.speech.PromptSection;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.platform.agent.AgentProvider;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class CognitionStack {

    private final MoodOrchestrator mood;
    private final DriveOrchestrator drives;
    private final @Nullable UserModelOrchestrator userModel;
    private final @Nullable MentalModelOrchestrator mentalModel;
    private final NarrativeOrchestrator narrative;

    CognitionStack(MoodOrchestrator mood,
                   DriveOrchestrator drives,
                   @Nullable UserModelOrchestrator userModel,
                   @Nullable MentalModelOrchestrator mentalModel,
                   NarrativeOrchestrator narrative) {
        this.mood = mood;
        this.drives = drives;
        this.userModel = userModel;
        this.mentalModel = mentalModel;
        this.narrative = narrative;
    }

    public static CognitionStack from(CompiledCognition config,
                                       @Nullable AgentProvider agentProvider) {
        var mood = new MoodOrchestrator(config.mood());
        var narrative = new NarrativeOrchestrator(new InMemoryNarrativeStore());

        DriveSource baseline = (agentId, tenantId) ->
                new DriveIntensity(DriveAxis.CURIOSITY, 0.5, "baseline");
        var composer = new DriveComposer();
        var drives = new DriveOrchestrator(
                baseline, baseline, baseline, baseline,
                mood, composer, config.drive());

        UserModelOrchestrator userModel = null;
        MentalModelOrchestrator mentalModel = null;
        if (agentProvider != null) {
            userModel = new UserModelOrchestrator(
                    new InMemoryUserProfileStore(), agentProvider, config.userModel());
            mentalModel = new MentalModelOrchestrator(
                    new InMemoryMentalModelStore(), agentProvider, config.mentalModel());
        }

        return new CognitionStack(mood, drives, userModel, mentalModel, narrative);
    }

    public void tick(String agentId, String tenantId,
                     @Nullable AgentDescriptor descriptor) {
        if (mood.currentMood(agentId, tenantId).isEmpty()) {
            mood.record(new io.casehub.blocks.agentic.social.MoodSignal.InteractionAppraisal(
                    0, 0, 0, "initialization"), agentId, tenantId);
        }
        mood.tick(agentId, tenantId);
        narrative.tick(agentId, tenantId);
        if (descriptor != null) {
            drives.tick(agentId, tenantId, descriptor);
        }
    }

    public List<PromptSection> promptSections() {
        var sections = new ArrayList<PromptSection>();
        sections.add(new MoodPromptSection(mood));
        sections.add(new DrivePromptSection(drives));
        sections.add(new NarrativePromptSection(narrative));
        if (userModel != null) sections.add(new UserModelPromptSection(userModel));
        if (mentalModel != null) sections.add(new MentalModelPromptSection(mentalModel));
        return sections;
    }

    public MoodOrchestrator mood() { return mood; }
    public DriveOrchestrator drives() { return drives; }
    public NarrativeOrchestrator narrative() { return narrative; }
    public @Nullable UserModelOrchestrator userModel() { return userModel; }
    public @Nullable MentalModelOrchestrator mentalModel() { return mentalModel; }

    static final class InMemoryNarrativeStore implements NarrativeStore {
        private final Map<String, NarrativeState> states = new ConcurrentHashMap<>();

        @Override public void store(NarrativeState state) {
            states.put(state.scopeId() + ":" + state.tenantId(), state);
        }

        @Override public @Nullable NarrativeState load(String scopeId, String tenantId) {
            return states.get(scopeId + ":" + tenantId);
        }
    }

    static final class InMemoryUserProfileStore implements UserProfileStore {
        private final Map<String, UserProfile> profiles = new ConcurrentHashMap<>();

        @Override public void store(UserProfile profile) {
            profiles.put(key(profile.agentId(), profile.subjectId(), profile.tenantId()), profile);
        }

        @Override public Optional<UserProfile> lookup(String agentId, String subjectId, String tenantId) {
            return Optional.ofNullable(profiles.get(key(agentId, subjectId, tenantId)));
        }

        @Override public List<UserProfile> findByAgent(String agentId, String tenantId) {
            return profiles.values().stream()
                    .filter(p -> p.agentId().equals(agentId) && p.tenantId().equals(tenantId))
                    .toList();
        }

        @Override public void eraseSubject(String subjectId, String tenantId) {
            profiles.entrySet().removeIf(e ->
                    e.getValue().subjectId().equals(subjectId) && e.getValue().tenantId().equals(tenantId));
        }

        private static String key(String agentId, String subjectId, String tenantId) {
            return agentId + ":" + subjectId + ":" + tenantId;
        }
    }

    static final class InMemoryMentalModelStore implements MentalModelStore {
        private final Map<String, MentalModelSnapshot> snapshots = new ConcurrentHashMap<>();

        @Override public void store(MentalModelSnapshot snapshot) {
            snapshots.put(key(snapshot.agentId(), snapshot.subjectId(), snapshot.tenantId()), snapshot);
        }

        @Override public Optional<MentalModelSnapshot> lookup(String agentId, String subjectId, String tenantId) {
            return Optional.ofNullable(snapshots.get(key(agentId, subjectId, tenantId)));
        }

        @Override public List<MentalModelSnapshot> findByAgent(String agentId, String tenantId) {
            return snapshots.values().stream()
                    .filter(s -> s.agentId().equals(agentId) && s.tenantId().equals(tenantId))
                    .toList();
        }

        @Override public void eraseSubject(String subjectId, String tenantId) {
            snapshots.entrySet().removeIf(e ->
                    e.getValue().subjectId().equals(subjectId) && e.getValue().tenantId().equals(tenantId));
        }

        private static String key(String agentId, String subjectId, String tenantId) {
            return agentId + ":" + subjectId + ":" + tenantId;
        }
    }
}
