package io.casehub.blocks.agentic.social;

import io.casehub.blocks.agentic.social.drive.DriveOrchestrator;
import io.casehub.blocks.agentic.social.goal.GoalProposalOrchestrator;
import io.casehub.blocks.agentic.social.narrative.NarrativeOrchestrator;
import io.casehub.blocks.agentic.social.prompt.DirectiveSection;
import io.casehub.blocks.agentic.social.prompt.DrivePromptSection;
import io.casehub.blocks.agentic.social.prompt.GoalPromptSection;
import io.casehub.blocks.agentic.social.prompt.MentalModelPromptSection;
import io.casehub.blocks.agentic.social.prompt.MoodPromptSection;
import io.casehub.blocks.agentic.social.prompt.NarrativePromptSection;
import io.casehub.blocks.agentic.social.prompt.StrategyPromptSection;
import io.casehub.blocks.agentic.social.prompt.UserModelPromptSection;
import io.casehub.blocks.memory.MemoryHygieneOrchestrator;
import io.casehub.blocks.speech.PromptSection;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.neocortex.memory.engagement.EngagementEvent;
import io.casehub.neocortex.memory.relationship.QualitySignal;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CognitionCore {

    private static final System.Logger LOG =
            System.getLogger(CognitionCore.class.getName());

    private final MoodOrchestrator mood;
    private final DriveOrchestrator drives;
    private final @Nullable UserModelOrchestrator userModel;
    private final @Nullable MentalModelOrchestrator mentalModel;
    private final @Nullable StrategyLearningOrchestrator strategy;
    private final @Nullable NarrativeOrchestrator narrative;
    private final @Nullable GoalProposalOrchestrator goals;
    private final @Nullable MemoryHygieneOrchestrator memoryHygiene;
    private final @Nullable AgentProvider agentProvider;
    private final CognitionConfig config;

    public CognitionCore(MoodOrchestrator mood,
                          DriveOrchestrator drives,
                          @Nullable UserModelOrchestrator userModel,
                          @Nullable MentalModelOrchestrator mentalModel,
                          @Nullable StrategyLearningOrchestrator strategy,
                          @Nullable NarrativeOrchestrator narrative,
                          @Nullable GoalProposalOrchestrator goals,
                          @Nullable MemoryHygieneOrchestrator memoryHygiene) {
        this(mood, drives, userModel, mentalModel, strategy, narrative,
                goals, memoryHygiene, null, CognitionConfig.all());
    }

    public CognitionCore(MoodOrchestrator mood,
                          DriveOrchestrator drives,
                          @Nullable UserModelOrchestrator userModel,
                          @Nullable MentalModelOrchestrator mentalModel,
                          @Nullable StrategyLearningOrchestrator strategy,
                          @Nullable NarrativeOrchestrator narrative,
                          @Nullable GoalProposalOrchestrator goals,
                          @Nullable MemoryHygieneOrchestrator memoryHygiene,
                          @Nullable AgentProvider agentProvider) {
        this(mood, drives, userModel, mentalModel, strategy, narrative,
                goals, memoryHygiene, agentProvider, CognitionConfig.all());
    }

    public CognitionCore(MoodOrchestrator mood,
                          DriveOrchestrator drives,
                          @Nullable UserModelOrchestrator userModel,
                          @Nullable MentalModelOrchestrator mentalModel,
                          @Nullable StrategyLearningOrchestrator strategy,
                          @Nullable NarrativeOrchestrator narrative,
                          @Nullable GoalProposalOrchestrator goals,
                          @Nullable MemoryHygieneOrchestrator memoryHygiene,
                          @Nullable AgentProvider agentProvider,
                          CognitionConfig config) {
        this.mood = mood;
        this.drives = drives;
        this.userModel = userModel;
        this.mentalModel = mentalModel;
        this.strategy = strategy;
        this.narrative = narrative;
        this.goals = goals;
        this.memoryHygiene = memoryHygiene;
        this.agentProvider = agentProvider;
        this.config = config;
    }

    public void tick(String agentId, String tenantId,
                     @Nullable AgentDescriptor descriptor,
                     Set<String> activeSubjects) {
        if (config.moodEnabled()) {
            if (mood.currentMood(agentId, tenantId).isEmpty()) {
                mood.record(new MoodSignal.InteractionAppraisal(0, 0, 0, "init"),
                        agentId, tenantId);
            }
            mood.tick(agentId, tenantId);
        }

        if (config.memoryHygieneEnabled() && memoryHygiene != null) {
            safeRun(() -> memoryHygiene.tick(agentId, tenantId));
        }

        if (config.narrativeEnabled() && narrative != null) {
            safeRun(() -> narrative.tick(agentId, tenantId));
        }

        if (config.drivesEnabled() && descriptor != null) {
            safeRun(() -> drives.tick(agentId, tenantId, descriptor));
        }

        if (config.strategyEnabled() && strategy != null) {
            safeRun(() -> strategy.tick(agentId, tenantId));
        }

        for (String subjectId : activeSubjects) {
            if (config.userModelEnabled() && userModel != null) {
                safeRun(() -> userModel.tick(agentId, subjectId, tenantId));
            }
            if (config.mentalModelEnabled() && mentalModel != null) {
                safeRun(() -> mentalModel.tick(agentId, subjectId, tenantId));
            }
        }

        if (config.goalsEnabled() && goals != null && descriptor != null) {
            safeRun(() -> goals.tick(agentId, tenantId, descriptor));
        }
    }

    public void recordInteraction(String agentId, String tenantId,
                                   @Nullable String subjectId,
                                   String userMessage, String response) {
        if (config.moodEnabled()) {
            safeRun(() -> appraiseMood(agentId, tenantId, userMessage, response));
        }

        if (subjectId != null) {
            if (config.userModelEnabled() && userModel != null) {
                safeRun(() -> userModel.record(
                        new InteractionSignal.CustomSignal(
                                userMessage, QualitySignal.NEUTRAL),
                        agentId, subjectId, tenantId));
            }
            if (config.mentalModelEnabled() && mentalModel != null) {
                safeRun(() -> extractAndRecordMentalState(
                        agentId, subjectId, tenantId, userMessage));
            }
            if (config.strategyEnabled() && strategy != null) {
                safeRun(() -> strategy.record(
                        new EngagementSignal.TurnOutcome(
                                new EngagementEvent(agentId, subjectId,
                                        tenantId, null,
                                        UUID.randomUUID().toString(),
                                        Instant.now(),
                                        userMessage.isBlank()
                                                ? "[interaction]"
                                                : userMessage,
                                        null, Map.of(), true, null,
                                        (int) response.length(),
                                        null, null, null),
                                Map.of(), response),
                        agentId, subjectId, tenantId));
            }
        }
    }

    private static final String MOOD_APPRAISAL_PROMPT = """
            Rate the emotional tone of this conversation exchange from the \
            perspective of the agent who just responded. Score each PAD axis \
            as a delta (change from neutral):
            - pleasure: how pleasant/unpleasant was this exchange? [-0.3, +0.3]
            - arousal: how energizing/calming? [-0.3, +0.3]
            - dominance: how empowering/diminishing? [-0.3, +0.3]
            
            Respond with JSON only:
            {"pleasure":0.15,"arousal":0.1,"dominance":0.05,"cause":"mutual intellectual recognition"}""";

    private void appraiseMood(String agentId, String tenantId,
                               String userMessage, String response) {
        if (agentProvider == null) return;
        try {
            var truncatedMsg = userMessage.length() > 200
                    ? userMessage.substring(0, 200) + "..." : userMessage;
            var truncatedResp = response.length() > 200
                    ? response.substring(0, 200) + "..." : response;
            var userPrompt = "Other person said:\n" + truncatedMsg
                    + "\n\nAgent responded:\n" + truncatedResp;
            var config = AgentSessionConfig.of(MOOD_APPRAISAL_PROMPT, userPrompt);
            var sb = new StringBuilder();
            agentProvider.invoke(config)
                    .subscribe().asStream()
                    .filter(e -> e instanceof AgentEvent.TextDelta)
                    .map(e -> ((AgentEvent.TextDelta) e).text())
                    .forEach(sb::append);
            var json = sb.toString();
            var jsonStart = json.indexOf('{');
            var jsonEnd = json.lastIndexOf('}');
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                json = json.substring(jsonStart, jsonEnd + 1);
                double p = extractDouble(json, "pleasure");
                double a = extractDouble(json, "arousal");
                double d = extractDouble(json, "dominance");
                var cause = extractJsonString(json, "cause");
                p = Math.clamp(p, -0.3, 0.3);
                a = Math.clamp(a, -0.3, 0.3);
                d = Math.clamp(d, -0.3, 0.3);
                mood.record(new MoodSignal.InteractionAppraisal(p, a, d,
                        cause.isEmpty() ? "interaction" : cause),
                        agentId, tenantId);
            }
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "Mood appraisal failed", e);
        }
    }

    private static double extractDouble(String json, String key) {
        var pattern = java.util.regex.Pattern.compile(
                "\"" + key + "\"\\s*:\\s*(-?\\d+\\.?\\d*)");
        var matcher = pattern.matcher(json);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : 0.0;
    }

    private static String extractJsonString(String json, String key) {
        var pattern = java.util.regex.Pattern.compile(
                "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        var matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static final String BDI_EXTRACTION_PROMPT = """
            Extract mental state signals from what this person said. \
            Classify each statement as a belief (what they think is true), \
            desire (what they want), or intention (what they plan to do).
            
            Respond with JSON only. Use short descriptive keys (2-4 words, \
            kebab-case). Only include clear, confident signals — skip vague \
            or ambiguous statements.
            
            {"beliefs":[{"key":"values-universal-energy","text":"Believes energy should be freely available to all","type":"BELIEF_STATEMENT"}],\
            "desires":[{"key":"recognition-for-ac","text":"Wants recognition for alternating current work","type":"DESIRE_EXPRESSION"}],\
            "intentions":[{"key":"build-wardenclyffe","text":"Plans to build the Wardenclyffe tower","type":"INTENTION_DECLARATION"}]}""";

    private void extractAndRecordMentalState(String agentId, String subjectId,
                                              String tenantId, String utterance) {
        if (agentProvider != null) {
            try {
                var truncated = utterance.length() > 500
                        ? utterance.substring(0, 500) + "..." : utterance;
                var config = AgentSessionConfig.of(BDI_EXTRACTION_PROMPT,
                        "What " + subjectId + " said:\n" + truncated);
                var sb = new StringBuilder();
                agentProvider.invoke(config)
                        .subscribe().asStream()
                        .filter(e -> e instanceof AgentEvent.TextDelta)
                        .map(e -> ((AgentEvent.TextDelta) e).text())
                        .forEach(sb::append);
                var json = sb.toString();
                recordExtractedSignals(agentId, subjectId, tenantId, json);
                return;
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING,
                        "LLM BDI extraction failed, falling back to heuristic", e);
            }
        }
        mentalModel.record(new MentalStateSignal.VerbalCue(
                utterance.length() > 100 ? utterance.substring(0, 100) : utterance,
                CueType.BELIEF_STATEMENT), agentId, subjectId, tenantId);
    }

    private void recordExtractedSignals(String agentId, String subjectId,
                                         String tenantId, String json) {
        var jsonStart = json.indexOf('{');
        var jsonEnd = json.lastIndexOf('}');
        if (jsonStart < 0 || jsonEnd < 0) return;
        json = json.substring(jsonStart, jsonEnd + 1);

        recordSignalArray(agentId, subjectId, tenantId, json,
                "beliefs", CueType.BELIEF_STATEMENT);
        recordSignalArray(agentId, subjectId, tenantId, json,
                "desires", CueType.DESIRE_EXPRESSION);
        recordSignalArray(agentId, subjectId, tenantId, json,
                "intentions", CueType.INTENTION_DECLARATION);
    }

    private void recordSignalArray(String agentId, String subjectId,
                                    String tenantId, String json,
                                    String arrayName, CueType cueType) {
        var arrayPattern = java.util.regex.Pattern.compile(
                "\"" + arrayName + "\"\\s*:\\s*\\[([^\\]]*)]");
        var arrayMatcher = arrayPattern.matcher(json);
        if (!arrayMatcher.find()) return;
        var arrayContent = arrayMatcher.group(1);

        var itemPattern = java.util.regex.Pattern.compile(
                "\"text\"\\s*:\\s*\"([^\"]+)\"");
        var itemMatcher = itemPattern.matcher(arrayContent);
        while (itemMatcher.find()) {
            var text = itemMatcher.group(1);
            mentalModel.record(new MentalStateSignal.VerbalCue(text, cueType),
                    agentId, subjectId, tenantId);
        }
    }

    public List<PromptSection> promptSections() {
        var sections = new ArrayList<PromptSection>();
        if (config.moodEnabled())
            sections.add(new MoodPromptSection(mood));
        if (config.drivesEnabled())
            sections.add(new DrivePromptSection(drives));
        if (config.narrativeEnabled() && narrative != null)
            sections.add(new NarrativePromptSection(narrative));
        if (config.userModelEnabled() && userModel != null)
            sections.add(new UserModelPromptSection(userModel));
        if (config.mentalModelEnabled() && mentalModel != null)
            sections.add(new MentalModelPromptSection(mentalModel));
        if (config.strategyEnabled() && strategy != null)
            sections.add(new StrategyPromptSection(strategy));
        if (config.goalsEnabled() && goals != null)
            sections.add(new GoalPromptSection(goals));
        if (config.directivePrompts()) {
            return sections.stream().map(DirectiveSection::wrap).toList();
        }
        return sections;
    }

    public CognitionConfig config() { return config; }

    public MoodOrchestrator mood() { return mood; }
    public DriveOrchestrator drives() { return drives; }
    public @Nullable UserModelOrchestrator userModel() { return userModel; }
    public @Nullable MentalModelOrchestrator mentalModel() { return mentalModel; }
    public @Nullable StrategyLearningOrchestrator strategy() { return strategy; }
    public @Nullable NarrativeOrchestrator narrative() { return narrative; }
    public @Nullable GoalProposalOrchestrator goals() { return goals; }
    public @Nullable MemoryHygieneOrchestrator memoryHygiene() { return memoryHygiene; }

    private void safeRun(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING,
                    "Orchestrator operation failed", e);
        }
    }
}
