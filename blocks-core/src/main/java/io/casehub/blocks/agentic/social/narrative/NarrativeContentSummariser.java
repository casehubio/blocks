package io.casehub.blocks.agentic.social.narrative;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.memory.ReflectionEntry;
import io.casehub.blocks.summarisation.ContentSummariser;
import io.casehub.blocks.agent.StructuredAgentInvoker;
import io.casehub.blocks.agent.StructuredAgentInvoker.InvocationResult;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.jspecify.annotations.Nullable;

import java.io.StringReader;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class NarrativeContentSummariser
        implements ContentSummariser<ReflectionEntry, NarrativeState> {

    private static final Logger LOG = Logger.getLogger(NarrativeContentSummariser.class.getName());

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
    private final NarrativeConfig config;
    private final Clock clock;

    public NarrativeContentSummariser(AgentProvider agentProvider,
                                       NarrativeConfig config) {
        this(agentProvider, config, Clock.systemUTC());
    }

    NarrativeContentSummariser(AgentProvider agentProvider,
                                NarrativeConfig config,
                                Clock clock) {
        this.agentProvider = agentProvider;
        this.config = config;
        this.clock = clock;
    }

    @Override
    public CompletionStage<NarrativeState> summarise(
            List<ReflectionEntry> items,
            @Nullable NarrativeState previous) {

        if (items.isEmpty()) {
            return CompletableFuture.completedFuture(previous);
        }

        var reflections = items;
        boolean capped = reflections.size() > config.maxReflectionsPerSynthesis();
        if (capped) {
            reflections = reflections.subList(0, config.maxReflectionsPerSynthesis());
        }

        var userPrompt = assembleUserPrompt(previous, reflections);

        String responseText;
        try {
            var textResult = StructuredAgentInvoker.invokeText(agentProvider,
                    AgentSessionConfig.of(SYSTEM_PROMPT, userPrompt));
            if (textResult instanceof InvocationResult.AgentError<?>) {
                throw new RuntimeException("LLM invocation returned empty");
            }
            responseText = ((InvocationResult.Success<String>) textResult).value();
        } catch (Exception e) {
            LOG.warning("LLM invocation failed: " + e.getMessage());
            return CompletableFuture.completedFuture(previous);
        }

        if (responseText == null || responseText.isBlank()) {
            return CompletableFuture.completedFuture(previous);
        }

        SynthesisResult result;
        try {
            result = parseResponse(responseText);
        } catch (Exception e) {
            LOG.warning("Failed to parse synthesis response: " + e.getMessage());
            return CompletableFuture.completedFuture(previous);
        }

        var now = Instant.now(clock);
        var newEpisodes = buildEpisodes(result.newEpisodes(), reflections, now);

        var allEpisodes = new ArrayList<IndividualEpisode>();
        if (previous != null) {
            allEpisodes.addAll(previous.episodes());
        }
        allEpisodes.addAll(newEpisodes);

        var groupEpisodes = previous != null
                ? previous.groupEpisodes()
                : List.<GroupEpisode>of();

        var allFragmentsForTagMatching = new ArrayList<NarrativeFragment>();
        allFragmentsForTagMatching.addAll(allEpisodes);
        allFragmentsForTagMatching.addAll(groupEpisodes);

        var themes = buildThemes(result.themes(), allFragmentsForTagMatching, now);

        if (newEpisodes.isEmpty() && themes.isEmpty()) {
            return CompletableFuture.completedFuture(previous);
        }

        if (themes.isEmpty() && !allEpisodes.isEmpty()) {
            LOG.warning("LLM produced no themes despite existing episodes — treating as failure");
            return CompletableFuture.completedFuture(previous);
        }

        var allFragments = new ArrayList<NarrativeFragment>();
        allFragments.addAll(allEpisodes);
        allFragments.addAll(groupEpisodes);
        allFragments.addAll(themes);

        var scopeId = previous != null ? previous.scopeId() : reflections.get(0).agentId();
        var tenantId = previous != null ? previous.tenantId() : reflections.get(0).tenantId();

        var synthesisedAt = capped
                ? reflections.getLast().generatedAt()
                : now;

        var state = new NarrativeState(scopeId, tenantId,
                NarrativeScope.INDIVIDUAL, allFragments, synthesisedAt,
                reflections.size());

        return CompletableFuture.completedFuture(state);
    }

    String assembleUserPrompt(@Nullable NarrativeState currentState,
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
}
