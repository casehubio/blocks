package io.casehub.blocks.agentic.social.narrative;

import io.casehub.blocks.agentic.social.TokenJaccardDistance;
import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.memory.ReflectionEntry;
import io.casehub.blocks.memory.ReflectionQueryStore;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;

import java.io.StringReader;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@ApplicationScoped
public class NarrativeSynthesiser {

    private static final Logger LOG = Logger.getLogger(NarrativeSynthesiser.class.getName());

    static final String SYSTEM_PROMPT = """
            You are synthesising a first-person narrative identity from an agent's \
            reflections and experiences. Given the agent's existing narrative context \
            and new reflections, produce:

            1. New episodes — significant experiences from the reflections. Each \
            episode has a description, emotional valence [-1.0, 1.0], and \
            thematic tags.

            2. Updated themes — identity themes derived from ALL episodes (existing \
            and new). Each theme has a label, salience [0.0, 1.0], thematic tags, \
            and per-axis drive modulation weights.

            Drive axes: CURIOSITY, COMPETENCE, AFFILIATION, AUTONOMY.
            Axis weights range [-1.0, 1.0]: positive amplifies the drive, negative \
            dampens it.

            Respond with JSON only. No explanation outside the JSON.""";

    private final AgentProvider agentProvider;
    private final NarrativeStore narrativeStore;
    private final ReflectionQueryStore reflectionQueryStore;
    private final NarrativeConfig config;
    private final Clock clock;

    private final ConcurrentHashMap<String, ReentrantLock> synthesisLocks =
            new ConcurrentHashMap<>();

    @Inject
    public NarrativeSynthesiser(AgentProvider agentProvider,
                                 NarrativeStore narrativeStore,
                                 ReflectionQueryStore reflectionQueryStore,
                                 NarrativeConfig config) {
        this(agentProvider, narrativeStore, reflectionQueryStore,
                config, Clock.systemUTC());
    }

    NarrativeSynthesiser(AgentProvider agentProvider,
                          NarrativeStore narrativeStore,
                          ReflectionQueryStore reflectionQueryStore,
                          NarrativeConfig config,
                          Clock clock) {
        this.agentProvider = agentProvider;
        this.narrativeStore = narrativeStore;
        this.reflectionQueryStore = reflectionQueryStore;
        this.config = config;
        this.clock = clock;
    }

    public NarrativeSynthesisTick synthesiseIfNeeded(String agentId, String tenantId) {
        var key = agentId + ":" + tenantId;
        var lock = synthesisLocks.computeIfAbsent(key, k -> new ReentrantLock());
        lock.lock();
        try {
            return doSynthesise(agentId, tenantId);
        } finally {
            lock.unlock();
        }
    }

    private NarrativeSynthesisTick doSynthesise(String agentId, String tenantId) {
        var currentState = narrativeStore.load(agentId, tenantId);
        var since = currentState != null ? currentState.synthesisedAt() : Instant.EPOCH;
        var now = Instant.now(clock);

        int count = reflectionQueryStore.countSince(agentId, tenantId, since);
        var gate = config.synthesisGate();
        boolean quietPeriodTriggered = Duration.between(since, now)
                .compareTo(gate.quietPeriodBypass()) >= 0;

        if (count == 0) {
            return new NarrativeSynthesisTick.Skipped("no new reflections");
        }

        if (!quietPeriodTriggered && count < gate.minNewReflections()) {
            return new NarrativeSynthesisTick.Skipped("insufficient reflections: " + count);
        }

        var reflections = reflectionQueryStore.findSince(agentId, tenantId, since);
        boolean capped = reflections.size() > config.maxReflectionsPerSynthesis();
        if (capped) {
            reflections = reflections.subList(0, config.maxReflectionsPerSynthesis());
        }

        if (!quietPeriodTriggered) {
            var reflectionText = reflections.stream()
                    .map(ReflectionEntry::insight)
                    .collect(Collectors.joining("\n"));
            var currentNarrativeText = currentState != null
                    ? currentState.episodes().stream()
                    .map(IndividualEpisode::description)
                    .collect(Collectors.joining("\n"))
                    : "";
            double novelty = TokenJaccardDistance.distance(reflectionText, currentNarrativeText);
            if (novelty < gate.noveltyThreshold()) {
                return new NarrativeSynthesisTick.Skipped("low novelty");
            }
        }

        var userPrompt = assembleUserPrompt(currentState, reflections);

        String responseText;
        try {
            responseText = agentProvider.invoke(AgentSessionConfig.of(SYSTEM_PROMPT, userPrompt))
                    .filter(e -> e instanceof AgentEvent.TextDelta)
                    .map(e -> ((AgentEvent.TextDelta) e).text())
                    .collect().asList()
                    .await().indefinitely()
                    .stream().collect(Collectors.joining());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "LLM invocation failed for agent " + agentId, e);
            return new NarrativeSynthesisTick.Skipped("llm failure");
        }

        if (responseText == null || responseText.isBlank()) {
            return new NarrativeSynthesisTick.Skipped("empty response");
        }

