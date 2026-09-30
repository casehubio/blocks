package io.casehub.blocks.agentic.cognition;

import io.casehub.blocks.speech.AvatarCognition;
import io.casehub.blocks.speech.AssembledPrompt;
import io.casehub.blocks.speech.ConversationTurn;
import io.casehub.blocks.speech.PromptContext;
import io.casehub.blocks.speech.SpeechPromptAssembler;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentRegistry;
import io.casehub.eidos.api.GoalLifecycleState;
import io.casehub.neocortex.cognition.core.CognitionCore;
import io.casehub.neocortex.cognition.goal.CognitiveGoalOrchestrator;
import io.casehub.neocortex.cognition.prompt.CognitionRenderContext;
import io.casehub.neocortex.cognition.prompt.ProactiveSpeechSupport;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

public class CognitionAvatarAdapter implements AvatarCognition {

    private static final System.Logger LOG = System.getLogger(CognitionAvatarAdapter.class.getName());

    private final CognitionCore core;
    private final @Nullable AgentRegistry agentRegistry;
    private final @Nullable CognitiveGoalOrchestrator cognitiveGoals;

    public CognitionAvatarAdapter(CognitionCore core,
                                   @Nullable AgentRegistry agentRegistry,
                                   @Nullable CognitiveGoalOrchestrator cognitiveGoals) {
        this.core = Objects.requireNonNull(core);
        this.agentRegistry = agentRegistry;
        this.cognitiveGoals = cognitiveGoals;
    }

    @Override
    public SpeechPromptAssembler wrapAssembler(SpeechPromptAssembler base, String agentId,
                                                String tenantId, Supplier<String> subjectIdSupplier) {
        var renderers = core.promptSections();
        return new SpeechPromptAssembler() {
            @Override
            public AssembledPrompt assemble(String userMessage, List<ConversationTurn> history) {
                var assembled = base.assemble(userMessage, history);
                var enriched = new StringBuilder(assembled.systemPrompt());
                var ctx = new PromptContext(agentId, tenantId, subjectIdSupplier.get());
                var renderCtx = new CognitionRenderContext(ctx.agentId(), ctx.tenantId(), ctx.subjectId());
                for (var renderer : renderers) {
                    try {
                        var contribution = renderer.render(renderCtx);
                        if (contribution != null) {
                            enriched.append("\n\n").append(contribution);
                        }
                    } catch (Exception e) {
                        LOG.log(System.Logger.Level.WARNING,
                                "Prompt renderer failed: " + renderer.getClass().getSimpleName(), e);
                    }
                }
                return new AssembledPrompt(enriched.toString(), assembled.userPrompt(), assembled.model());
            }
        };
    }

    @Override
    public void initialize(String agentId, String tenantId) {
        var descriptor = resolveDescriptor(agentId, tenantId);
        core.tick(agentId, tenantId, descriptor, (aid, tid) -> Set.of());
    }

    @Override
    public void tick(String agentId, String tenantId, Set<String> activeSubjects) {
        var descriptor = resolveDescriptor(agentId, tenantId);
        core.tick(agentId, tenantId, descriptor, (aid, tid) -> activeSubjects);
        consumeGoalRevisions(agentId, tenantId);
    }

    @Override
    public @Nullable String evaluateProactive(String agentId, String tenantId,
                                               String channelContext) {
        if (core.innerLife() == null || !core.config().innerLifeEnabled() || agentRegistry == null) {
            return null;
        }
        return agentRegistry.findById(agentId, tenantId)
                .map(desc -> new ProactiveSpeechSupport(core.innerLife(), desc)
                        .evaluateProactive(channelContext))
                .orElse(null);
    }

    @Override
    public void recordInteraction(String agentId, String tenantId,
                                  @Nullable String subjectId,
                                  String userMessage, String response) {
        core.recordInteraction(agentId, tenantId, subjectId, userMessage, response, null);
        if (core.innerLife() != null && agentRegistry != null) {
            agentRegistry.findById(agentId, tenantId)
                    .ifPresent(desc -> safeRun(() -> core.innerLife().observeResponse(desc)));
        }
    }

    private void consumeGoalRevisions(String agentId, String tenantId) {
        if (cognitiveGoals == null || agentRegistry == null) return;

        for (var revision : cognitiveGoals.pendingRevisions(agentId, tenantId)) {
            if (revision.eidosGoalName() == null) continue;

            GoalLifecycleState newState = switch (revision.decaySignal()) {
                case "dormant" -> GoalLifecycleState.DORMANT;
                case "abandon" -> GoalLifecycleState.ABANDONED;
                default -> null;
            };
            if (newState == null) continue;

            try {
                agentRegistry.updateGoalLifecycleState(
                        agentId, tenantId, revision.eidosGoalName(), newState);
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING,
                        "Failed to transition goal '" + revision.eidosGoalName() + "'", e);
            }
        }
    }

    private @Nullable AgentDescriptor resolveDescriptor(String agentId, String tenantId) {
        if (agentRegistry == null) return null;
        return agentRegistry.findById(agentId, tenantId).orElse(null);
    }

    private void safeRun(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "Signal recording failed", e);
        }
    }
}
