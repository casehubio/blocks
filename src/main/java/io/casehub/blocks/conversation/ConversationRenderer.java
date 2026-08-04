package io.casehub.blocks.conversation;

import io.casehub.work.progress.ProgressInstance;

public class ConversationRenderer {

    private static final String DEFAULT_EMOJI = "⬜";

    private final ConversationRendererConfig config;
    private final ProgressRenderer progressRenderer;

    public ConversationRenderer(ConversationRendererConfig config) {
        this(config, new DefaultProgressRenderer());
    }

    public ConversationRenderer(ConversationRendererConfig config,
                                ProgressRenderer progressRenderer) {
        this.config = config;
        this.progressRenderer = progressRenderer;
    }

    public String render(ConversationState state) {
        return render(state, RenderContext.EMPTY);
    }

    public String render(ConversationState state, RenderContext ctx) {
        var sb = new StringBuilder();
        sb.append("# Conversation Summary\n\n---\n\n");

        if (config.showConvergenceSignal() && ctx.convergence() != null) {
            ConvergenceSignal signal = ctx.convergence();
            sb.append("**Convergence:** ").append(signal.state())
              .append(" (").append(String.format("%.2f", signal.confidence())).append(")")
              .append(" — ").append(signal.reason()).append("\n\n");
        }

        if (config.groupByTopic()) {
            renderByTopic(sb, state, ctx);
        } else {
            renderFlat(sb, state, ctx);
        }

        if (!state.humanFlags().isEmpty()) {
            sb.append("⚑ **Human review needed:**\n");
            for (FlagEntry flag : state.humanFlags()) {
                sb.append("- ").append(flag.content()).append("\n");
            }
        }

        java.util.List<SubTaskFinding> standaloneFindings = state.subTaskFindings().values().stream()
                                                                 .filter(f -> f.pointId() == null)
                                                                 .toList();
        if (!standaloneFindings.isEmpty()) {
            sb.append("\n---\n\n**Sub-task findings**\n\n");
            for (SubTaskFinding f : standaloneFindings) {
                sb.append(renderFinding(f));
            }
        }

        if (!state.memos().isEmpty()) {
            sb.append("\n---\n\n**Agent Memos**\n\n");
            for (RoundMemo memo : state.memos()) {
                String roleDisplay = config.roleLabel().getOrDefault(memo.role(), memo.role());
                sb.append("**").append(roleDisplay).append(" memo — Round ").append(memo.round())
                  .append(":** ").append(memo.content()).append("\n\n");
            }
        }

        return sb.toString();
    }

    private void renderFlat(StringBuilder sb, ConversationState state, RenderContext ctx) {
        if (config.showProgress() && !ctx.progress().isEmpty()) {
            sb.append("**Progress**\n");
            for (var topicEntry : ctx.progress().entrySet()) {
                if (ctx.progress().size() > 1) {
                    sb.append("_").append(topicEntry.getKey()).append(":_ ");
                }
                for (ProgressInstance pi : topicEntry.getValue()) {
                    sb.append(progressRenderer.render(pi)).append("\n");
                }
            }
            sb.append("\n");
        }

        java.util.List<ConversationPoint> unresolved = new java.util.ArrayList<>();
        java.util.List<ConversationPoint> escalated  = new java.util.ArrayList<>();
        java.util.List<ConversationPoint> resolved   = new java.util.ArrayList<>();

        for (ConversationPoint point : state.points().values()) {
            if (config.resolvedStatuses().contains(point.status())) {
                resolved.add(point);
            } else if (config.escalatedStatuses().contains(point.status())) {
                escalated.add(point);
            } else {
                unresolved.add(point);
            }
        }

        renderPoints(sb, unresolved, state, false, ctx);
        renderPoints(sb, escalated, state, false, ctx);
        renderPoints(sb, resolved, state, true, ctx);
    }

    private void renderByTopic(StringBuilder sb, ConversationState state, RenderContext ctx) {
        var topicOrder = new java.util.LinkedHashMap<String, java.util.List<ConversationPoint>>();
        for (ConversationPoint point : state.points().values()) {
            String topic = point.topic() != null ? point.topic() : "general";
            topicOrder.computeIfAbsent(topic, k -> new java.util.ArrayList<>()).add(point);
        }

        for (var entry : topicOrder.entrySet()) {
            sb.append("## ").append(entry.getKey()).append("\n\n");

            if (config.showProgress() && ctx.progress().containsKey(entry.getKey())) {
                for (ProgressInstance pi : ctx.progress().get(entry.getKey())) {
                    sb.append(progressRenderer.render(pi)).append("\n");
                }
                sb.append("\n");
            }

            if (config.showObligationChain()) {
                String chain = renderObligationChain(entry.getValue());
                if (!chain.isEmpty()) {
                    sb.append(chain).append("\n\n");
                }
            }

            if (config.showEpistemicStatus() && ctx.commonGround() != null) {
                CommonGroundState cg   = ctx.commonGround();
                long              est  = entry.getValue().stream().filter(p -> cg.establishedFacts().containsKey(p.id())).count();
                long              pend = entry.getValue().stream().filter(p -> cg.pendingClaims().containsKey(p.id())).count();
                long              disp = entry.getValue().stream().filter(p -> cg.disputedPoints().containsKey(p.id())).count();
                sb.append(est).append(" established, ").append(pend).append(" pending, ").append(disp).append(" disputed\n\n");
            }

            java.util.List<ConversationPoint> unresolved = new java.util.ArrayList<>();
            java.util.List<ConversationPoint> escalated  = new java.util.ArrayList<>();
            java.util.List<ConversationPoint> resolved   = new java.util.ArrayList<>();

            for (ConversationPoint point : entry.getValue()) {
                if (config.resolvedStatuses().contains(point.status())) {
                    resolved.add(point);
                } else if (config.escalatedStatuses().contains(point.status())) {
                    escalated.add(point);
                } else {
                    unresolved.add(point);
                }
            }

            renderPoints(sb, unresolved, state, false, ctx);
            renderPoints(sb, escalated, state, false, ctx);
            renderPoints(sb, resolved, state, true, ctx);
        }
    }