        SynthesisResult result;
        try {
            result = parseResponse(responseText);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to parse synthesis response: " + e.getMessage());
            return new NarrativeSynthesisTick.Skipped("parse failure");
        }

        var newEpisodes = buildEpisodes(result.newEpisodes(), reflections, now);

        var allEpisodes = new ArrayList<IndividualEpisode>();
        if (currentState != null) {
            allEpisodes.addAll(currentState.episodes());
        }
        allEpisodes.addAll(newEpisodes);

        var groupEpisodes = currentState != null
                ? currentState.groupEpisodes()
                : List.<GroupEpisode>of();

        var allFragmentsForTagMatching = new ArrayList<NarrativeFragment>();
        allFragmentsForTagMatching.addAll(allEpisodes);
        allFragmentsForTagMatching.addAll(groupEpisodes);

        var themes = buildThemes(result.themes(), allFragmentsForTagMatching, now);

        if (newEpisodes.isEmpty() && themes.isEmpty()) {
            return new NarrativeSynthesisTick.Skipped("empty synthesis result");
        }

        if (themes.isEmpty() && !allEpisodes.isEmpty()) {
            LOG.warning("LLM produced no themes despite existing episodes — treating as failure");
            return new NarrativeSynthesisTick.Skipped("no themes produced");
        }

        pruneEpisodes(allEpisodes);
        pruneThemes(themes);

        var allFragments = new ArrayList<NarrativeFragment>();
        allFragments.addAll(allEpisodes);
        allFragments.addAll(groupEpisodes);
        allFragments.addAll(themes);

        var synthesisedAt = capped
                ? reflections.getLast().generatedAt()
                : now;

        var state = new NarrativeState(agentId, tenantId,
                NarrativeScope.INDIVIDUAL, allFragments, synthesisedAt,
                reflections.size());
        narrativeStore.store(state);

