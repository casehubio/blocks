package io.casehub.blocks.agentic.yaml.llm;

import io.casehub.blocks.agentic.social.CognitionCore;
import io.casehub.blocks.agentic.social.CognitionSnapshot;
import io.casehub.blocks.agentic.social.MentalModelOrchestrator;
import io.casehub.blocks.agentic.social.MentalModelSnapshot;
import io.casehub.blocks.agentic.social.MentalModelStore;
import io.casehub.blocks.agentic.social.MoodOrchestrator;
import io.casehub.blocks.agentic.social.StrategyLearningOrchestrator;
import io.casehub.blocks.agentic.social.StrategyProfile;
import io.casehub.blocks.agentic.social.StrategyStore;
import io.casehub.blocks.agentic.social.UserModelOrchestrator;
import io.casehub.blocks.agentic.social.UserProfile;
import io.casehub.blocks.agentic.social.UserProfileStore;
import io.casehub.blocks.agentic.social.drive.AffiliationDrive;
import io.casehub.blocks.agentic.social.drive.AutonomyDrive;
import io.casehub.blocks.agentic.social.drive.CompetenceDrive;
import io.casehub.blocks.agentic.social.goal.AffiliationGoalMapper;
import io.casehub.blocks.agentic.social.goal.AutonomyGoalMapper;
import io.casehub.blocks.agentic.social.goal.CompetenceGoalMapper;
import io.casehub.blocks.agentic.social.goal.DriveGoalMapper;
import io.casehub.blocks.agentic.social.goal.CrossAxisGoalEnricher;
import io.casehub.blocks.agentic.social.goal.GoalEscalationPolicy;
import io.casehub.blocks.agentic.social.goal.GoalProposalOrchestrator;
import io.casehub.blocks.agentic.social.goal.DriveGoalFormationStrategy;
import io.casehub.eidos.api.GoalSignalStore;
import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.social.drive.DriveComposer;
import io.casehub.blocks.agentic.social.drive.DriveIntensity;
import io.casehub.blocks.agentic.social.drive.DriveOrchestrator;
import io.casehub.blocks.agentic.social.drive.DriveSource;
import io.casehub.blocks.agentic.social.narrative.DerivedTheme;
import io.casehub.blocks.agentic.social.narrative.IndividualEpisode;
import io.casehub.blocks.agentic.social.narrative.NarrativeFragment;
import io.casehub.blocks.agentic.social.narrative.NarrativeOrchestrator;
import io.casehub.blocks.agentic.social.narrative.NarrativeScope;
import io.casehub.blocks.agentic.social.narrative.NarrativeState;
import io.casehub.blocks.agentic.social.narrative.NarrativeStore;
import io.casehub.blocks.agentic.yaml.compiler.CompiledCognition;
import io.casehub.blocks.speech.PromptSection;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.neocortex.memory.cbr.inmem.InMemoryCbrCaseMemoryStore;
import io.casehub.platform.agent.AgentProvider;
import jakarta.enterprise.inject.Instance;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CognitionStack {

    private final CognitionCore core;
    private final Stage stage;
    private final NarrativeStore narrativeStore;

    public enum Stage {
        BASELINE, SIGNALS, REAL_DRIVES, NARRATIVE, FULL
    }

    CognitionStack(CognitionCore core, Stage stage,
                   NarrativeStore narrativeStore) {
        this.core = core;
        this.stage = stage;
        this.narrativeStore = narrativeStore;
    }

    public static CognitionStack from(CompiledCognition config,
                                       @Nullable AgentProvider agentProvider,
                                       Stage stage) {
        var mood = new MoodOrchestrator(config.mood());
        var narrativeStore = new InMemoryNarrativeStore();
        var narrative = new NarrativeOrchestrator(narrativeStore);
        var composer = new DriveComposer();

        UserModelOrchestrator userModel = null;
        MentalModelOrchestrator mentalModel = null;
        StrategyLearningOrchestrator strategy = null;

        if (agentProvider != null && stage.ordinal() >= Stage.SIGNALS.ordinal()) {
            userModel = new UserModelOrchestrator(
                    new InMemoryUserProfileStore(), agentProvider, config.userModel());
            mentalModel = new MentalModelOrchestrator(
                    new InMemoryMentalModelStore(), agentProvider, config.mentalModel());
        }

        DriveOrchestrator drives;
        GoalProposalOrchestrator goals = null;
        if (agentProvider != null && stage.ordinal() >= Stage.REAL_DRIVES.ordinal()) {
            strategy = new StrategyLearningOrchestrator(
                    new InMemoryStrategyStore(),
                    new InMemoryCbrCaseMemoryStore(),
                    (agentId, tenantId, since, maxEntries) -> List.of(),
                    agentProvider, config.strategyLearning());
            var competence = new CompetenceDrive(strategy);
            var affiliation = new AffiliationDrive(userModel, 0.3,
                    java.time.Duration.ofHours(1));
            var autonomy = new AutonomyDrive(mentalModel, 0.5);
            DriveSource curiosity = (a, t) ->
                    new DriveIntensity(DriveAxis.CURIOSITY, 0.5, "baseline");
            drives = new DriveOrchestrator(curiosity, competence,
                    affiliation, autonomy, mood, composer, config.drive());

            if (stage.ordinal() >= Stage.FULL.ordinal()) {
                var mapperList = List.<DriveGoalMapper>of(
                        new CompetenceGoalMapper(competence),
                        new AffiliationGoalMapper(affiliation, 0.3,
                                java.time.Duration.ofHours(1)),
                        new AutonomyGoalMapper(autonomy, 0.5));
                goals = new GoalProposalOrchestrator(drives,
                        listInstance(mapperList),
                        noOpInstance(),
                        noOpInstance(),
                        noOpInstance(),
                        noOpInstance(),
                        noOpInstance(),
                        config.goalProposal(),
                        config.goalEscalation());
            }
        } else {
            DriveSource baseline = (agentId, tenantId) ->
                    new DriveIntensity(DriveAxis.CURIOSITY, 0.5, "baseline");
            drives = new DriveOrchestrator(
                    baseline, baseline, baseline, baseline,
                    mood, composer, config.drive());
        }

        var core = new CognitionCore(mood, drives, userModel, mentalModel,
                strategy, narrative, goals, null);
        return new CognitionStack(core, stage, narrativeStore);
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

    public void updateNarrative(String agentId, String tenantId,
                                List<ConversationRunner.Turn> recentTurns) {
        if (stage.ordinal() < Stage.NARRATIVE.ordinal()) return;
        if (recentTurns.size() < 2) return;

        var now = Instant.now();
        var existing = narrativeStore.load(agentId, tenantId);
        var fragments = new ArrayList<NarrativeFragment>();
        if (existing != null) fragments.addAll(existing.fragments());

        var latest = recentTurns.subList(
                Math.max(0, recentTurns.size() - 2), recentTurns.size());
        var description = latest.get(0).speakerName() + " and "
                + latest.get(1).speakerName() + " discussed: "
                + latest.get(0).dialogue().substring(0,
                        Math.min(80, latest.get(0).dialogue().length()))
                + "...";
        var tags = List.of("exchange", "turn-" + latest.get(1).number());
        var episode = new IndividualEpisode(
                UUID.randomUUID().toString(), now, now, tags,
                description, 0.5, List.of());
        fragments.add(episode);

        if (fragments.stream().filter(f -> f instanceof IndividualEpisode)
                .count() >= 2) {
            var existingThemes = fragments.stream()
                    .filter(f -> f instanceof DerivedTheme).count();
            if (existingThemes == 0) {
                var theme = new DerivedTheme(
                        UUID.randomUUID().toString(), now, now,
                        List.of("dialogue", "connection"),
                        "shared-intellectual-curiosity", 0.7,
                        Map.of(DriveAxis.CURIOSITY, 0.3,
                                DriveAxis.AFFILIATION, 0.2),
                        fragments.stream()
                                .filter(f -> f instanceof IndividualEpisode)
                                .map(NarrativeFragment::id).toList());
                fragments.add(theme);
            }
        }

        var state = new NarrativeState(agentId, tenantId,
                NarrativeScope.INDIVIDUAL, fragments, now,
                recentTurns.size());
        narrativeStore.store(state);
    }

    @SuppressWarnings("unchecked")
    private static <T> Instance<T> noOpInstance() {
        return (Instance<T>) NO_OP_INSTANCE;
    }

    @SuppressWarnings("unchecked")
    private static <T> Instance<T> listInstance(List<T> items) {
        return new Instance<>() {
            @Override public Instance<T> select(java.lang.annotation.Annotation... q) { return this; }
            @Override public <U extends T> Instance<U> select(Class<U> s, java.lang.annotation.Annotation... q) { throw new UnsupportedOperationException(); }
            @Override public <U extends T> Instance<U> select(jakarta.enterprise.util.TypeLiteral<U> s, java.lang.annotation.Annotation... q) { throw new UnsupportedOperationException(); }
            @Override public boolean isUnsatisfied() { return items.isEmpty(); }
            @Override public boolean isAmbiguous() { return items.size() > 1; }
            @Override public boolean isResolvable() { return items.size() == 1; }
            @Override public void destroy(T instance) {}
            @Override public Handle<T> getHandle() { throw new UnsupportedOperationException(); }
            @Override public Iterable<? extends Handle<T>> handles() { return List.of(); }
            @Override public T get() { return items.getFirst(); }
            @Override public java.util.Iterator<T> iterator() { return items.iterator(); }
            @Override public java.util.stream.Stream<T> stream() { return items.stream(); }
        };
    }

    private static final Instance<?> NO_OP_INSTANCE = new Instance<>() {
        @Override public Instance<Object> select(java.lang.annotation.Annotation... qualifiers) { return this; }
        @Override public <U extends Object> Instance<U> select(Class<U> subtype, java.lang.annotation.Annotation... qualifiers) { throw new UnsupportedOperationException(); }
        @Override public <U extends Object> Instance<U> select(jakarta.enterprise.util.TypeLiteral<U> subtype, java.lang.annotation.Annotation... qualifiers) { throw new UnsupportedOperationException(); }
        @Override public boolean isUnsatisfied() { return true; }
        @Override public boolean isAmbiguous() { return false; }
        @Override public boolean isResolvable() { return false; }
        @Override public void destroy(Object instance) {}
        @Override public Handle<Object> getHandle() { throw new UnsupportedOperationException(); }
        @Override public Iterable<? extends Handle<Object>> handles() { return List.of(); }
        @Override public Object get() { throw new UnsupportedOperationException(); }
        @Override public java.util.Iterator<Object> iterator() { return java.util.Collections.emptyIterator(); }
    };

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

    static final class InMemoryStrategyStore implements StrategyStore {
        private final Map<String, StrategyProfile> profiles = new ConcurrentHashMap<>();

        @Override public void store(StrategyProfile profile) {
            profiles.put(profile.agentId() + ":" + profile.tenantId(), profile);
        }

        @Override public Optional<StrategyProfile> lookup(String agentId, String tenantId) {
            return Optional.ofNullable(profiles.get(agentId + ":" + tenantId));
        }

        @Override public List<String> subjectInsights(String agentId, String subjectId, String tenantId) {
            return List.of();
        }

        @Override public void eraseAgent(String agentId, String tenantId) {
            profiles.remove(agentId + ":" + tenantId);
        }

        @Override public void eraseSubject(String subjectId, String tenantId) {}
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
