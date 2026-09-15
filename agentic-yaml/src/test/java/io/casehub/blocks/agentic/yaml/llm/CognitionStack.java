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
import java.util.stream.Collectors;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.neocortex.memory.cbr.inmem.InMemoryCbrCaseMemoryStore;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
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

    private static final System.Logger LOG =
            System.getLogger(CognitionStack.class.getName());

    private final CognitionCore core;
    private final Stage stage;
    private final NarrativeStore narrativeStore;
    private final @Nullable AgentProvider agentProvider;
    private final @Nullable io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore cbrStore;
    private final @Nullable io.casehub.blocks.agentic.social.StrategyLearningConfig strategyConfig;
    private final ConcurrentHashMap<String, Boolean> primedAgents = new ConcurrentHashMap<>();

    public enum Stage {
        BASELINE, SIGNALS, REAL_DRIVES, NARRATIVE, FULL
    }

    CognitionStack(CognitionCore core, Stage stage,
                   NarrativeStore narrativeStore,
                   @Nullable AgentProvider agentProvider,
                   @Nullable io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore cbrStore,
                   @Nullable io.casehub.blocks.agentic.social.StrategyLearningConfig strategyConfig) {
        this.core = core;
        this.stage = stage;
        this.narrativeStore = narrativeStore;
        this.agentProvider = agentProvider;
        this.cbrStore = cbrStore;
        this.strategyConfig = strategyConfig;
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
        io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore cbrStore = null;

        if (agentProvider != null && stage.ordinal() >= Stage.SIGNALS.ordinal()) {
            userModel = new UserModelOrchestrator(
                    new InMemoryUserProfileStore(), null, config.userModel());
            mentalModel = new MentalModelOrchestrator(
                    new InMemoryMentalModelStore(), null, config.mentalModel());
        }

        DriveOrchestrator drives;
        GoalProposalOrchestrator goals = null;
        if (agentProvider != null && stage.ordinal() >= Stage.REAL_DRIVES.ordinal()) {
            cbrStore = new InMemoryCbrCaseMemoryStore();
            strategy = new StrategyLearningOrchestrator(
                    new InMemoryStrategyStore(),
                    cbrStore,
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
                strategy, narrative, goals, null, agentProvider);
        return new CognitionStack(core, stage, narrativeStore, agentProvider,
                cbrStore, cbrStore != null ? config.strategyLearning() : null);
    }

    public static CognitionStack from(CompiledCognition config,
                                       @Nullable AgentProvider agentProvider) {
        return from(config, agentProvider, Stage.SIGNALS);
    }

    public CognitionCore core() { return core; }
    public Stage stage() { return stage; }

    public void tick(String agentId, String tenantId,
                     @Nullable AgentDescriptor descriptor) {
        primeIfNeeded(agentId, tenantId, descriptor);
        core.tick(agentId, tenantId, descriptor, (a, t) -> Set.of());
    }

    public void tick(String agentId, String tenantId,
                     @Nullable AgentDescriptor descriptor,
                     Set<String> activeSubjects) {
        primeIfNeeded(agentId, tenantId, descriptor);
        core.tick(agentId, tenantId, descriptor, (a, t) -> activeSubjects);
    }

    private void primeIfNeeded(String agentId, String tenantId,
                                @Nullable AgentDescriptor descriptor) {
        if (stage.ordinal() < Stage.REAL_DRIVES.ordinal()) return;
        if (descriptor == null || cbrStore == null || strategyConfig == null) return;
        if (primedAgents.putIfAbsent(agentId + ":" + tenantId, true) != null) return;

        var constraints = descriptor.constraints();
        if (constraints == null || constraints.isEmpty()) return;

        for (int i = 0; i < strategyConfig.minCasesForReflection(); i++) {
            var features = buildSyntheticFeatures(descriptor, i, strategyConfig.minCasesForReflection());
            var summary = "Synthetic interaction " + (i + 1) + " derived from "
                    + constraints.getFirst().text();
            var cbrCase = new io.casehub.neocortex.memory.cbr.FeatureVectorCbrCase(
                    summary, "-", null, null, features, null, agentId);
            cbrStore.store(cbrCase, strategyConfig.engagementCaseType(),
                    agentId, strategyConfig.memoryDomain(), tenantId, null,
                    io.casehub.platform.api.path.Path.root());
        }
    }

    private static Map<String, io.casehub.neocortex.memory.cbr.FeatureValue> buildSyntheticFeatures(
            AgentDescriptor descriptor, int index, int count) {
        double progress = count > 1 ? (double) index / (count - 1) : 0.5;

        var features = new java.util.LinkedHashMap<String, io.casehub.neocortex.memory.cbr.FeatureValue>();
        features.put("subjectId", io.casehub.neocortex.memory.cbr.FeatureValue.string("synthetic"));
        features.put("agentId", io.casehub.neocortex.memory.cbr.FeatureValue.string(descriptor.name()));
        features.put("conversationTimestamp",
                io.casehub.neocortex.memory.cbr.FeatureValue.number(
                        (double) (Instant.now().toEpochMilli() - (count - index) * 3600_000L)));
        features.put("turnCount", io.casehub.neocortex.memory.cbr.FeatureValue.number(4.0 + index));
        features.put("avgResponseLength", io.casehub.neocortex.memory.cbr.FeatureValue.number(80 + progress * 170));
        features.put("continuationRate", io.casehub.neocortex.memory.cbr.FeatureValue.number(0.6 + progress * 0.25));
        features.put("meanAffectShift", io.casehub.neocortex.memory.cbr.FeatureValue.number(-0.05 + progress * 0.25));

        double formality = 0.5;
        double verbosity = 0.5;
        if (descriptor.constraints() != null) {
            for (var c : descriptor.constraints()) {
                var text = c.text().toLowerCase();
                if (text.contains("precision") || text.contains("exact") || text.contains("rigour")) {
                    formality = 0.8; verbosity = 0.3;
                } else if (text.contains("artistic") || text.contains("creative") || text.contains("expressive")) {
                    formality = 0.4; verbosity = 0.7;
                }
            }
        }
        features.put("avgSnapshot_verbosity", io.casehub.neocortex.memory.cbr.FeatureValue.number(verbosity));
        features.put("avgSnapshot_formality", io.casehub.neocortex.memory.cbr.FeatureValue.number(formality));
        features.put("avgSnapshot_initiative", io.casehub.neocortex.memory.cbr.FeatureValue.number(0.5));
        features.put("avgSnapshot_directness", io.casehub.neocortex.memory.cbr.FeatureValue.number(0.5));
        features.put("avgSnapshot_questionRate", io.casehub.neocortex.memory.cbr.FeatureValue.number(0.5));

        return Map.copyOf(features);
    }

    public List<PromptSection> promptSections() {
        return core.promptSections();
    }

    public CognitionSnapshot snapshot(String agentId, String tenantId,
                                       int turnNumber, Set<String> subjectIds) {
        return CognitionSnapshot.capture(core, agentId, tenantId,
                turnNumber, subjectIds);
    }

    private static final String EPISODE_PROMPT = """
            You are analysing a conversation between historical figures. \
            Extract the most significant moment from the latest exchange.
            
            Respond ONLY with JSON:
            {"description":"One sentence describing what happened and why it matters",\
            "valence":0.5,\
            "tags":["tag1","tag2"]}
            
            valence: emotional significance from -1 (deeply negative) to +1 (deeply positive)
            tags: 2-4 thematic tags (e.g. "intellectual-recognition", "shared-vulnerability")""";

    private static final String THEME_PROMPT = """
            You are analysing a series of narrative episodes from a conversation \
            between historical figures. Derive the overarching theme.
            
            Respond ONLY with JSON:
            {"label":"kebab-case-theme-name",\
            "salience":0.7,\
            "tags":["tag1","tag2"],\
            "drives":{"CURIOSITY":0.3,"AFFILIATION":0.2}}
            
            label: short kebab-case theme identifier
            salience: how central this theme is [0,1]
            tags: 2-3 thematic tags
            drives: which motivational axes this theme relates to and how strongly [-1,+1]. \
            Valid axes: CURIOSITY, COMPETENCE, AFFILIATION, AUTONOMY. Only include relevant axes.""";

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
        var episode = extractEpisode(latest, now);
        fragments.add(episode);

        var episodeCount = fragments.stream()
                .filter(f -> f instanceof IndividualEpisode).count();
        if (episodeCount >= 2 && episodeCount % 2 == 0) {
            var theme = deriveTheme(fragments, now);
            if (theme != null) {
                fragments.removeIf(f -> f instanceof DerivedTheme);
                fragments.add(theme);
            }
        }

        var state = new NarrativeState(agentId, tenantId,
                NarrativeScope.INDIVIDUAL, fragments, now,
                recentTurns.size());
        narrativeStore.store(state);
    }

    private IndividualEpisode extractEpisode(List<ConversationRunner.Turn> turns,
                                              Instant now) {
        if (agentProvider == null) {
            return heuristicEpisode(turns, now);
        }
        try {
            var exchange = turns.stream()
                    .map(t -> t.speakerName() + ": " + truncate(t.dialogue(), 300))
                    .collect(Collectors.joining("\n\n"));
            var config = AgentSessionConfig.of(EPISODE_PROMPT, exchange);
            var json = invokeAndExtractJson(config);
            if (json == null) return heuristicEpisode(turns, now);

            var description = extractJsonString(json, "description");
            var valence = extractJsonDouble(json, "valence", 0.5);
            var tags = extractJsonStringArray(json, "tags");

            if (description.isBlank()) return heuristicEpisode(turns, now);

            return new IndividualEpisode(
                    UUID.randomUUID().toString(), now, now,
                    tags.isEmpty() ? List.of("exchange") : tags,
                    description, Math.clamp(valence, -1.0, 1.0), List.of());
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING,
                    "LLM episode extraction failed, using heuristic", e);
            return heuristicEpisode(turns, now);
        }
    }

    private @Nullable DerivedTheme deriveTheme(List<NarrativeFragment> fragments,
                                                Instant now) {
        if (agentProvider == null) return heuristicTheme(fragments, now);
        try {
            var episodeSummary = fragments.stream()
                    .filter(f -> f instanceof IndividualEpisode)
                    .map(f -> "- " + ((IndividualEpisode) f).description())
                    .collect(Collectors.joining("\n"));
            var config = AgentSessionConfig.of(THEME_PROMPT,
                    "Episodes so far:\n" + episodeSummary);
            var json = invokeAndExtractJson(config);
            if (json == null) return heuristicTheme(fragments, now);

            var label = extractJsonString(json, "label");
            var salience = extractJsonDouble(json, "salience", 0.7);
            var tags = extractJsonStringArray(json, "tags");
            var drives = extractDriveWeights(json);

            if (label.isBlank()) return heuristicTheme(fragments, now);

            return new DerivedTheme(
                    UUID.randomUUID().toString(), now, now,
                    tags.isEmpty() ? List.of("dialogue") : tags,
                    label, Math.clamp(salience, 0.0, 1.0), drives,
                    fragments.stream()
                            .filter(f -> f instanceof IndividualEpisode)
                            .map(NarrativeFragment::id).toList());
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING,
                    "LLM theme derivation failed, using heuristic", e);
            return heuristicTheme(fragments, now);
        }
    }

    private static IndividualEpisode heuristicEpisode(
            List<ConversationRunner.Turn> turns, Instant now) {
        var description = turns.get(0).speakerName() + " and "
                + turns.get(1).speakerName() + " discussed: "
                + truncate(turns.get(0).dialogue(), 80) + "...";
        return new IndividualEpisode(UUID.randomUUID().toString(), now, now,
                List.of("exchange", "turn-" + turns.get(1).number()),
                description, 0.5, List.of());
    }

    private static DerivedTheme heuristicTheme(
            List<NarrativeFragment> fragments, Instant now) {
        return new DerivedTheme(UUID.randomUUID().toString(), now, now,
                List.of("dialogue", "connection"),
                "shared-intellectual-curiosity", 0.7,
                Map.of(DriveAxis.CURIOSITY, 0.3, DriveAxis.AFFILIATION, 0.2),
                fragments.stream()
                        .filter(f -> f instanceof IndividualEpisode)
                        .map(NarrativeFragment::id).toList());
    }

    private @Nullable String invokeAndExtractJson(AgentSessionConfig config) {
        var sb = new StringBuilder();
        agentProvider.invoke(config)
                .subscribe().asStream()
                .filter(e -> e instanceof AgentEvent.TextDelta)
                .map(e -> ((AgentEvent.TextDelta) e).text())
                .forEach(sb::append);
        var raw = sb.toString();
        var start = raw.indexOf('{');
        var end = raw.lastIndexOf('}');
        return (start >= 0 && end > start) ? raw.substring(start, end + 1) : null;
    }

    private static String extractJsonString(String json, String key) {
        var pattern = java.util.regex.Pattern.compile(
                "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        var matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static double extractJsonDouble(String json, String key,
                                             double fallback) {
        var pattern = java.util.regex.Pattern.compile(
                "\"" + key + "\"\\s*:\\s*(-?\\d+\\.?\\d*)");
        var matcher = pattern.matcher(json);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : fallback;
    }

    private static List<String> extractJsonStringArray(String json, String key) {
        var pattern = java.util.regex.Pattern.compile(
                "\"" + key + "\"\\s*:\\s*\\[([^\\]]*)]");
        var matcher = pattern.matcher(json);
        if (!matcher.find()) return List.of();
        var items = new ArrayList<String>();
        var itemPattern = java.util.regex.Pattern.compile("\"([^\"]+)\"");
        var itemMatcher = itemPattern.matcher(matcher.group(1));
        while (itemMatcher.find()) items.add(itemMatcher.group(1));
        return items;
    }

    private static Map<DriveAxis, Double> extractDriveWeights(String json) {
        var drivesPattern = java.util.regex.Pattern.compile(
                "\"drives\"\\s*:\\s*\\{([^}]*)}");
        var matcher = drivesPattern.matcher(json);
        if (!matcher.find()) return Map.of();
        var drivesJson = matcher.group(1);
        var weights = new java.util.EnumMap<DriveAxis, Double>(DriveAxis.class);
        for (var axis : DriveAxis.values()) {
            var axisPattern = java.util.regex.Pattern.compile(
                    "\"" + axis.name() + "\"\\s*:\\s*(-?\\d+\\.?\\d*)");
            var axisMatcher = axisPattern.matcher(drivesJson);
            if (axisMatcher.find()) {
                weights.put(axis, Math.clamp(
                        Double.parseDouble(axisMatcher.group(1)), -1.0, 1.0));
            }
        }
        return weights;
    }

    private static String truncate(String s, int maxLen) {
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
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
