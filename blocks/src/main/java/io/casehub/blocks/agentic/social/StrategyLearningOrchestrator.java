package io.casehub.blocks.agentic.social;

import io.casehub.blocks.summarisation.ContentSummariser;
import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.FeatureField;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.FeatureVectorCbrCase;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import io.casehub.neocortex.memory.cbr.TrendAnalyzer;
import io.casehub.neocortex.memory.cbr.TrendProfile;
import io.casehub.neocortex.memory.cbr.TrendSpec;
import io.casehub.neocortex.memory.cbr.TrendType;
import io.casehub.neocortex.memory.reflection.ReflectionOrchestrator;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.api.path.Path;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class StrategyLearningOrchestrator {

    private static final Logger LOG = Logger.getLogger(StrategyLearningOrchestrator.class.getName());

    private static final String SYSTEM_PROMPT = """
            You are a metacognitive strategy advisor for an AI agent. Analyze the agent's \
            interaction history and recommend strategy adjustments.
            
            Respond with JSON only:
            {"guidelines":["guideline1","guideline2"],\
            "dimensionDeltas":{"verbosity":-0.1,"formality":0.05}}
            
            Guidelines: ranked list, most impactful first. Include per-subject insights \
            where patterns differ significantly from global.
            Deltas: in [-0.2, +0.2]. Only include dimensions that should change.""";

    static final List<String> DEFAULT_DIMENSIONS = List.of(
            "verbosity", "formality", "initiative", "directness", "questionRate");
    static final double       TREND_IMPROVING_THRESHOLD = 0.6;
    static final double       TREND_DECLINING_THRESHOLD = 0.4;


    private final StrategyStore strategyStore;
    private final CbrCaseMemoryStore cbrStore;
    private final ReflectionOrchestrator reflectionOrchestrator;
    private final AgentProvider agentProvider;
    private final @Nullable ContentSummariser<EngagementSignal> summariser;
    private final StrategyLearningConfig config;
    private final Clock clock;

    private final ConcurrentHashMap<String, AgentLearningState> states = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> tickLocks = new ConcurrentHashMap<>();

    @Inject
    public StrategyLearningOrchestrator(StrategyStore strategyStore,
                                         CbrCaseMemoryStore cbrStore,
                                         ReflectionOrchestrator reflectionOrchestrator,
                                         AgentProvider agentProvider,
                                         StrategyLearningConfig config) {
        this(strategyStore, cbrStore, reflectionOrchestrator, agentProvider,
                null, config, Clock.systemUTC());
    }

    StrategyLearningOrchestrator(StrategyStore strategyStore,
                                  CbrCaseMemoryStore cbrStore,
                                  ReflectionOrchestrator reflectionOrchestrator,
                                  AgentProvider agentProvider,
                                  @Nullable ContentSummariser<EngagementSignal> summariser,
                                  StrategyLearningConfig config,
                                  Clock clock) {
        this.strategyStore = strategyStore;
        this.cbrStore = cbrStore;
        this.reflectionOrchestrator = reflectionOrchestrator;
        this.agentProvider = agentProvider;
        this.summariser = summariser;
        this.config = config;
        this.clock = clock;
    }

    public void record(EngagementSignal signal, String agentId,
                        String subjectId, String tenantId) {
        var state = states.computeIfAbsent(stateKey(agentId, tenantId),
                k -> new AgentLearningState(agentId, tenantId, config.maxBufferSize()));
        state.lastActivityTimestamp = clock.instant();

        if (signal instanceof EngagementSignal.TurnOutcome turn) {
            state.pendingTurns.addLast(new TurnEntry(turn, subjectId));
            if (state.pendingTurns.size() > config.maxBufferSize()) {
                state.pendingTurns.removeFirst();
            }
        } else if (signal instanceof EngagementSignal.ConversationOutcome conv) {
            state.pendingConversations.addLast(new ConversationEntry(conv, subjectId));
        }
    }

    public StrategyLearningTick tick(String agentId, String tenantId) {
        var state = states.get(stateKey(agentId, tenantId));
        if (state == null || (state.pendingTurns.isEmpty() && state.pendingConversations.isEmpty())) {
            return new StrategyLearningTick.NoChange("no pending signals");
        }

        var lock = tickLocks.computeIfAbsent(stateKey(agentId, tenantId),
                k -> new ReentrantLock());
        lock.lock();
        try {
            return doTick(state);
        } finally {
            lock.unlock();
        }
    }

    public StrategyReflection reflect(String agentId, String tenantId) {
        var lock = tickLocks.computeIfAbsent(stateKey(agentId, tenantId),
                k -> new ReentrantLock());
        lock.lock();
        try {
            return doReflect(agentId, tenantId);
        } finally {
            lock.unlock();
        }
    }

    public Optional<StrategyProfile> currentStrategy(String agentId, String tenantId) {
        var state = states.get(stateKey(agentId, tenantId));
        if (state != null && state.currentProfile != null) {
            return Optional.of(state.currentProfile);
        }
        return strategyStore.lookup(agentId, tenantId);
    }

    public java.util.Optional<EngagementTrend>

    engagementTrend(String agentId, String tenantId) {
        var key   = stateKey(agentId, tenantId);
        var state = states.get(key);
        if (state == null || state.currentProfile == null) {return java.util.Optional.empty();}

        var profile = state.currentProfile;
        var trends = new java.util.HashMap<String, EngagementTrend.TrendDirection>();
        for (var entry : profile.dimensions().entrySet()) {
            double val = entry.getValue();
            trends.put(entry.getKey(), val > TREND_IMPROVING_THRESHOLD ? EngagementTrend.TrendDirection.IMPROVING
                                                 : val < TREND_DECLINING_THRESHOLD ? EngagementTrend.TrendDirection.DECLINING
                                                             : EngagementTrend.TrendDirection.STABLE);
        }
        double responseRate = state.totalSignals > 0
                              ? (double) state.totalResponded / state.totalSignals : 0.0;
        return java.util.Optional.of(new EngagementTrend(trends, responseRate, state.totalSignals));
    }


    private StrategyLearningTick doTick(AgentLearningState state) {
        var drainedTurns = new ArrayList<TurnEntry>();
        while (!state.pendingTurns.isEmpty()) {
            drainedTurns.add(state.pendingTurns.removeFirst());
        }

        for (var entry : drainedTurns) {
            state.totalSignals++;
            var event = entry.signal.event();
            if (event.responded() != null && event.responded()) {
                state.totalResponded++;
            }
            if (event.affectShift() != null) {
                state.affectSum += event.affectShift();
            }
        }

        double engagementRate = state.totalSignals > 0
                ? (double) state.totalResponded / state.totalSignals : 0.0;
        double meanSentiment = state.totalSignals > 0
                ? state.affectSum / state.totalSignals : 0.0;
        state.lastTickTimestamp = clock.instant();

        if (state.pendingConversations.isEmpty()
                && drainedTurns.size() < config.minSignalsForConversationCase()) {
            return new StrategyLearningTick.Observed(
                    drainedTurns.size(), engagementRate, meanSentiment);
        }

        var storedConversations = new ArrayList<String>();
        int casesStored = 0;

        var drainedConversations = new ArrayList<ConversationEntry>();
        while (!state.pendingConversations.isEmpty()) {
            drainedConversations.add(state.pendingConversations.removeFirst());
        }

        for (var convEntry : drainedConversations) {
            var conv = convEntry.signal;
            var matchedTurns = drainedTurns.stream()
                    .filter(t -> convEntry.subjectId.equals(t.subjectId)
                            && conv.conversationId().equals(t.signal.event().caseId()))
                    .toList();

            if (matchedTurns.isEmpty()) continue;

            var features = extractFeatures(matchedTurns, convEntry.subjectId, state.agentId);
            var summary = conv.conversationSummary() != null
                    ? conv.conversationSummary()
                    : "Conversation with " + convEntry.subjectId + " (" + conv.turnCount() + " turns)";
            var cbrCase = new FeatureVectorCbrCase(
                    summary, "-", null, null, features, null, state.agentId);
            cbrStore.store(cbrCase, config.engagementCaseType(), state.agentId,
                    config.memoryDomain(), state.tenantId, null, Path.root());
            storedConversations.add(conv.conversationId());
            casesStored++;
        }

        if (drainedConversations.isEmpty() && drainedTurns.size() >= config.minSignalsForConversationCase()) {
            var grouped = new LinkedHashMap<String, List<TurnEntry>>();
            for (var entry : drainedTurns) {
                grouped.computeIfAbsent(entry.subjectId, k -> new ArrayList<>()).add(entry);
            }
            for (var group : grouped.entrySet()) {
                if (group.getValue().size() >= config.minSignalsForConversationCase()) {
                    var features = extractFeatures(group.getValue(), group.getKey(), state.agentId);
                    var summary = "Interaction with " + group.getKey()
                            + " (" + group.getValue().size() + " turns)";
                    var cbrCase = new FeatureVectorCbrCase(
                            summary, "-", null, null, features, null, state.agentId);
                    cbrStore.store(cbrCase, config.engagementCaseType(), state.agentId,
                            config.memoryDomain(), state.tenantId, null, Path.root());
                    storedConversations.add(group.getKey());
                    casesStored++;
                }
            }
        }

        if (casesStored == 0) {
            return new StrategyLearningTick.Observed(
                    drainedTurns.size(), engagementRate, meanSentiment);
        }
        return new StrategyLearningTick.Learned(
                drainedTurns.size(), engagementRate, meanSentiment,
                List.copyOf(storedConversations), casesStored);
    }

    private Map<String, FeatureValue> extractFeatures(List<TurnEntry> turns,
                                                       String subjectId, String agentId) {
        var features = new LinkedHashMap<String, FeatureValue>();
        features.put("subjectId", FeatureValue.string(subjectId));
        features.put("agentId", FeatureValue.string(agentId));
        features.put("conversationTimestamp",
                FeatureValue.number((double) clock.instant().toEpochMilli()));
        features.put("turnCount", FeatureValue.number(turns.size()));

        double lenSum = 0; int lenCount = 0;
        double sentSum = 0; int sentCount = 0;
        int continued = 0; int contTotal = 0;

        for (var entry : turns) {
            var event = entry.signal.event();
            if (event.responseLength() != null) {
                lenSum += event.responseLength();
                lenCount++;
            }
            if (event.affectShift() != null) {
                sentSum += event.affectShift();
                sentCount++;
            }
            if (event.continued() != null) {
                contTotal++;
                if (event.continued()) continued++;
            }
        }

        features.put("avgResponseLength",
                FeatureValue.number(lenCount > 0 ? lenSum / lenCount : 0));
        features.put("continuationRate",
                FeatureValue.number(contTotal > 0 ? (double) continued / contTotal : 0));
        features.put("meanAffectShift",
                FeatureValue.number(sentCount > 0 ? sentSum / sentCount : 0));

        for (String dim : DEFAULT_DIMENSIONS) {
            double sum = 0; int count = 0;
            for (var entry : turns) {
                Double val = entry.signal.dimensionalSnapshot().get(dim);
                if (val != null) { sum += val; count++; }
            }
            features.put("avgSnapshot_" + dim,
                    FeatureValue.number(count > 0 ? sum / count : config.defaultDimensionValue()));
        }

        return Map.copyOf(features);
    }

    private StrategyReflection doReflect(String agentId, String tenantId) {
        var profile = currentStrategy(agentId, tenantId).orElseGet(
                () -> defaultProfile(agentId, tenantId));

        var query = CbrQuery.of(tenantId, config.memoryDomain(), Path.root(),
                        config.engagementCaseType(), Map.of(), config.maxReflectionSources())
                .withMinSimilarity(0.0);
        var allCases = cbrStore.retrieveSimilar(query, CbrCase.class);
        var cases = allCases.stream()
                .filter(s -> agentId.equals(s.cbrCase().producerAgentId()))
                .toList();

        if (cases.size() < config.minCasesForReflection()) {
            return new StrategyReflection.NoChange("insufficient evidence ("
                    + cases.size() + "/" + config.minCasesForReflection() + ")");
        }

        TrendProfile trends = analyzeTrends(cases);

        var state = states.get(stateKey(agentId, tenantId));
        Instant since = state != null && state.lastReflectTimestamp != null
                ? state.lastReflectTimestamp : Instant.EPOCH;
        List<String> reflections;
        try {
            reflections = reflectionOrchestrator.reflect(
                    agentId, tenantId, since, config.maxReflectionSources());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "ReflectionOrchestrator failed for " + agentId, e);
            reflections = List.of();
        }

        var perSubjectSummary = summarizePerSubject(cases);
        var userPrompt = buildReflectionPrompt(profile, trends, reflections, perSubjectSummary);

        String llmResponse;
        try {
            var sessionConfig = AgentSessionConfig.of(SYSTEM_PROMPT, userPrompt);
            var responseText = new StringBuilder();
            agentProvider.invoke(sessionConfig)
                    .subscribe().asStream()
                    .filter(e -> e instanceof AgentEvent.TextDelta)
                    .map(e -> ((AgentEvent.TextDelta) e).text())
                    .forEach(responseText::append);
            llmResponse = responseText.toString();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "LLM synthesis failed for " + agentId, e);
            return new StrategyReflection.NoChange("LLM synthesis failed");
        }

        return applyReflectionResult(profile, llmResponse, trends, cases.size(),
                agentId, tenantId, state);
    }

    private TrendProfile analyzeTrends(List<? extends ScoredCbrCase<CbrCase>> cases) {
        var sorted = cases.stream()
                          .sorted((a, b) -> {
                              double tA = numericFeature(a.cbrCase().features(), "conversationTimestamp", 0);
                              double tB = numericFeature(b.cbrCase().features(), "conversationTimestamp", 0);
                              return Double.compare(tA, tB);
                          })
                          .toList();

        var observations = new ArrayList<Map<String, FeatureValue>>();
        for (var scored : sorted) {
            observations.add(scored.cbrCase().features());
        }

        if (observations.size() < 2) {
            return new TrendProfile(Map.of());
        }

        try {
            var trendSpec = new TrendSpec(
                    Set.of(TrendType.SLOPE, TrendType.DELTA, TrendType.VOLATILITY),
                    java.time.temporal.ChronoUnit.MILLIS);
            var tsSchema = new FeatureField.TimeSeries(
                    "engagement",
                    List.of(
                            FeatureField.numeric("conversationTimestamp", 0, Double.MAX_VALUE),
                            FeatureField.numeric("continuationRate", 0, 1),
                            FeatureField.numeric("meanAffectShift", -1, 1),
                            FeatureField.numeric("avgResponseLength", 0, 10000)),
                    "conversationTimestamp",
                    null,
                    trendSpec);
            return TrendAnalyzer.analyze(observations, tsSchema);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "TrendAnalyzer failed", e);
            return new TrendProfile(Map.of());
        }}

    private String summarizePerSubject(List<? extends ScoredCbrCase<CbrCase>> cases) {
        var bySubject = new LinkedHashMap<String, List<Map<String, FeatureValue>>>();
        for (var scored : cases) {
            var features = scored.cbrCase().features();
            var sv = features.get("subjectId");
            if (sv instanceof FeatureValue.StringVal s) {
                bySubject.computeIfAbsent(s.value(), k -> new ArrayList<>()).add(features);
            }
        }

        var sb = new StringBuilder();
        for (var entry : bySubject.entrySet()) {
            double avgCont = entry.getValue().stream()
                    .mapToDouble(f -> numericFeature(f, "continuationRate", 0))
                    .average().orElse(0);
            double avgSent = entry.getValue().stream()
                    .mapToDouble(f -> numericFeature(f, "meanAffectShift", 0))
                    .average().orElse(0);
            sb.append(String.format("  %s: engagement %.0f%%, sentiment %+.2f (%d conversations)\n",
                    entry.getKey(), avgCont * 100, avgSent, entry.getValue().size()));
        }
        return sb.toString();
    }

    private String buildReflectionPrompt(StrategyProfile profile, TrendProfile trends,
                                          List<String> reflections, String perSubjectSummary) {
        var sb = new StringBuilder();
        sb.append("Current strategy dimensions:\n");
        for (var dim : profile.dimensions().entrySet()) {
            sb.append(String.format("  %s = %.2f\n", dim.getKey(), dim.getValue()));
        }

        sb.append("\nCurrent guidelines:\n");
        if (profile.guidelines().isEmpty()) {
            sb.append("  None yet\n");
        } else {
            for (int i = 0; i < profile.guidelines().size(); i++) {
                sb.append(String.format("  %d. %s\n", i + 1, profile.guidelines().get(i)));
            }
        }

        sb.append("\nEngagement trend analysis:\n");
        if (trends.metrics().isEmpty()) {
            sb.append("  Insufficient data for trends\n");
        } else {
            for (var m : trends.metrics().entrySet()) {
                sb.append(String.format("  %s = %.4f\n", m.getKey(), m.getValue()));
            }
        }

        sb.append("\nReflective insights:\n");
        if (reflections.isEmpty()) {
            sb.append("  None\n");
        } else {
            for (var r : reflections) {
                sb.append("  - ").append(r).append("\n");
            }
        }

        sb.append("\nPer-subject engagement patterns:\n");
        sb.append(perSubjectSummary.isEmpty() ? "  None\n" : perSubjectSummary);

        sb.append(String.format("\nProvide up to %d ranked guidelines and dimensional deltas.",
                config.maxGuidelines()));
        return sb.toString();
    }

    private StrategyReflection applyReflectionResult(StrategyProfile profile, String json,
                                                      TrendProfile trends, int evidenceCases,
                                                      String agentId, String tenantId,
                                                      @Nullable AgentLearningState state) {
        try {
            json = json.strip();
            if (json.startsWith("```")) {
                json = json.replaceFirst("```[a-z]*\\n?", "").replaceFirst("\\n?```$", "").strip();
            }

            if (!json.contains("\"guidelines\"") && !json.contains("\"dimensionDeltas\"")) {
                LOG.warning("LLM output missing expected JSON keys: " + json);
                return new StrategyReflection.NoChange("parse failure");
            }

            var guidelines = extractStringArray(json, "guidelines");
            if (guidelines.isEmpty()) {
                guidelines = profile.guidelines();
            }
            if (guidelines.size() > config.maxGuidelines()) {
                guidelines = guidelines.subList(0, config.maxGuidelines());
            }

            var deltas        = extractDeltas(json);
            var newDimensions = new LinkedHashMap<>(profile.dimensions());
            for (var delta : deltas.entrySet()) {
                if (!DEFAULT_DIMENSIONS.contains(delta.getKey())) {
                    LOG.fine("Ignoring unknown dimension: " + delta.getKey());
                    continue;
                }
                double clamped = Math.max(-0.2, Math.min(0.2, delta.getValue()));
                double current = newDimensions.getOrDefault(delta.getKey(),
                                                            config.defaultDimensionValue());
                newDimensions.put(delta.getKey(), Math.max(0.0, Math.min(1.0, current + clamped)));
            }

            var now = clock.instant();
            var updated = new StrategyProfile(agentId, tenantId,
                                              Map.copyOf(newDimensions), List.copyOf(guidelines), now, evidenceCases);
            strategyStore.store(updated);

            if (state != null) {
                state.currentProfile       = updated;
                state.lastReflectTimestamp = now;
            }

            return new StrategyReflection.Reflected(
                    updated, List.copyOf(guidelines), trends, evidenceCases);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to parse reflection result: " + json, e);
            return new StrategyReflection.NoChange("parse failure");
        }}

    private List<String> extractStringArray(String json, String field) {
        var result = new ArrayList<String>();
        String key = "\"" + field + "\"";
        int keyIdx = json.indexOf(key);
        if (keyIdx < 0) return result;
        int arrStart = json.indexOf('[', keyIdx);
        if (arrStart < 0) return result;
        int arrEnd = json.indexOf(']', arrStart);
        if (arrEnd < 0) return result;
        String inner = json.substring(arrStart + 1, arrEnd);
        for (String element : inner.split(",")) {
            element = element.strip();
            if (element.startsWith("\"") && element.endsWith("\"") && element.length() > 1) {
                result.add(element.substring(1, element.length() - 1));
            }
        }
        return result;
    }

    private Map<String, Double> extractDeltas(String json) {
        var result = new LinkedHashMap<String, Double>();
        String key = "\"dimensionDeltas\"";
        int keyIdx = json.indexOf(key);
        if (keyIdx < 0) return result;
        int objStart = json.indexOf('{', keyIdx);
        if (objStart < 0) return result;
        int objEnd = json.indexOf('}', objStart);
        if (objEnd < 0) return result;
        String inner = json.substring(objStart + 1, objEnd);
        for (String pair : inner.split(",")) {
            pair = pair.strip();
            int colon = pair.indexOf(':');
            if (colon < 0) continue;
            String k = pair.substring(0, colon).strip();
            String v = pair.substring(colon + 1).strip();
            if (k.startsWith("\"") && k.endsWith("\"") && k.length() > 1) {
                k = k.substring(1, k.length() - 1);
            }
            try {
                result.put(k, Double.parseDouble(v));
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    private StrategyProfile defaultProfile(String agentId, String tenantId) {
        var dims = new LinkedHashMap<String, Double>();
        for (String dim : DEFAULT_DIMENSIONS) {
            dims.put(dim, config.defaultDimensionValue());
        }
        return new StrategyProfile(agentId, tenantId, dims, List.of(), clock.instant(), 0);
    }

    private static double numericFeature(Map<String, FeatureValue> features,
                                          String key, double defaultVal) {
        var val = features.get(key);
        if (val instanceof FeatureValue.NumberVal nv) return nv.value();
        return defaultVal;
    }

    private static String stateKey(String agentId, String tenantId) {
        return agentId + ":" + tenantId;
    }

    record TurnEntry(EngagementSignal.TurnOutcome signal, String subjectId) {}
    record ConversationEntry(EngagementSignal.ConversationOutcome signal, String subjectId) {}

    static final class AgentLearningState {
        final String agentId;
        final String tenantId;
        final ArrayDeque<TurnEntry> pendingTurns;
        final ArrayDeque<ConversationEntry> pendingConversations;
        int totalSignals;
        int totalResponded;
        double affectSum;
        Instant lastSignalTimestamp;
        Instant lastTickTimestamp;
        Instant lastReflectTimestamp;
        Instant lastActivityTimestamp;
        @Nullable StrategyProfile currentProfile;

        AgentLearningState(String agentId, String tenantId, int maxBufferSize) {
            this.agentId = agentId;
            this.tenantId = tenantId;
            this.pendingTurns = new ArrayDeque<>(maxBufferSize);
            this.pendingConversations = new ArrayDeque<>();
        }
    }
}
