package io.casehub.blocks.agentic.yaml.llm;

import io.casehub.blocks.agentic.social.CognitionCore;
import io.casehub.blocks.agentic.social.CognitionSnapshot;
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
import io.casehub.blocks.agentic.yaml.compiler.CompiledCognition;
import io.casehub.blocks.speech.PromptSection;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.platform.agent.AgentProvider;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CognitionStack {

    private final CognitionCore core;
    private final Stage stage;

    public enum Stage {
        BASELINE, SIGNALS, REAL_DRIVES, NARRATIVE, FULL
    }

    CognitionStack(CognitionCore core, Stage stage) {
        this.core = core;
        this.stage = stage;
    }

    public static CognitionStack from(CompiledCognition config,
                                       @Nullable AgentProvider agentProvider,
                                       Stage stage) {
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
        if (agentProvider != null && stage.ordinal() >= Stage.SIGNALS.ordinal()) {
            userModel = new UserModelOrchestrator(
                    new InMemoryUserProfileStore(), agentProvider, config.userModel());
            mentalModel = new MentalModelOrchestrator(
                    new InMemoryMentalModelStore(), agentProvider, config.mentalModel());
        }

        var core = new CognitionCore(mood, drives, userModel, mentalModel,
                null, narrative, null, null);
        return new CognitionStack(core, stage);
    }

    public static CognitionStack from(CompiledCognition config,
                                       @Nullable AgentProvider agentProvider) {
        return from(config, agentProvider, Stage.SIGNALS);
    }

    public CognitionCore core() { return core; }
    public Stage stage() { return stage; }

    public void tick(String agentId, String tenantId,
                     @Nullable AgentDescriptor descriptor) {
        core.tick(agentId, tenantId, descriptor, Set.of());
    }

    public void tick(String agentId, String tenantId,
                     @Nullable AgentDescriptor descriptor,
                     Set<String> activeSubjects) {
        core.tick(agentId, tenantId, descriptor, activeSubjects);
    }

    public List<PromptSection> promptSections() {
        return core.promptSections();
    }

    public CognitionSnapshot snapshot(String agentId, String tenantId,
                                       int turnNumber, Set<String> subjectIds) {
        return CognitionSnapshot.capture(core, agentId, tenantId,
                turnNumber, subjectIds);
    }

    public MoodOrchestrator mood() { return core.mood(); }
    public DriveOrchestrator drives() { return core.drives(); }
    public NarrativeOrchestrator narrative() { return core.narrative(); }
    public @Nullable UserModelOrchestrator userModel() { return core.userModel(); }
    public @Nullable MentalModelOrchestrator mentalModel() { return core.mentalModel(); }

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
