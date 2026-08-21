package io.casehub.blocks.agentic.social;

import io.casehub.blocks.conversation.CommonGroundState;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class MentalModelOrchestrator {

    private static final Logger LOG = Logger.getLogger(MentalModelOrchestrator.class.getName());

    private static final String SYSTEM_PROMPT = """
            You are analysing conversation signals to infer another person's mental state. \
            Given the current attributed beliefs, desires, and intentions, plus recent signals, \
            produce an updated BDI assessment.
            
            Respond with JSON only:
            {"beliefs":[{"key":"...","text":"...","confidence":0.8}],\
            "desires":[{"key":"...","text":"...","confidence":0.7}],\
            "intentions":[{"key":"...","text":"...","confidence":0.5}]}
            
            Only include NEW or CHANGED entries. Omit unchanged entries — they are preserved. \
            Only include entries with confidence >= 0.3.""";

    private final MentalModelStore modelStore;
    private final AgentProvider agentProvider;
    private final MentalModelConfig config;
    private final Clock clock;

    private final ConcurrentHashMap<String, SubjectMentalState> states = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> tickLocks = new ConcurrentHashMap<>();

    @Inject
    public MentalModelOrchestrator(MentalModelStore modelStore,
                                   AgentProvider agentProvider,
                                   MentalModelConfig config) {
        this(modelStore, agentProvider, config, Clock.systemUTC());
    }

    MentalModelOrchestrator(MentalModelStore modelStore,
                            AgentProvider agentProvider,
                            MentalModelConfig config,
                            Clock clock) {
        this.modelStore = modelStore;
        this.agentProvider = agentProvider;
        this.config = config;
        this.clock = clock;
    }

    public void record(MentalStateSignal signal,
                       String agentId, String subjectId, String tenantId) {
        var key = stateKey(agentId, subjectId, tenantId);
        var state = states.computeIfAbsent(key,
                k -> new SubjectMentalState(agentId, subjectId, tenantId, config.maxBufferSize(), clock));
        synchronized (state) {
            if (signal instanceof MentalStateSignal.VerbalCue vc) {
                extractHeuristic(state, vc);
            }
            state.appendSignal(signal.content());
            state.pendingSignals++;
            state.lastSignalTimestamp = clock.instant();
            state.lastActivityTimestamp = clock.instant();
        }
    }

    public MentalModelTick tick(String agentId, String subjectId, String tenantId) {
        var key = stateKey(agentId, subjectId, tenantId);
        var state = states.get(key);

        if (state == null) {
            var existing = modelStore.lookup(agentId, subjectId, tenantId).orElse(null);
            if (existing != null) {
                state = states.computeIfAbsent(key,
                        k -> SubjectMentalState.fromSnapshot(existing, config.maxBufferSize(), clock));
            } else {
                return new MentalModelTick.Unchanged("no signals");
            }
        }

        var lock = tickLocks.computeIfAbsent(key, k -> new ReentrantLock());
        lock.lock();
        try {
            return doTick(state);
        } finally {
            lock.unlock();
            evictStale();
        }
    }

    public List<MentalProjection> project(String agentId, String subjectId, String tenantId) {
        var state = states.get(stateKey(agentId, subjectId, tenantId));
        if (state == null) return List.of();

        var projections = new ArrayList<MentalProjection>();
        synchronized (state) {
            projectDimension(state.beliefs, projections);
            projectDimension(state.desires, projections);
            projectDimension(state.intentions, projections);
        }
        return List.copyOf(projections);
    }

    public void observeConversation(CommonGroundState commonGround,
                                    String agentId, String subjectId, String tenantId) {
        var key = stateKey(agentId, subjectId, tenantId);
        var state = states.computeIfAbsent(key,
                k -> new SubjectMentalState(agentId, subjectId, tenantId, config.maxBufferSize(), clock));
        synchronized (state) {
            for (var fact : commonGround.establishedFacts().values()) {
                upsertBelief(state, fact.topic(), fact.content(), 0.9);
            }
            for (var fact : commonGround.pendingClaims().values()) {
                upsertBelief(state, fact.topic(), fact.content(), 0.5);
            }
            for (var fact : commonGround.disputedPoints().values()) {
                upsertBelief(state, fact.topic(), fact.content(), 0.3);
            }
            int totalFacts = commonGround.establishedFacts().size()
                    + commonGround.pendingClaims().size()
                    + commonGround.disputedPoints().size();
            state.pendingSignals += totalFacts;
            state.lastSignalTimestamp = clock.instant();
            state.lastActivityTimestamp = clock.instant();
        }
    }

    public java.util.List<MentalModelSnapshot>

    activeSnapshots(String agentId, String tenantId) {
        var prefix = agentId + ":";
        var suffix = ":" + tenantId;
        return states.entrySet().stream()
                     .filter(e -> e.getKey().startsWith(prefix) && e.getKey().endsWith(suffix))
                     .map(e -> e.getValue().currentSnapshot)
                     .filter(java.util.Objects::nonNull)
                     .toList();
    }


    // --- private ---

    private MentalModelTick doTick(SubjectMentalState state) {
        int snapPending;
        String snapBuffer;
        synchronized (state) {
            snapPending = state.pendingSignals;
            snapBuffer = state.drainBuffer();
            state.pendingSignals = 0;
        }

        var now = clock.instant();
        boolean anyDecayed = applyDecay(state, now);
        boolean anyEvicted = evictBelowFloor(state);

        if (snapPending == 0 && !anyDecayed && !anyEvicted && state.currentSnapshot == null) {
            return new MentalModelTick.Unchanged("no signals");
        }

        var previousSnapshot = state.currentSnapshot;
        boolean inferred = false;

        if (shouldInfer(state, snapBuffer)) {
            invokeLlmInference(state, snapBuffer);
            state.lastInferenceTimestamp = now;
            inferred = true;
        }

        var snapshot = buildSnapshot(state, now);
        state.currentSnapshot = snapshot;
        state.lastActivityTimestamp = now;

        try {
            modelStore.store(snapshot);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to persist mental model for " + state.subjectId, e);
        }

        if (inferred) {
            return new MentalModelTick.Inferred(snapshot, previousSnapshot);
        }
        return new MentalModelTick.Updated(snapshot);
    }

    private void extractHeuristic(SubjectMentalState state, MentalStateSignal.VerbalCue cue) {
        var now = clock.instant();
        var content = cue.content();
        var normalizedKey = normalizeKey(content);
        switch (cue.type()) {
            case BELIEF_STATEMENT -> upsertBelief(state, normalizedKey, content, 0.8);
            case DESIRE_EXPRESSION -> upsertState(state.desires, normalizedKey, content,
                    0.8, BdiDimension.DESIRE, now);
            case INTENTION_DECLARATION -> upsertState(state.intentions, normalizedKey, content,
                    0.8, BdiDimension.INTENTION, now);
        }
    }

    private void upsertBelief(SubjectMentalState state, String key, String description, double confidence) {
        var now = clock.instant();
        var existing = state.beliefs.get(key);
        int entrenchment = existing != null ? existing.entrenchment() + 1 : 1;
        state.beliefs.put(key, new AttributedState(key, description, confidence,
                entrenchment, now, BdiDimension.BELIEF));
    }

    private void upsertState(Map<String, AttributedState> map, String key,
                             String description, double confidence,
                             BdiDimension dimension, Instant now) {
        map.put(key, new AttributedState(key, description, confidence, 0, now, dimension));
    }

    static String normalizeKey(String content) {
        return content.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .strip()
                .replaceAll("\\s+", "_");
    }

    private boolean applyDecay(SubjectMentalState state, Instant now) {
        boolean changed = false;
        synchronized (state) {
            changed |= decayMap(state.beliefs, config.beliefHalfLife(), now);
            changed |= decayMap(state.desires, config.desireHalfLife(), now);
            changed |= decayMap(state.intentions, config.intentionHalfLife(), now);
        }
        return changed;
    }

    private boolean decayMap(Map<String, AttributedState> map, Duration halfLife, Instant now) {
        boolean changed = false;
        for (var entry : map.entrySet()) {
            var s = entry.getValue();
            long elapsedMs = Duration.between(s.lastReinforced(), now).toMillis();
            double halfLifeMs = halfLife.toMillis();
            if (halfLifeMs > 0 && elapsedMs > 0) {
                double decayed = s.confidence() * Math.pow(0.5, elapsedMs / halfLifeMs);
                if (Math.abs(decayed - s.confidence()) > 0.001) {
                    entry.setValue(new AttributedState(s.key(), s.description(), decayed,
                            s.entrenchment(), s.lastReinforced(), s.dimension()));
                    changed = true;
                }
            }
        }
        return changed;
    }

    private boolean evictBelowFloor(SubjectMentalState state) {
        boolean evicted = false;
        synchronized (state) {
            evicted |= state.beliefs.entrySet().removeIf(e -> e.getValue().confidence() < config.confidenceFloor());
            evicted |= state.desires.entrySet().removeIf(e -> e.getValue().confidence() < config.confidenceFloor());
            evicted |= state.intentions.entrySet().removeIf(e -> e.getValue().confidence() < config.confidenceFloor());
        }
        return evicted;
    }

    private boolean shouldInfer(SubjectMentalState state, String buffer) {
        if (buffer.isEmpty()) return false;
        long lines = buffer.chars().filter(c -> c == '\n').count();
        if (lines < config.minSignalsForInference()) return false;
        if (state.lastInferenceTimestamp != null) {
            var elapsed = Duration.between(state.lastInferenceTimestamp, clock.instant());
            if (elapsed.compareTo(config.inferenceCooldown()) < 0) return false;
        }
        return true;
    }

    private void invokeLlmInference(SubjectMentalState state, String buffer) {
        try {
            var currentBeliefs = formatBdi(state.beliefs.values().stream().toList());
            var currentDesires = formatBdi(state.desires.values().stream().toList());
            var currentIntentions = formatBdi(state.intentions.values().stream().toList());

            int maxObs = config.maxSignalsInPrompt();
            String[] lines = buffer.split("\n");
            var recentText = new StringBuilder();
            int start = Math.max(0, lines.length - maxObs);
            for (int i = start; i < lines.length; i++) {
                if (!lines[i].isBlank()) recentText.append(lines[i]).append("\n");
            }

            var userPrompt = String.format("""
                    Current mental model:
                      Beliefs: %s
                      Desires: %s
                      Intentions: %s
                    
                    Recent signals (%d):
                    %s""",
                    currentBeliefs, currentDesires, currentIntentions,
                    lines.length, recentText);

            var sessionConfig = AgentSessionConfig.of(SYSTEM_PROMPT, userPrompt);
            var responseText = new StringBuilder();
            agentProvider.invoke(sessionConfig)
                    .subscribe().asStream()
                    .filter(e -> e instanceof AgentEvent.TextDelta)
                    .map(e -> ((AgentEvent.TextDelta) e).text())
                    .forEach(responseText::append);

            applyInferenceResult(state, responseText.toString());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "LLM inference failed for " + state.subjectId, e);
        }
    }

    private void applyInferenceResult(SubjectMentalState state, String json) {
        try {
            json = json.strip();
            if (json.startsWith("```")) {
                json = json.replaceFirst("```[a-z]*\\n?", "").replaceFirst("\\n?```$", "").strip();
            }
            var now = clock.instant();

            mergeInferred(state.beliefs, extractArray(json, "beliefs"), BdiDimension.BELIEF, now);
            mergeInferred(state.desires, extractArray(json, "desires"), BdiDimension.DESIRE, now);
            mergeInferred(state.intentions, extractArray(json, "intentions"), BdiDimension.INTENTION, now);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to parse inference result: " + json, e);
        }
    }

    private void mergeInferred(Map<String, AttributedState> map, List<InferredEntry> entries,
                               BdiDimension dimension, Instant now) {
        for (var entry : entries) {
            var existing = map.get(entry.key);
            int entrenchment = dimension == BdiDimension.BELIEF
                    ? (existing != null ? existing.entrenchment() + 1 : 1)
                    : 0;
            map.put(entry.key, new AttributedState(entry.key, entry.text,
                    entry.confidence, entrenchment, now, dimension));
        }
    }

    private List<InferredEntry> extractArray(String json, String field) {
        var result = new ArrayList<InferredEntry>();
        var needle = "\"" + field + "\"";
        int idx = json.indexOf(needle);
        if (idx < 0) return result;
        int arrStart = json.indexOf('[', idx);
        if (arrStart < 0) return result;

        int depth = 0;
        int arrEnd = arrStart;
        for (int i = arrStart; i < json.length(); i++) {
            if (json.charAt(i) == '[') depth++;
            else if (json.charAt(i) == ']') {
                depth--;
                if (depth == 0) { arrEnd = i; break; }
            }
        }
        var arrStr = json.substring(arrStart + 1, arrEnd);

        int objDepth = 0;
        int objStart = -1;
        for (int i = 0; i < arrStr.length(); i++) {
            if (arrStr.charAt(i) == '{') {
                if (objDepth == 0) objStart = i;
                objDepth++;
            } else if (arrStr.charAt(i) == '}') {
                objDepth--;
                if (objDepth == 0 && objStart >= 0) {
                    var obj = arrStr.substring(objStart, i + 1);
                    var key = extractJsonString(obj, "key");
                    var text = extractJsonString(obj, "text");
                    var confStr = extractJsonValue(obj, "confidence");
                    if (key != null && text != null && confStr != null) {
                        try {
                            double conf = Double.parseDouble(confStr);
                            if (conf >= 0.3) result.add(new InferredEntry(key, text, conf));
                        } catch (NumberFormatException ignored) {}
                    }
                    objStart = -1;
                }
            }
        }
        return result;
    }

    private @Nullable String extractJsonString(String json, String field) {
        var needle = "\"" + field + "\"";
        int idx = json.indexOf(needle);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + needle.length());
        if (colon < 0) return null;
        int quote = json.indexOf('"', colon + 1);
        if (quote < 0) return null;
        var sb = new StringBuilder();
        for (int i = quote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                sb.append(json.charAt(++i));
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private @Nullable String extractJsonValue(String json, String field) {
        var needle = "\"" + field + "\"";
        int idx = json.indexOf(needle);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + needle.length());
        if (colon < 0) return null;
        int start = colon + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        int end = start;
        while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
        return json.substring(start, end).strip();
    }

    private String formatBdi(List<AttributedState> states) {
        if (states.isEmpty()) return "none";
        var sb = new StringBuilder();
        for (var s : states) {
            if (!sb.isEmpty()) sb.append("; ");
            sb.append(s.key()).append("=").append(s.description())
                    .append(" (conf=").append(String.format("%.2f", s.confidence())).append(")");
        }
        return sb.toString();
    }

    private void projectDimension(Map<String, AttributedState> map,
                                  List<MentalProjection> projections) {
        for (var entry : map.values()) {
            if (entry.confidence() >= config.projectionFloor()) {
                projections.add(new MentalProjection(
                        entry.key(), true, entry.confidence(), entry.dimension()));
            }
        }
    }

    private MentalModelSnapshot buildSnapshot(SubjectMentalState state, Instant now) {
        synchronized (state) {
            return new MentalModelSnapshot(
                    state.agentId, state.subjectId, state.tenantId,
                    List.copyOf(state.beliefs.values()),
                    List.copyOf(state.desires.values()),
                    List.copyOf(state.intentions.values()),
                    state.lastSignalTimestamp != null ? state.lastSignalTimestamp : now,
                    state.lastInferenceTimestamp,
                    now);
        }
    }

    private void evictStale() {
        var now = clock.instant();
        var timeout = config.evictionTimeout();
        states.entrySet().removeIf(entry -> {
            var st = entry.getValue();
            return st.lastActivityTimestamp != null
                    && Duration.between(st.lastActivityTimestamp, now).compareTo(timeout) > 0;
        });
    }

    private static String stateKey(String agentId, String subjectId, String tenantId) {
        return agentId + ":" + subjectId + ":" + tenantId;
    }

    private record InferredEntry(String key, String text, double confidence) {}

    static final class SubjectMentalState {
        final String agentId;
        final String subjectId;
        final String tenantId;

        final Map<String, AttributedState> beliefs = new LinkedHashMap<>();
        final Map<String, AttributedState> desires = new LinkedHashMap<>();
        final Map<String, AttributedState> intentions = new LinkedHashMap<>();

        int pendingSignals;
        private final ArrayDeque<String> signalBuffer;
        private final int maxBufferSize;

        @Nullable Instant lastSignalTimestamp;
        @Nullable Instant lastInferenceTimestamp;
        @Nullable Instant lastActivityTimestamp;
        @Nullable MentalModelSnapshot currentSnapshot;

        SubjectMentalState(String agentId, String subjectId, String tenantId,
                           int maxBufferSize, Clock clock) {
            this.agentId = agentId;
            this.subjectId = subjectId;
            this.tenantId = tenantId;
            this.maxBufferSize = maxBufferSize;
            this.signalBuffer = new ArrayDeque<>(Math.min(maxBufferSize, 16));
            this.lastActivityTimestamp = clock.instant();
        }

        void appendSignal(String text) {
            if (signalBuffer.size() >= maxBufferSize) {
                signalBuffer.pollFirst();
            }
            signalBuffer.addLast(text);
        }

        String drainBuffer() {
            var sb = new StringBuilder();
            for (var s : signalBuffer) {
                sb.append(s).append("\n");
            }
            signalBuffer.clear();
            return sb.toString();
        }

        static SubjectMentalState fromSnapshot(MentalModelSnapshot snapshot,
                                               int maxBufferSize, Clock clock) {
            var state = new SubjectMentalState(snapshot.agentId(), snapshot.subjectId(),
                    snapshot.tenantId(), maxBufferSize, clock);
            for (var b : snapshot.beliefs()) state.beliefs.put(b.key(), b);
            for (var d : snapshot.desires()) state.desires.put(d.key(), d);
            for (var i : snapshot.intentions()) state.intentions.put(i.key(), i);
            state.lastSignalTimestamp = snapshot.lastSignal();
            state.lastInferenceTimestamp = snapshot.lastInference();
            state.currentSnapshot = snapshot;
            return state;
        }
    }
}