        return new NarrativeSynthesisTick.Synthesised(state, reflections.size());
    }

    String assembleUserPrompt(NarrativeState currentState,
                               List<ReflectionEntry> reflections) {
        var sb = new StringBuilder();

        sb.append("## Existing Episodes\n");
        if (currentState != null && !currentState.episodes().isEmpty()) {
            int i = 1;
            for (var ep : currentState.episodes()) {
                sb.append(i++).append(". ").append(ep.description())
                        .append(" (valence: ")
                        .append(String.format("%.1f", ep.emotionalValence()))
                        .append(")\n");
            }
        } else {
            sb.append("None.\n");
        }

        sb.append("\n## Existing Theme Labels\n");
        if (currentState != null && !currentState.themes().isEmpty()) {
            for (var theme : currentState.themes()) {
                sb.append("- ").append(theme.label()).append("\n");
            }
        } else {
            sb.append("None.\n");
        }

        sb.append("\n## New Reflections\n");
        for (int i = 0; i < reflections.size(); i++) {
            sb.append(i).append(". ").append(reflections.get(i).insight()).append("\n");
        }

        sb.append("""

                ## Response Format
                {
                  "newEpisodes": [
                    {
                      "description": "...",
                      "emotionalValence": 0.0,
                      "thematicTags": ["..."],
                      "fromReflections": [0, 2]
                    }
                  ],
                  "themes": [
                    {
                      "label": "...",
                      "salience": 0.0,
                      "thematicTags": ["..."],
                      "axisWeights": {
                        "CURIOSITY": 0.0,
                        "COMPETENCE": 0.0,
                        "AFFILIATION": 0.0,
                        "AUTONOMY": 0.0
                      }
                    }
                  ]
                }
                """);

        return sb.toString();
    }

    record SynthesisResult(List<EpisodeSpec> newEpisodes, List<ThemeSpec> themes) {}
    record EpisodeSpec(String description, double emotionalValence,
                       List<String> thematicTags, List<Integer> fromReflections) {}
    record ThemeSpec(String label, double salience,
                     List<String> thematicTags, Map<String, Double> axisWeights) {}

    SynthesisResult parseResponse(String responseText) {
        var cleaned = responseText.strip();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```(?:json)?\\s*", "")
                    .replaceAll("\\s*```$", "");
        }

        JsonObject json;
        try (var reader = Json.createReader(new StringReader(cleaned))) {
            json = reader.readObject();
        }

        var episodes = new ArrayList<EpisodeSpec>();
        if (json.containsKey("newEpisodes")) {
            var arr = json.getJsonArray("newEpisodes");
            for (int i = 0; i < arr.size(); i++) {
                try {
                    var obj = arr.getJsonObject(i);
                    var desc = obj.getString("description");
                    var valence = obj.getJsonNumber("emotionalValence").doubleValue();
                    var tags = new ArrayList<String>();
                    if (obj.containsKey("thematicTags")) {
                        var tagsArr = obj.getJsonArray("thematicTags");
                        for (int j = 0; j < tagsArr.size(); j++) {
                            tags.add(tagsArr.getString(j));
                        }
                    }
                    var refs = new ArrayList<Integer>();
                    if (obj.containsKey("fromReflections")) {
                        var refsArr = obj.getJsonArray("fromReflections");
                        for (int j = 0; j < refsArr.size(); j++) {
                            refs.add(refsArr.getInt(j));
                        }
                    }
                    episodes.add(new EpisodeSpec(desc, valence, tags, refs));
                } catch (Exception e) {
                    LOG.warning("Skipping invalid episode at index " + i
                            + ": " + e.getMessage());
                }
            }
        }

        var themes = new ArrayList<ThemeSpec>();
        if (json.containsKey("themes")) {
            var arr = json.getJsonArray("themes");
            for (int i = 0; i < arr.size(); i++) {
                try {
                    var obj = arr.getJsonObject(i);
                    var label = obj.getString("label");
                    var salience = obj.getJsonNumber("salience").doubleValue();
                    var tags = new ArrayList<String>();
                    if (obj.containsKey("thematicTags")) {
                        var tagsArr = obj.getJsonArray("thematicTags");
                        for (int j = 0; j < tagsArr.size(); j++) {
                            tags.add(tagsArr.getString(j));
                        }
                    }
                    var weights = new HashMap<String, Double>();
                    if (obj.containsKey("axisWeights")) {
                        var weightsObj = obj.getJsonObject("axisWeights");
                        for (var key : weightsObj.keySet()) {
                            weights.put(key, weightsObj.getJsonNumber(key).doubleValue());
                        }
                    }
                    themes.add(new ThemeSpec(label, salience, tags, weights));
                } catch (Exception e) {
                    LOG.warning("Skipping invalid theme at index " + i
                            + ": " + e.getMessage());
                }
            }
        }

        return new SynthesisResult(episodes, themes);
    }

    private List<IndividualEpisode> buildEpisodes(List<EpisodeSpec> specs,
                                                   List<ReflectionEntry> reflections,
                                                   Instant now) {
        var episodes = new ArrayList<IndividualEpisode>();
        for (var spec : specs) {
            try {
                var valence = Math.clamp(spec.emotionalValence(), -1.0, 1.0);
                var sourceIds = new ArrayList<String>();
                for (var idx : spec.fromReflections()) {
                    if (idx >= 0 && idx < reflections.size()) {
                        sourceIds.addAll(reflections.get(idx).sourceCaseIds());
                    }
                }
                episodes.add(new IndividualEpisode(
                        UUID.randomUUID().toString(), now, null,
                        spec.thematicTags(), spec.description(),
                        valence, sourceIds));
            } catch (Exception e) {
                LOG.warning("Skipping invalid episode: " + e.getMessage());
            }
        }
        return episodes;
    }

    private List<DerivedTheme> buildThemes(List<ThemeSpec> specs,
                                            List<? extends NarrativeFragment> allFragments,
                                            Instant now) {
        var themes = new ArrayList<DerivedTheme>();
        for (var spec : specs) {
            try {
                var salience = Math.clamp(spec.salience(), 0.0, 1.0);
                var axisWeights = new EnumMap<DriveAxis, Double>(DriveAxis.class);
                for (var entry : spec.axisWeights().entrySet()) {
                    try {
                        var axis = DriveAxis.valueOf(entry.getKey());
                        axisWeights.put(axis, Math.clamp(entry.getValue(), -1.0, 1.0));
                    } catch (IllegalArgumentException ignored) {
                        LOG.warning("Unknown drive axis: " + entry.getKey());
                    }
                }
                var themeTags = spec.thematicTags().stream()
                        .map(String::toLowerCase)
                        .collect(Collectors.toSet());
                var supportingIds = new ArrayList<String>();
                if (!themeTags.isEmpty()) {
                    for (var fragment : allFragments) {
                        if (!fragment.thematicTags().isEmpty()) {
                            for (var tag : fragment.thematicTags()) {
                                if (themeTags.contains(tag.toLowerCase())) {
                                    supportingIds.add(fragment.id());
                                    break;
                                }
                            }
                        }
                    }
                }
                themes.add(new DerivedTheme(
                        UUID.randomUUID().toString(), now, null,
                        spec.thematicTags(), spec.label(),
                        salience, axisWeights, supportingIds));
            } catch (Exception e) {
                LOG.warning("Skipping invalid theme: " + e.getMessage());
            }
        }
        return themes;
    }

    private void pruneEpisodes(List<IndividualEpisode> episodes) {
        if (episodes.size() > config.maxEpisodes()) {
            episodes.sort(Comparator.comparing(IndividualEpisode::from));
            while (episodes.size() > config.maxEpisodes()) {
                episodes.removeFirst();
            }
        }
    }

    private void pruneThemes(List<DerivedTheme> themes) {
        themes.removeIf(t -> t.salience() < config.themeSalienceFloor());
        if (themes.size() > config.maxThemes()) {
            themes.sort(Comparator.comparingDouble(DerivedTheme::salience));
            while (themes.size() > config.maxThemes()) {
                themes.removeFirst();
            }
        }
    }
}
