package io.casehub.blocks.conversation;

import io.casehub.api.model.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationRendererTest {

    // --- helpers ---

    static ConversationRendererConfig emptyConfig() {
        return ConversationRendererConfig.builder().build();
    }

    static ConversationRendererConfig reviewConfig() {
        return ConversationRendererConfig.builder()
                .statusEmoji(Map.of(
                        "OPEN", "🔴",       // red circle
                        "ACTIVE", "🟡",     // yellow circle
                        "AGREED", "✅",
                        "PENDING_HUMAN", "🔵", // blue circle
                        "DECLINED", "🚫",    // no entry
                        "DISPUTED", "⚡"))
                .priorityLabel(Map.of(
                        Priority.HIGH, "P1",
                        Priority.MEDIUM, "P2",
                        Priority.LOW, "P3"))
                .entryTypeLabel(Map.of(
                        "RAISE", "raise",
                        "AGREE", "agree",
                        "COUNTER", "counter",
                        "DISPUTE", "dispute",
                        "QUALIFY", "qualify",
                        "FLAG_HUMAN", "flag",
                        "DECLINED", "declined"))
                .roleLabel(Map.of(
                        "REV", "Reviewer",
                        "IMP", "Implementor"))
                .resolvedStatuses(Set.of("AGREED", "DECLINED"))
                .escalatedStatuses(Set.of("PENDING_HUMAN"))
                .build();
    }

    static ConversationPoint point(String id, Priority priority, String scope, String location,
                                   String status, List<ThreadEntry> thread) {
        return new ConversationPoint(id, "general", new PointClassification(priority, scope, location), thread, status);
    }

    static ThreadEntry entry(String role, String entryType, String content) {
        return new ThreadEntry(null, null, null, null, null, role, 1, entryType, content);
    }

    static ConversationState state(Map<String, ConversationPoint> points,
                                    List<FlagEntry> flags,
                                    List<RoundMemo> memos,
                                    Map<String, SubTaskFinding> findings) {
        return new ConversationState(points, flags, memos, findings);
    }

    // --- Tests ---

    @Test
    void emptyState_producesHeaderOnly() {
        var renderer = new ConversationRenderer(emptyConfig());
        var result = renderer.render(state(Map.of(), List.of(), List.of(), Map.of()));
        assertThat(result).isEqualTo("# Conversation Summary\n\n---\n\n");
    }

    @Test
    void singlePointWithThread_formatsCorrectly() {
        var points = new LinkedHashMap<String, ConversationPoint>();
        points.put("RP-1", point("RP-1", Priority.HIGH, "error-handling", "api/Handler.java:42",
                "OPEN",
                List.of(entry("REV", "RAISE", "Missing null check"),
                        entry("IMP", "COUNTER", "Handled upstream"))));

        var renderer = new ConversationRenderer(reviewConfig());
        var result = renderer.render(state(points, List.of(), List.of(), Map.of()));

        assertThat(result).contains("## 🔴 [RP-1] P1");
        assertThat(result).contains("error-handling");
        assertThat(result).contains("api/Handler.java:42");
        assertThat(result).contains("Missing null check");
        assertThat(result).contains("> **Reviewer (raise):** Missing null check");
        assertThat(result).contains("> **Implementor (counter):** Handled upstream");
    }

    @Test
    void pointsGrouped_unresolvedBeforeEscalatedBeforeResolved() {
        var points = new LinkedHashMap<String, ConversationPoint>();
        points.put("RP-1", point("RP-1", Priority.LOW, "style", null, "AGREED",
                List.of(entry("REV", "RAISE", "Resolved point"))));
        points.put("RP-2", point("RP-2", Priority.HIGH, "bug", null, "OPEN",
                List.of(entry("REV", "RAISE", "Unresolved point"))));
        points.put("RP-3", point("RP-3", Priority.MEDIUM, "design", null, "PENDING_HUMAN",
                List.of(entry("REV", "RAISE", "Escalated point"))));

        var renderer = new ConversationRenderer(reviewConfig());
        var result = renderer.render(state(points, List.of(), List.of(), Map.of()));

        int unresolvedPos = result.indexOf("Unresolved point");
        int escalatedPos = result.indexOf("Escalated point");
        int resolvedPos = result.indexOf("Resolved point");

        assertThat(unresolvedPos).isLessThan(escalatedPos);
        assertThat(escalatedPos).isLessThan(resolvedPos);
    }

    @Test
    void resolvedPoints_haveStrikethrough() {
        var points = new LinkedHashMap<String, ConversationPoint>();
        points.put("RP-1", point("RP-1", Priority.LOW, "style", null, "AGREED",
                List.of(entry("REV", "RAISE", "Agreed point"))));
        points.put("RP-2", point("RP-2", Priority.LOW, "style", null, "DECLINED",
                List.of(entry("REV", "RAISE", "Declined point"))));

        var renderer = new ConversationRenderer(reviewConfig());
        var result = renderer.render(state(points, List.of(), List.of(), Map.of()));

        assertThat(result).contains("~~[RP-1]");
        assertThat(result).contains("~~[RP-2]");
    }

    @Test
    void unresolvedPoints_noStrikethrough() {
        var points = new LinkedHashMap<String, ConversationPoint>();
        points.put("RP-1", point("RP-1", Priority.HIGH, "bug", null, "OPEN",
                List.of(entry("REV", "RAISE", "Open point"))));

        var renderer = new ConversationRenderer(reviewConfig());
        var result = renderer.render(state(points, List.of(), List.of(), Map.of()));

        assertThat(result).doesNotContain("~~");
    }

    @Test
    void unknownStatus_usesDefaultEmoji() {
        var points = new LinkedHashMap<String, ConversationPoint>();
        points.put("RP-1", point("RP-1", Priority.HIGH, "bug", null, "CUSTOM_STATUS",
                List.of(entry("REV", "RAISE", "Custom status"))));

        var renderer = new ConversationRenderer(reviewConfig());
        var result = renderer.render(state(points, List.of(), List.of(), Map.of()));

        assertThat(result).contains("## ⬜ [RP-1]");  // white square default
    }

    @Test
    void defaultConfig_usesRawStrings() {
        var points = new LinkedHashMap<String, ConversationPoint>();
        points.put("RP-1", point("RP-1", Priority.HIGH, "bug", null, "OPEN",
                List.of(entry("REV", "RAISE", "Raw test"))));

        var renderer = new ConversationRenderer(emptyConfig());
        var result = renderer.render(state(points, List.of(), List.of(), Map.of()));

        // No status emoji configured → default ⬜
        assertThat(result).contains("⬜");
        // No priority label configured → Priority.toString() = "HIGH"
        assertThat(result).contains("HIGH");
        // No entry type label configured → raw lowercase
        assertThat(result).contains("raise");
        // No role label configured → raw role string
        assertThat(result).contains("REV");
    }

    @Test
    void priorityLabel_configuredOverridesToString() {
        var config = ConversationRendererConfig.builder()
                .priorityLabel(Map.of(Priority.HIGH, "Critical"))
                .build();

        var points = new LinkedHashMap<String, ConversationPoint>();
        points.put("RP-1", point("RP-1", Priority.HIGH, "bug", null, "OPEN",
                List.of(entry("REV", "RAISE", "Priority test"))));

        var renderer = new ConversationRenderer(config);
        var result = renderer.render(state(points, List.of(), List.of(), Map.of()));

        assertThat(result).contains("Critical");
        assertThat(result).doesNotContain("HIGH");
    }

    @Test
    void entryTypeLabel_configuredOverridesDefault() {
        var config = ConversationRendererConfig.builder()
                .entryTypeLabel(Map.of("RAISE", "raised"))
                .build();

        var points = new LinkedHashMap<String, ConversationPoint>();
        points.put("RP-1", point("RP-1", Priority.LOW, "style", null, "OPEN",
                List.of(entry("REV", "RAISE", "Label test"))));

        var renderer = new ConversationRenderer(config);
        var result = renderer.render(state(points, List.of(), List.of(), Map.of()));

        assertThat(result).contains("(raised)");
    }

    @Test
    void roleLabel_configuredOverridesDefault() {
        var config = ConversationRendererConfig.builder()
                .roleLabel(Map.of("REV", "Critic"))
                .build();

        var points = new LinkedHashMap<String, ConversationPoint>();
        points.put("RP-1", point("RP-1", Priority.LOW, "style", null, "OPEN",
                List.of(entry("REV", "RAISE", "Role test"))));

        var renderer = new ConversationRenderer(config);
        var result = renderer.render(state(points, List.of(), List.of(), Map.of()));

        assertThat(result).contains("**Critic (raise):**");
    }

    @Test
    void subTaskFindings_pointSpecificRenderedInline() {
        var points = new LinkedHashMap<String, ConversationPoint>();
        points.put("RP-1", point("RP-1", Priority.HIGH, "bug", null, "OPEN",
                List.of(entry("REV", "RAISE", "Has sub-task"))));

        var findings = new LinkedHashMap<String, SubTaskFinding>();
        findings.put("st-1", new SubTaskFinding("st-1", "VERIFY", "REV", "RP-1",
                "Verified: bug confirmed", null, TaskStatus.COMPLETED));

        var renderer = new ConversationRenderer(emptyConfig());
        var result = renderer.render(state(points, List.of(), List.of(), findings));

        // Finding appears after the point's thread entries, before the separator
        int pointHeader = result.indexOf("[RP-1]");
        int findingPos = result.indexOf("VERIFY");
        int separator = result.indexOf("---", findingPos);

        assertThat(pointHeader).isGreaterThanOrEqualTo(0);
        assertThat(findingPos).isGreaterThan(pointHeader);
        assertThat(separator).isGreaterThan(findingPos);
    }

    @Test
    void subTaskFindings_standaloneRenderedSeparately() {
        var findings = new LinkedHashMap<String, SubTaskFinding>();
        findings.put("st-1", new SubTaskFinding("st-1", "NEUTRAL_SUMMARY", "REV", null,
                "Overall summary text", null, TaskStatus.COMPLETED));

        var renderer = new ConversationRenderer(emptyConfig());
        var result = renderer.render(state(Map.of(), List.of(), List.of(), findings));

        assertThat(result).contains("**Sub-task findings**");
        assertThat(result).contains("NEUTRAL_SUMMARY");
        assertThat(result).contains("Overall summary text");
    }

    @Test
    void subTaskFindings_statusRendering() {
        var findings = new LinkedHashMap<String, SubTaskFinding>();
        findings.put("st-p", new SubTaskFinding("st-p", "CHECK", "REV", null,
                null, null, TaskStatus.PENDING));
        findings.put("st-e", new SubTaskFinding("st-e", "VALIDATE", "REV", null,
                null, "timeout", TaskStatus.FAULTED));
        findings.put("st-c", new SubTaskFinding("st-c", "VERIFY", "REV", null,
                "All good", null, TaskStatus.COMPLETED));

        var renderer = new ConversationRenderer(emptyConfig());
        var result = renderer.render(state(Map.of(), List.of(), List.of(), findings));

        assertThat(result).contains("⏳ **CHECK** pending...");    // hourglass
        assertThat(result).contains("✗ **VALIDATE** failed: timeout");  // cross mark
        assertThat(result).contains("⊕ **VERIFY**");              // circled plus
        assertThat(result).contains("All good");
    }

    @Test
    void humanFlags_renderedInSection() {
        var flags = List.of(
                new FlagEntry("e1", 1, "REV", "Needs product owner input"),
                new FlagEntry("e2", 2, "IMP", "Architecture concern"));

        var renderer = new ConversationRenderer(emptyConfig());
        var result = renderer.render(state(Map.of(), flags, List.of(), Map.of()));

        assertThat(result).contains("⚑ **Human review needed:**");
        assertThat(result).contains("- Needs product owner input");
        assertThat(result).contains("- Architecture concern");
    }

    @Test
    void memos_groupedByRound() {
        var memos = List.of(
                new RoundMemo("REV", 1, "Initial assessment complete"),
                new RoundMemo("IMP", 1, "Will address in next push"),
                new RoundMemo("REV", 2, "Satisfied with changes"));

        var renderer = new ConversationRenderer(reviewConfig());
        var result = renderer.render(state(Map.of(), List.of(), memos, Map.of()));

        assertThat(result).contains("**Agent Memos**");
        assertThat(result).contains("**Reviewer memo — Round 1:**");
        assertThat(result).contains("Initial assessment complete");
        assertThat(result).contains("**Implementor memo — Round 1:**");
        assertThat(result).contains("**Reviewer memo — Round 2:**");
    }

    @Test
    void memos_defaultConfig_usesRawRoleString() {
        var memos = List.of(new RoundMemo("CUSTOM_ROLE", 1, "Some notes"));

        var renderer = new ConversationRenderer(emptyConfig());
        var result = renderer.render(state(Map.of(), List.of(), memos, Map.of()));

        assertThat(result).contains("**CUSTOM_ROLE memo");
    }

    @Test
    void locationOmitted_whenNull() {
        var points = new LinkedHashMap<String, ConversationPoint>();
        points.put("RP-1", point("RP-1", Priority.HIGH, "logic", null, "OPEN",
                List.of(entry("REV", "RAISE", "No location"))));

        var renderer = new ConversationRenderer(reviewConfig());
        var result = renderer.render(state(points, List.of(), List.of(), Map.of()));

        // Header should have "P1 · logic — No location" without extra " · null"
        assertThat(result).contains("P1 · logic — No location");
        assertThat(result).doesNotContain("null");
    }

    @Test
    void locationIncluded_whenPresent() {
        var points = new LinkedHashMap<String, ConversationPoint>();
        points.put("RP-1", point("RP-1", Priority.HIGH, "logic", "src/Main.java:10", "OPEN",
                List.of(entry("REV", "RAISE", "Has location"))));

        var renderer = new ConversationRenderer(reviewConfig());
        var result = renderer.render(state(points, List.of(), List.of(), Map.of()));

        assertThat(result).contains("P1 · logic · src/Main.java:10 — Has location");
    }

    @Test
    void completeRendering_allSections() {
        var points = new LinkedHashMap<String, ConversationPoint>();
        points.put("RP-1", point("RP-1", Priority.HIGH, "bug", "Handler.java:42", "ACTIVE",
                List.of(entry("REV", "RAISE", "NPE risk"),
                        entry("IMP", "QUALIFY", "Guarded by caller"))));
        points.put("RP-2", point("RP-2", Priority.LOW, "style", null, "AGREED",
                List.of(entry("REV", "RAISE", "Naming convention"),
                        entry("IMP", "AGREE", "Will fix"))));

        var flags = List.of(new FlagEntry("e1", 1, "REV", "Needs escalation"));
        var memos = List.of(new RoundMemo("REV", 1, "First pass done"));

        var inlineFindings = new LinkedHashMap<String, SubTaskFinding>();
        inlineFindings.put("st-1", new SubTaskFinding("st-1", "VERIFY", "REV", "RP-1",
                "Bug confirmed", null, TaskStatus.COMPLETED));
        inlineFindings.put("st-2", new SubTaskFinding("st-2", "SUMMARY", "REV", null,
                "Session wrap", null, TaskStatus.COMPLETED));

        var renderer = new ConversationRenderer(reviewConfig());
        var result = renderer.render(state(points, flags, memos, inlineFindings));

        // Unresolved (ACTIVE) before resolved (AGREED) — cross-group ordering
        assertThat(result.indexOf("NPE risk")).isLessThan(result.indexOf("Naming convention"));
        // Standalone findings section
        assertThat(result).contains("**Sub-task findings**");
        assertThat(result).contains("SUMMARY");
        // Flags section
        assertThat(result).contains("Needs escalation");
        // Memos section
        assertThat(result).contains("Reviewer memo");
        // Resolved point has strikethrough
        assertThat(result).contains("~~[RP-2]");
    }

    @Test
    void subTaskFinding_completedWithNullFinding_showsFallback() {
        var findings = new LinkedHashMap<String, SubTaskFinding>();
        findings.put("st-1", new SubTaskFinding("st-1", "VERIFY", "REV", null,
                null, null, TaskStatus.COMPLETED));

        var renderer = new ConversationRenderer(emptyConfig());
        var result = renderer.render(state(Map.of(), List.of(), List.of(), findings));

        assertThat(result).contains("(no finding)");
    }

    @Test
    void configDefensiveCopy_mutatingOriginalMapDoesNotAffectConfig() {
        var statusEmoji = new java.util.HashMap<String, String>();
        statusEmoji.put("OPEN", "🔴");

        var config = ConversationRendererConfig.builder()
                .statusEmoji(statusEmoji)
                .build();

        statusEmoji.put("EXTRA", "!");
        assertThat(config.statusEmoji()).doesNotContainKey("EXTRA");
    }

    @Test
    void emptyThread_firstContentIsEmpty() {
        var points = new LinkedHashMap<String, ConversationPoint>();
        points.put("RP-1", point("RP-1", Priority.LOW, "style", null, "OPEN", List.of()));

        var renderer = new ConversationRenderer(reviewConfig());
        var result = renderer.render(state(points, List.of(), List.of(), Map.of()));

        // Should still render the header without NPE
        assertThat(result).contains("[RP-1]");
        assertThat(result).contains("P3 · style —");
    }

    @Test
    void noTimestamp_inOutput() {
        var points = new LinkedHashMap<String, ConversationPoint>();
        points.put("RP-1", point("RP-1", Priority.HIGH, "bug", null, "OPEN",
                List.of(entry("REV", "RAISE", "Test"))));

        var renderer = new ConversationRenderer(reviewConfig());
        var result = renderer.render(state(points, List.of(), List.of(), Map.of()));

        assertThat(result).doesNotContain("Updated:");
        assertThat(result).doesNotContain("Instant");
    }

    @Test
    void allPointsInSameGroup_allRendered() {
        // Map.copyOf in ConversationState does not preserve insertion order,
        // so we verify all points appear — not their relative order.
        var points = new LinkedHashMap<String, ConversationPoint>();
        points.put("RP-1", point("RP-1", Priority.LOW, "style", null, "OPEN",
                List.of(entry("REV", "RAISE", "First unresolved"))));
        points.put("RP-2", point("RP-2", Priority.HIGH, "bug", null, "OPEN",
                List.of(entry("REV", "RAISE", "Second unresolved"))));
        points.put("RP-3", point("RP-3", Priority.MEDIUM, "design", null, "OPEN",
                List.of(entry("REV", "RAISE", "Third unresolved"))));

        var renderer = new ConversationRenderer(reviewConfig());
        var result = renderer.render(state(points, List.of(), List.of(), Map.of()));

        assertThat(result).contains("First unresolved");
        assertThat(result).contains("Second unresolved");
        assertThat(result).contains("Third unresolved");
    }

    @Test
    void topicGrouping_pointsGroupedByTopic() {
        var config = reviewConfig().toBuilder()
                                   .groupByTopic(true)
                                   .build();

        var points = new java.util.LinkedHashMap<String, ConversationPoint>();
        points.put("RP-1", new ConversationPoint("RP-1", "review",
                                                 new PointClassification(Priority.HIGH, "bug", null),
                                                 List.of(entry("REV", "RAISE", "Bug in review")), "OPEN"));
        points.put("RP-2", new ConversationPoint("RP-2", "analysis",
                                                 new PointClassification(Priority.MEDIUM, "design", null),
                                                 List.of(entry("REV", "RAISE", "Design issue")), "OPEN"));
        points.put("RP-3", new ConversationPoint("RP-3", "review",
                                                 new PointClassification(Priority.LOW, "style", null),
                                                 List.of(entry("REV", "RAISE", "Style nit")), "OPEN"));

        var renderer = new ConversationRenderer(config);
        var result   = renderer.render(state(points, List.of(), List.of(), Map.of()));

        assertThat(result).contains("## review");
        assertThat(result).contains("## analysis");
        int firstReview  = result.indexOf("Bug in review");
        int secondReview = result.indexOf("Style nit");
        int analysis     = result.indexOf("Design issue");
        assertThat(firstReview).isLessThan(secondReview);
        assertThat(secondReview).isLessThan(analysis);
    }

    @Test
    void obligationChain_rendersMessageTypeProgression() {
        var config = reviewConfig().toBuilder()
                                   .groupByTopic(true)
                                   .showObligationChain(true)
                                   .messageTypeLabel(Map.of(
                                           io.casehub.qhorus.api.message.MessageType.COMMAND, "commanded",
                                           io.casehub.qhorus.api.message.MessageType.STATUS, "progress",
                                           io.casehub.qhorus.api.message.MessageType.DONE, "completed"))
                                   .build();

        var points = new java.util.LinkedHashMap<String, ConversationPoint>();
        points.put("RP-1", new ConversationPoint("RP-1", "review",
                                                 new PointClassification(Priority.HIGH, "bug", null),
                                                 List.of(
                                                         new ThreadEntry("RP-1", 1L, io.casehub.qhorus.api.message.MessageType.COMMAND, null, null, "REV", 1, "RAISE", "Review this"),
                                                         new ThreadEntry(null, 2L, io.casehub.qhorus.api.message.MessageType.STATUS, null, null, "IMP", 1, "QUALIFY", "Working on it"),
                                                         new ThreadEntry(null, 3L, io.casehub.qhorus.api.message.MessageType.DONE, null, null, "IMP", 1, "AGREE", "Fixed")),
                                                 "AGREED"));

        var renderer = new ConversationRenderer(config);
        var result   = renderer.render(state(points, List.of(), List.of(), Map.of()));

        assertThat(result).contains("commanded → progress → completed ✓");
    }

    @Test
    void obligationChain_incompleteShowsHourglass() {
        var config = reviewConfig().toBuilder()
                                   .groupByTopic(true)
                                   .showObligationChain(true)
                                   .messageTypeLabel(Map.of(
                                           io.casehub.qhorus.api.message.MessageType.COMMAND, "commanded",
                                           io.casehub.qhorus.api.message.MessageType.STATUS, "progress"))
                                   .build();

        var points = new java.util.LinkedHashMap<String, ConversationPoint>();
        points.put("RP-1", new ConversationPoint("RP-1", "analysis",
                                                 new PointClassification(Priority.MEDIUM, "design", null),
                                                 List.of(
                                                         new ThreadEntry("RP-1", 1L, io.casehub.qhorus.api.message.MessageType.COMMAND, null, null, "REV", 1, "RAISE", "Analyse this"),
                                                         new ThreadEntry(null, 2L, io.casehub.qhorus.api.message.MessageType.STATUS, null, null, "IMP", 1, "QUALIFY", "In progress")),
                                                 "OPEN"));

        var renderer = new ConversationRenderer(config);
        var result   = renderer.render(state(points, List.of(), List.of(), Map.of()));

        assertThat(result).contains("commanded → progress ⏳");
    }

    @Test
    void reactions_renderedInlineAfterThreadEntries() {
        var points = new java.util.LinkedHashMap<String, ConversationPoint>();
        points.put("RP-1", new ConversationPoint("RP-1", "general",
                                                 new PointClassification(Priority.HIGH, "bug", null),
                                                 List.of(new ThreadEntry("RP-1", 10L, io.casehub.qhorus.api.message.MessageType.COMMAND, null, null, "REV", 1, "RAISE", "Bug found")),
                                                 "OPEN"));

        var reactions = Map.of(10L, List.of(
                new io.casehub.qhorus.api.message.ReactionGroup("👍", 3, List.of("a1", "a2", "a3")),
                new io.casehub.qhorus.api.message.ReactionGroup("✅", 1, List.of("a4"))));

        var renderer = new ConversationRenderer(emptyConfig());
        var result   = renderer.render(state(points, List.of(), List.of(), Map.of()), RenderContext.withReactions(reactions));

        assertThat(result).contains("👍×3");
        assertThat(result).contains("✅×1");
    }

    @Test
    void reactions_noReactionsForMessage_noDecoration() {
        var points = new java.util.LinkedHashMap<String, ConversationPoint>();
        points.put("RP-1", new ConversationPoint("RP-1", "general",
                                                 new PointClassification(Priority.HIGH, "bug", null),
                                                 List.of(new ThreadEntry("RP-1", 10L, null, null, null, "REV", 1, "RAISE", "No reactions")),
                                                 "OPEN"));

        var renderer = new ConversationRenderer(emptyConfig());
        var result   = renderer.render(state(points, List.of(), List.of(), Map.of()), RenderContext.withReactions(Map.of()));

        assertThat(result).doesNotContain("×");
    }


    @Test
    void epistemicBadges_renderedPerPoint() {
        var thread = List.of(entry("REV", "RAISE", "a claim"));
        var points = Map.of("p1", point("p1", Priority.HIGH, "test", null, "OPEN", thread));
        var config = reviewConfig().toBuilder()
                                   .showEpistemicStatus(true).build();
        var renderer = new ConversationRenderer(config);
        var cg = new CommonGroundState(
                Map.of("p1", new GroundedFact("p1", "general", EpistemicStatus.ESTABLISHED,
                                              "a claim", java.util.Set.of("agent-a"), java.util.Set.of(), 1)),
                Map.of(), Map.of());
        var ctx    = new RenderContext(Map.of(), cg, null, Map.of());
        var result = renderer.render(state(points, List.of(), List.of(), Map.of()), ctx);
        assertThat(result).contains("[established by agent-a]");
    }

    @Test
    void epistemicBadge_pending_showsAwaitingAcknowledgement() {
        var thread = List.of(entry("REV", "RAISE", "a claim"));
        var points = Map.of("p1", point("p1", Priority.HIGH, "test", null, "OPEN", thread));
        var config = reviewConfig().toBuilder()
                                   .showEpistemicStatus(true).build();
        var renderer = new ConversationRenderer(config);
        var cg = new CommonGroundState(Map.of(),
                                       Map.of("p1", new GroundedFact("p1", "general", EpistemicStatus.PENDING,
                                                                     "a claim", java.util.Set.of(), java.util.Set.of(), 1)),
                                       Map.of());
        var ctx    = new RenderContext(Map.of(), cg, null, Map.of());
        var result = renderer.render(state(points, List.of(), List.of(), Map.of()), ctx);
        assertThat(result).contains("[pending — awaiting acknowledgement]");
    }

    @Test
    void epistemicBadge_disputed_showsDisputedBy() {
        var thread = List.of(entry("REV", "RAISE", "a claim"));
        var points = Map.of("p1", point("p1", Priority.HIGH, "test", null, "OPEN", thread));
        var config = reviewConfig().toBuilder()
                                   .showEpistemicStatus(true).build();
        var renderer = new ConversationRenderer(config);
        var cg = new CommonGroundState(Map.of(), Map.of(),
                                       Map.of("p1", new GroundedFact("p1", "general", EpistemicStatus.DISPUTED,
                                                                     "a claim", java.util.Set.of(), java.util.Set.of("agent-b"), 1)));
        var ctx    = new RenderContext(Map.of(), cg, null, Map.of());
        var result = renderer.render(state(points, List.of(), List.of(), Map.of()), ctx);
        assertThat(result).contains("[disputed by agent-b]");
    }

    @Test
    void convergenceHeader_renderedAtTop() {
        var config = reviewConfig().toBuilder()
                                   .showConvergenceSignal(true).build();
        var renderer = new ConversationRenderer(config);
        var signal = new ConvergenceSignal(ConvergenceState.CONVERGING, 0.78,
                                           "common ground growing, 1 disputed point remaining");
        var ctx = new RenderContext(Map.of(), null, signal, Map.of());
        var result = renderer.render(
                state(Map.of(), List.of(), List.of(), Map.of()), ctx);
        assertThat(result).contains("**Convergence:** CONVERGING (0.78)");
        assertThat(result).contains("common ground growing, 1 disputed point remaining");
    }

    @Test
    void epistemicBadges_notRendered_whenConfigDisabled() {
        var thread   = List.of(entry("REV", "RAISE", "a claim"));
        var points   = Map.of("p1", point("p1", Priority.HIGH, "test", null, "OPEN", thread));
        var config   = reviewConfig(); // showEpistemicStatus defaults to false
        var renderer = new ConversationRenderer(config);
        var cg = new CommonGroundState(
                Map.of("p1", new GroundedFact("p1", "general", EpistemicStatus.ESTABLISHED,
                                              "a claim", java.util.Set.of("agent-a"), java.util.Set.of(), 1)),
                Map.of(), Map.of());
        var ctx    = new RenderContext(Map.of(), cg, null, Map.of());
        var result = renderer.render(state(points, List.of(), List.of(), Map.of()), ctx);
        assertThat(result).doesNotContain("[established");
    }

    // --- Progress rendering tests ---

    static ConversationPoint topicPoint(String id, String topic, String status) {
        return new ConversationPoint(id, topic,
                new PointClassification(Priority.HIGH, "scope", null),
                List.of(entry("agent", "RAISE", "content")), status);
    }

    static ConversationState stateOf(List<ConversationPoint> pts) {
        var map = new LinkedHashMap<String, ConversationPoint>();
        pts.forEach(p -> map.put(p.id(), p));
        return new ConversationState(map, List.of(), List.of(), Map.of());
    }

    static io.casehub.work.progress.ProgressInstance progressInstance(
            String shapeType,
            com.fasterxml.jackson.databind.JsonNode definition,
            com.fasterxml.jackson.databind.JsonNode state) {
        return new io.casehub.work.progress.ProgressInstance(
                java.util.UUID.randomUUID(), "t1", "WORK_ITEM", "s1",
                null, null, shapeType, definition, state,
                io.casehub.work.progress.ProgressStatus.ACTIVE, null,
                java.time.Instant.now(), java.time.Instant.now());
    }

    static com.fasterxml.jackson.databind.ObjectMapper om() {
        return new com.fasterxml.jackson.databind.ObjectMapper();
    }

    @Test
    void progress_inTopicView_rendersBetweenHeaderAndObligationChain() {
        var config = ConversationRendererConfig.builder()
                .groupByTopic(true).showProgress(true).build();
        var renderer = new ConversationRenderer(config);

        var pi = progressInstance("percentage",
                om().createObjectNode().put("label", "Code quality"),
                om().createObjectNode().put("value", 63));
        var ctx = new RenderContext(Map.of(), null, null,
                Map.of("review", List.of(pi)));

        var result = renderer.render(stateOf(List.of(topicPoint("p1", "review", "open"))), ctx);

        assertThat(result).contains("## review\n\nCode quality: 63%\n");
    }

    @Test
    void progress_inFlatView_singleTopic_rendersUnderProgressHeader() {
        var config = ConversationRendererConfig.builder()
                .showProgress(true).build();
        var renderer = new ConversationRenderer(config);

        var pi = progressInstance("count",
                om().createObjectNode().put("label", "Files"),
                om().createObjectNode().put("current", 3).put("total", 10));
        var ctx = new RenderContext(Map.of(), null, null,
                Map.of("review", List.of(pi)));

        var result = renderer.render(stateOf(List.of(topicPoint("p1", "review", "open"))), ctx);

        assertThat(result).contains("**Progress**\n");
        assertThat(result).contains("Files: 3 of 10\n");
        assertThat(result).doesNotContain("_review:_");
    }

    @Test
    void progress_inFlatView_multiTopic_prefixesWithTopicName() {
        var config = ConversationRendererConfig.builder()
                .showProgress(true).build();
        var renderer = new ConversationRenderer(config);

        var pi1 = progressInstance("percentage",
                om().createObjectNode().put("label", "Quality"),
                om().createObjectNode().put("value", 50));
        var pi2 = progressInstance("percentage",
                om().createObjectNode().put("label", "Coverage"),
                om().createObjectNode().put("value", 80));
        var progress = new LinkedHashMap<String, List<io.casehub.work.progress.ProgressInstance>>();
        progress.put("review", List.of(pi1));
        progress.put("analysis", List.of(pi2));
        var ctx = new RenderContext(Map.of(), null, null, progress);

        var result = renderer.render(stateOf(List.of(topicPoint("p1", "general", "open"))), ctx);

        assertThat(result).contains("_review:_");
        assertThat(result).contains("_analysis:_");
    }

    @Test
    void progress_showProgressFalse_noProgressInOutput() {
        var config = ConversationRendererConfig.builder()
                .showProgress(false).build();
        var renderer = new ConversationRenderer(config);

        var pi = progressInstance("percentage",
                om().createObjectNode().put("label", "Quality"),
                om().createObjectNode().put("value", 63));
        var ctx = new RenderContext(Map.of(), null, null,
                Map.of("review", List.of(pi)));

        var result = renderer.render(stateOf(List.of(topicPoint("p1", "review", "open"))), ctx);

        assertThat(result).doesNotContain("Quality");
        assertThat(result).doesNotContain("63%");
    }

    @Test
    void progress_emptyMap_noCrashNoOutput() {
        var config = ConversationRendererConfig.builder()
                .showProgress(true).build();
        var renderer = new ConversationRenderer(config);

        var ctx = new RenderContext(Map.of(), null, null, Map.of());

        var result = renderer.render(stateOf(List.of(topicPoint("p1", "review", "open"))), ctx);

        assertThat(result).doesNotContain("Progress");
    }

    @Test
    void progress_customRenderer_isUsed() {
        var config = ConversationRendererConfig.builder()
                .showProgress(true).groupByTopic(true).build();
        ProgressRenderer custom = pi -> "CUSTOM:" + pi.shapeType();
        var renderer = new ConversationRenderer(config, custom);

        var pi = progressInstance("percentage",
                om().createObjectNode().put("label", "Q"),
                om().createObjectNode().put("value", 50));
        var ctx = new RenderContext(Map.of(), null, null,
                Map.of("review", List.of(pi)));

        var result = renderer.render(stateOf(List.of(topicPoint("p1", "review", "open"))), ctx);

        assertThat(result).contains("CUSTOM:percentage");
    }

    @Test
    void progress_topicWithNoProgress_rendersNormally() {
        var config = ConversationRendererConfig.builder()
                .groupByTopic(true).showProgress(true).build();
        var renderer = new ConversationRenderer(config);

        var ctx = new RenderContext(Map.of(), null, null, Map.of());

        var result = renderer.render(stateOf(List.of(topicPoint("p1", "review", "open"))), ctx);

        assertThat(result).contains("## review\n\n##");
    }

    @Test
    void progress_orderingPreserved() {
        var config = ConversationRendererConfig.builder()
                .groupByTopic(true).showProgress(true).build();
        var renderer = new ConversationRenderer(config);

        var pi1 = progressInstance("percentage",
                om().createObjectNode().put("label", "First"),
                om().createObjectNode().put("value", 10));
        var pi2 = progressInstance("percentage",
                om().createObjectNode().put("label", "Second"),
                om().createObjectNode().put("value", 20));
        var ctx = new RenderContext(Map.of(), null, null,
                Map.of("review", List.of(pi1, pi2)));

        var result = renderer.render(stateOf(List.of(topicPoint("p1", "review", "open"))), ctx);

        int firstIdx = result.indexOf("First: 10%");
        int secondIdx = result.indexOf("Second: 20%");
        assertThat(firstIdx).isGreaterThan(-1);
        assertThat(firstIdx).isLessThan(secondIdx);
    }
}