    private String renderObligationChain(java.util.List<ConversationPoint> points) {
        java.util.List<io.casehub.qhorus.api.message.MessageType> types = new java.util.ArrayList<>();
        for (ConversationPoint point : points) {
            for (ThreadEntry entry : point.thread()) {
                if (entry.messageType() != null) {
                    types.add(entry.messageType());
                }
            }
        }
        if (types.isEmpty()) {return "";}

        var labels = new java.util.ArrayList<String>();
        for (var type : types) {
            labels.add(config.messageTypeLabel().getOrDefault(type, type.name().toLowerCase()));
        }
        String chain = String.join(" → ", labels);
        if (types.get(types.size() - 1) == io.casehub.qhorus.api.message.MessageType.DONE) {
            chain += " ✓";
        } else {
            chain += " ⏳";
        }
        return chain;
    }

    private void renderPoints(StringBuilder sb, java.util.List<ConversationPoint> points,
                              ConversationState state, boolean strikethrough, RenderContext ctx) {
        for (ConversationPoint point : points) {
            String emoji        = config.statusEmoji().getOrDefault(point.status(), DEFAULT_EMOJI);
            String firstContent = point.thread().isEmpty() ? "" : point.thread().get(0).content();

            String priorityDisplay = config.priorityLabel()
                                           .getOrDefault(point.classification().priority(),
                                                         point.classification().priority() != null
                                                         ? point.classification().priority().toString() : "");

            String header = "[" + point.id() + "] "
                            + priorityDisplay + " · "
                            + point.classification().scope()
                            + (point.classification().location() != null
                               ? " · " + point.classification().location() : "")
                            + " — " + firstContent;

            if (strikethrough) {
                sb.append("## ").append(emoji).append(" ~~").append(header).append("~~\n");
            } else {
                sb.append("## ").append(emoji).append(" ").append(header).append("\n");
            }

            if (config.showEpistemicStatus() && ctx.commonGround() != null) {
                renderEpistemicBadge(sb, point, ctx.commonGround());
            }

            for (ThreadEntry entry : point.thread()) {
                String typeLabel = config.entryTypeLabel()
                                         .getOrDefault(entry.entryType(), entry.entryType().toLowerCase());
                String roleDisplay = config.roleLabel()
                                           .getOrDefault(entry.role(), entry.role());
                sb.append("> **").append(roleDisplay).append(" (").append(typeLabel).append("):** ")
                  .append(entry.content()).append("\n");

                if (entry.messageId() != null && ctx.reactions().containsKey(entry.messageId())) {
                    var groups = ctx.reactions().get(entry.messageId());
                    if (!groups.isEmpty()) {
                        sb.append("> ");
                        for (int i = 0; i < groups.size(); i++) {
                            if (i > 0) {sb.append(" ");}
                            sb.append(groups.get(i).emoji()).append("×").append(groups.get(i).count());
                        }
                        sb.append("\n");
                    }
                }
            }

            state.subTaskFindings().values().stream()
                 .filter(f -> point.id().equals(f.pointId()))
                 .forEach(f -> sb.append(renderFinding(f)));

            sb.append("\n---\n\n");
        }
    }

    private void renderEpistemicBadge(StringBuilder sb, ConversationPoint point, CommonGroundState cg) {
        GroundedFact fact = cg.establishedFacts().get(point.id());
        if (fact == null) {fact = cg.pendingClaims().get(point.id());}
        if (fact == null) {fact = cg.disputedPoints().get(point.id());}
        if (fact == null) {return;}

        switch (fact.status()) {
            case ESTABLISHED -> sb.append("[established by ").append(String.join(", ", fact.acknowledgedBy())).append("]\n");
            case PENDING -> sb.append("[pending — awaiting acknowledgement]\n");
            case DISPUTED -> sb.append("[disputed by ").append(String.join(", ", fact.disputedBy())).append("]\n");
        }
    }

    private String renderFinding(SubTaskFinding f) {
        return switch (f.status()) {
            case PENDING -> "  ⏳ **" + f.taskType() + "** pending...\n";
            case FAULTED -> "  ✗ **" + f.taskType() + "** failed: " + f.errorReason() + "\n";
            case COMPLETED -> "  ⊕ **" + f.taskType() + "** _(fresh context — no prior round knowledge)_\n"
                              + "  " + java.util.Objects.requireNonNullElse(f.finding(), "(no finding)") + "\n";
            default -> "  **" + f.taskType() + "** " + f.status() + System.lineSeparator();
        };
    }
}
