# SummaryUpdateHook Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #64 — SummaryUpdateHook implementation
**Issue group:** #64

**Goal:** Connect blocks' summarisation intelligence to qhorus's channel
summary slot via `SummaryUpdateHook` SPI — heuristic default + LLM
alternative.

**Architecture:** Two-repo change. Qhorus SPI enrichment adds messages to
`SummaryUpdateContext`. Blocks provides two `SummaryUpdateHook`
implementations: `HeuristicChannelSummariser` (`@DefaultBean`, append-only,
deterministic) and `LlmChannelSummariser` (`@Alternative @Priority(1)`,
edit-mode, LLM-powered via `AgentProvider`).

**Tech Stack:** Java 21, JUnit 5, Mockito, qhorus-api (`Message`,
`MessageQuery`, `SummaryUpdateHook`), platform-agent-api (`AgentProvider`,
`AgentSessionConfig`, `AgentEvent`)

## Global Constraints

- blocks has no CDI container in tests — plain JUnit 5 + Mockito only
- `AgentProvider` is a `provided` dependency — `LlmChannelSummariser`
  activates via `@Alternative @Priority(1)` when on classpath
- qhorus-api change must be backward-compatible — `NoOpSummaryUpdateHook`
  must continue to compile and work unchanged
- All qhorus changes committed and `mvn install`ed before blocks work begins

---

### Task 1: Enrich SummaryUpdateContext (qhorus)

**Repo:** `/Users/mdproctor/claude/casehub/qhorus`

**Files:**
- Modify: `api/src/main/java/io/casehub/qhorus/api/spi/SummaryUpdateContext.java`
- Modify: `runtime/src/main/java/io/casehub/qhorus/runtime/channel/ChannelSummaryScheduler.java`
- Modify: `runtime/src/main/java/io/casehub/qhorus/runtime/channel/ChannelSummaryService.java`
- Test: `runtime/src/test/java/io/casehub/qhorus/runtime/channel/ChannelSummarySchedulerTest.java` (if exists, else create)

**Interfaces:**
- Produces: `SummaryUpdateContext` with `recentMessages()` returning `List<Message>` and `messageQuery()` returning `Function<MessageQuery, List<Message>>`

- [ ] **Step 1: Add fields to SummaryUpdateContext**

Use `ide_edit_member` to replace the record declaration:

```java
public record SummaryUpdateContext(
        UUID channelId,
        String channelName,
        String tenancyId,
        String currentSummary,
        Long lastUpdatedMessageId,
        long messagesSinceLastUpdate,
        List<Message> recentMessages,
        Function<MessageQuery, List<Message>> messageQuery) {
}
```

Add imports: `io.casehub.qhorus.api.message.Message`,
`io.casehub.qhorus.api.store.query.MessageQuery`,
`java.util.List`, `java.util.function.Function`.

- [ ] **Step 2: Verify NoOpSummaryUpdateHook still compiles**

Run: `mvn --batch-mode compile -pl api,runtime`

The `NoOpSummaryUpdateHook` calls `context.currentSummary()` — record
accessor is unchanged. Must compile without modification.

- [ ] **Step 3: Update ChannelSummaryScheduler.updateSummary()**

Use `ide_replace_member` on `updateSummary` in `ChannelSummaryScheduler`:

```java
void updateSummary(ChannelSummary s) {
    Channel ch = crossTenantChannelStore.listAll().stream()
            .filter(c -> c.id().equals(s.channelId()))
            .findFirst()
            .orElse(null);
    if (ch == null) {
        LOG.warnf("Channel not found for summary update: %s", s.channelId());
        return;
    }

    long messagesSince = countMessagesSince(s.channelId(), s.lastUpdatedMessageId());
    List<Message> recent = fetchMessagesSince(s.channelId(), s.lastUpdatedMessageId());

    String updated = hook.update(new SummaryUpdateContext(
            s.channelId(), ch.name(), ch.tenancyId(),
            s.content(), s.lastUpdatedMessageId(), messagesSince,
            recent,
            q -> crossTenantMessageStore.scan(
                    q.toBuilder().channelId(s.channelId()).build())));

    Long maxMessageId = currentMaxMessageId(s.channelId());

    summaryStore.save(s.toBuilder()
            .content(updated)
            .updatedAt(Instant.now())
            .updatedBy("system:summary-scheduler")
            .lastUpdatedMessageId(maxMessageId)
            .build());

    summaryEvents.fireAsync(new ChannelSummaryUpdatedEvent(
            s.channelId(), ch.name(), "system:summary-scheduler"));
}
```

Add `fetchMessagesSince` helper method using `ide_insert_member`:

```java
private List<Message> fetchMessagesSince(UUID channelId, Long afterId) {
    MessageQuery.Builder qb = MessageQuery.builder().channelId(channelId);
    if (afterId != null) {
        qb.afterId(afterId);
    }
    return crossTenantMessageStore.scan(qb.build());
}
```

Add import: `io.casehub.qhorus.api.message.Message`.

- [ ] **Step 4: Update ChannelSummaryService.triggerUpdate()**

Use `ide_replace_member` on `triggerUpdate` in `ChannelSummaryService`:

```java
public Optional<ChannelSummary> triggerUpdate(UUID channelId) {
    ChannelSummary existing = summaryStore.findByChannelId(channelId).orElse(null);
    if (existing == null) {
        return Optional.empty();
    }

    Channel ch = channelService.findById(channelId)
            .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channelId));

    long messagesSince = countMessagesSince(channelId, existing.lastUpdatedMessageId());
    List<Message> recent = fetchMessagesSince(channelId, existing.lastUpdatedMessageId());

    String updated = hook.update(new SummaryUpdateContext(
            channelId, ch.name(), ch.tenancyId(),
            existing.content(), existing.lastUpdatedMessageId(), messagesSince,
            recent,
            q -> messageStore.scan(q.toBuilder().channelId(channelId).build())));

    Long maxMessageId = currentMaxMessageId(channelId);

    ChannelSummary saved = summaryStore.save(existing.toBuilder()
            .content(updated)
            .updatedAt(Instant.now())
            .updatedBy("system:summary-scheduler")
            .lastUpdatedMessageId(maxMessageId)
            .build());

    summaryEvents.fireAsync(new ChannelSummaryUpdatedEvent(
            channelId, ch.name(), "system:summary-scheduler"));
    return Optional.of(saved);
}
```

Add `fetchMessagesSince` helper using `ide_insert_member`:

```java
private List<Message> fetchMessagesSince(UUID channelId, Long afterId) {
    MessageQuery.Builder qb = MessageQuery.builder().channelId(channelId);
    if (afterId != null) {
        qb.afterId(afterId);
    }
    return messageStore.scan(qb.build());
}
```

Add import: `io.casehub.qhorus.api.message.Message`.

- [ ] **Step 5: Check MessageQuery has toBuilder()**

Use `ide_find_class` to locate `MessageQuery`, read it. If `toBuilder()`
does not exist, the `messageQuery` function must build a fresh query
instead:

```java
q -> messageStore.scan(MessageQuery.builder()
        .channelId(channelId)
        .afterId(q.afterId())
        .limit(q.limit())
        .descending(q.descending())
        .build())
```

Adapt Step 3 and Step 4 accordingly.

- [ ] **Step 6: Build and verify**

Run: `mvn --batch-mode test -pl api,runtime`
Expected: all existing tests pass, no compilation errors.

- [ ] **Step 7: Install to local Maven repo**

Run: `mvn --batch-mode install -DskipTests`

This makes the enriched `SummaryUpdateContext` visible to blocks.

- [ ] **Step 8: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/qhorus add \
  api/src/main/java/io/casehub/qhorus/api/spi/SummaryUpdateContext.java \
  runtime/src/main/java/io/casehub/qhorus/runtime/channel/ChannelSummaryScheduler.java \
  runtime/src/main/java/io/casehub/qhorus/runtime/channel/ChannelSummaryService.java
git -C /Users/mdproctor/claude/casehub/qhorus commit -m "feat(#355): enrich SummaryUpdateContext with recentMessages and messageQuery

Pre-fetch messages since lastUpdatedMessageId into the context so hook
implementations can summarise without injecting MessageStore. Channel-
scoped query function provides escape hatch for custom access patterns.

Backward-compatible — NoOpSummaryUpdateHook unchanged.

Refs casehubio/blocks#64"
```

---

### Task 2: HeuristicChannelSummariser (blocks)

**Repo:** `/Users/mdproctor/claude/casehub/blocks`

**Files:**
- Create: `src/main/java/io/casehub/blocks/channel/summary/HeuristicChannelSummariser.java`
- Create: `src/main/java/io/casehub/blocks/channel/summary/SummaryMode.java`
- Test: `src/test/java/io/casehub/blocks/channel/summary/HeuristicChannelSummariserTest.java`

**Interfaces:**
- Consumes: `SummaryUpdateHook` from qhorus-api, `SummaryUpdateContext` with `recentMessages()`
- Produces: `HeuristicChannelSummariser` implementing `SummaryUpdateHook`, `SummaryMode` enum

- [ ] **Step 1: Create SummaryMode enum**

Use `ide_create_file`:

```java
package io.casehub.blocks.channel.summary;

public enum SummaryMode {
    APPEND,
    EDIT
}
```

- [ ] **Step 2: Write failing test — empty messages returns current summary**

Use `ide_create_file`:

```java
package io.casehub.blocks.channel.summary;

import io.casehub.qhorus.api.spi.SummaryUpdateContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicChannelSummariserTest {

    private final HeuristicChannelSummariser summariser = new HeuristicChannelSummariser();

    @Test
    void emptyMessages_returnsCurrentSummary() {
        var ctx = new SummaryUpdateContext(
                UUID.randomUUID(), "test-channel", "tenant-1",
                "existing summary", 10L, 0,
                List.of(), q -> List.of());

        assertThat(summariser.update(ctx)).isEqualTo("existing summary");
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn --batch-mode test -pl . -Dtest=HeuristicChannelSummariserTest#emptyMessages_returnsCurrentSummary`
Expected: FAIL — class does not exist.

- [ ] **Step 4: Implement HeuristicChannelSummariser — minimal**

Use `ide_create_file`:

```java
package io.casehub.blocks.channel.summary;

import io.casehub.qhorus.api.spi.SummaryUpdateContext;
import io.casehub.qhorus.api.spi.SummaryUpdateHook;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@DefaultBean
@ApplicationScoped
public class HeuristicChannelSummariser implements SummaryUpdateHook {

    @Override
    public String update(SummaryUpdateContext context) {
        if (context.recentMessages() == null || context.recentMessages().isEmpty()) {
            return context.currentSummary();
        }
        return appendDelta(context);
    }

    private String appendDelta(SummaryUpdateContext context) {
        var messages = context.recentMessages();
        var sb = new StringBuilder();

        if (context.currentSummary() != null && !context.currentSummary().isBlank()) {
            sb.append(context.currentSummary()).append("\n\n");
        }

        sb.append("--- Update (").append(messages.size()).append(" messages) ---\n");

        var participants = messages.stream()
                .map(m -> m.sender())
                .filter(s -> s != null)
                .distinct()
                .toList();
        if (!participants.isEmpty()) {
            sb.append("Participants: ").append(String.join(", ", participants)).append("\n");
        }

        var first = messages.getFirst().createdAt();
        var last = messages.getLast().createdAt();
        if (first != null && last != null) {
            sb.append("Period: ").append(first).append(" — ").append(last).append("\n");
        }

        var topics = messages.stream()
                .map(m -> m.topic())
                .filter(t -> t != null && !t.isBlank())
                .distinct()
                .toList();
        if (!topics.isEmpty()) {
            sb.append("Topics: ").append(String.join(", ", topics)).append("\n");
        }

        return sb.toString().stripTrailing();
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn --batch-mode test -pl . -Dtest=HeuristicChannelSummariserTest#emptyMessages_returnsCurrentSummary`
Expected: PASS

- [ ] **Step 6: Write test — null currentSummary produces fresh summary**

Add to test class using `ide_insert_member`:

```java
@Test
void nullCurrentSummary_producesFreshSummary() {
    var msg = TestMessages.message("alice", "Hello everyone");
    var ctx = new SummaryUpdateContext(
            UUID.randomUUID(), "test-channel", "tenant-1",
            null, null, 1,
            List.of(msg), q -> List.of());

    var result = summariser.update(ctx);
    assertThat(result).contains("1 messages");
    assertThat(result).contains("alice");
    assertThat(result).doesNotContain("null");
}
```

Where `TestMessages` is the existing test helper. Check if
`io.casehub.blocks.channel.TestMessages` exists and has a `message()`
factory. If not, create a local helper method:

```java
private static Message message(String sender, String content) {
    return Message.builder()
            .id(1L)
            .channelId(UUID.randomUUID())
            .sender(sender)
            .content(content)
            .messageType(MessageType.STANDARD)
            .createdAt(Instant.now())
            .build();
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `mvn --batch-mode test -pl . -Dtest=HeuristicChannelSummariserTest#nullCurrentSummary_producesFreshSummary`
Expected: PASS

- [ ] **Step 8: Write test — participants extracted**

```java
@Test
void multipleParticipants_listedInSummary() {
    var msgs = List.of(
            message("alice", "First point"),
            message("bob", "Counterpoint"),
            message("alice", "Response"));
    var ctx = new SummaryUpdateContext(
            UUID.randomUUID(), "debate", "tenant-1",
            "Prior summary.", null, 3,
            msgs, q -> List.of());

    var result = summariser.update(ctx);
    assertThat(result).contains("alice", "bob");
    assertThat(result).contains("Prior summary.");
    assertThat(result).contains("3 messages");
}
```

- [ ] **Step 9: Run test — should pass**

Run: `mvn --batch-mode test -pl . -Dtest=HeuristicChannelSummariserTest#multipleParticipants_listedInSummary`
Expected: PASS

- [ ] **Step 10: Write test — topics extracted**

```java
@Test
void messagesWithTopics_topicsListed() {
    var msg1 = Message.builder().id(1L).channelId(UUID.randomUUID())
            .sender("alice").content("text").messageType(MessageType.STANDARD)
            .topic("architecture").createdAt(Instant.now()).build();
    var msg2 = Message.builder().id(2L).channelId(UUID.randomUUID())
            .sender("bob").content("text").messageType(MessageType.STANDARD)
            .topic("testing").createdAt(Instant.now()).build();
    var ctx = new SummaryUpdateContext(
            UUID.randomUUID(), "dev", "tenant-1",
            null, null, 2,
            List.of(msg1, msg2), q -> List.of());

    var result = summariser.update(ctx);
    assertThat(result).contains("architecture", "testing");
}
```

- [ ] **Step 11: Run test — should pass**

Run: `mvn --batch-mode test -pl . -Dtest=HeuristicChannelSummariserTest#messagesWithTopics_topicsListed`
Expected: PASS

- [ ] **Step 12: Run all tests**

Run: `mvn --batch-mode test`
Expected: all pass

- [ ] **Step 13: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add \
  src/main/java/io/casehub/blocks/channel/summary/SummaryMode.java \
  src/main/java/io/casehub/blocks/channel/summary/HeuristicChannelSummariser.java \
  src/test/java/io/casehub/blocks/channel/summary/HeuristicChannelSummariserTest.java
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#64): HeuristicChannelSummariser — @DefaultBean summary hook

Append-only structural summariser: participant names, message count,
time span, topics. Zero LLM cost, deterministic, always available.

Refs casehubio/blocks#64"
```

---

### Task 3: LlmChannelSummariser (blocks)

**Repo:** `/Users/mdproctor/claude/casehub/blocks`

**Files:**
- Create: `src/main/java/io/casehub/blocks/channel/summary/LlmChannelSummariser.java`
- Test: `src/test/java/io/casehub/blocks/channel/summary/LlmChannelSummariserTest.java`

**Interfaces:**
- Consumes: `SummaryUpdateHook` from qhorus-api, `AgentProvider` + `AgentSessionConfig` + `AgentEvent` from platform-agent-api, `SummaryMode` from Task 2
- Produces: `LlmChannelSummariser` implementing `SummaryUpdateHook`

- [ ] **Step 1: Write failing test — empty messages returns current summary without LLM call**

Use `ide_create_file`:

```java
package io.casehub.blocks.channel.summary;

import io.casehub.platform.agent.AgentProvider;
import io.casehub.qhorus.api.spi.SummaryUpdateContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LlmChannelSummariserTest {

    private final AgentProvider agentProvider = mock(AgentProvider.class);
    private final LlmChannelSummariser summariser =
            new LlmChannelSummariser(agentProvider, SummaryMode.EDIT);

    @Test
    void emptyMessages_returnsCurrentSummaryWithoutLlmCall() {
        var ctx = new SummaryUpdateContext(
                UUID.randomUUID(), "test-channel", "tenant-1",
                "existing summary", 10L, 0,
                List.of(), q -> List.of());

        assertThat(summariser.update(ctx)).isEqualTo("existing summary");
        verifyNoInteractions(agentProvider);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl . -Dtest=LlmChannelSummariserTest#emptyMessages_returnsCurrentSummaryWithoutLlmCall`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Implement LlmChannelSummariser**

Use `ide_create_file`:

```java
package io.casehub.blocks.channel.summary;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.spi.SummaryUpdateContext;
import io.casehub.qhorus.api.spi.SummaryUpdateHook;
import jakarta.alternative.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.stream.Collectors;

@Alternative
@Priority(1)
@ApplicationScoped
public class LlmChannelSummariser implements SummaryUpdateHook {

    private static final System.Logger LOG = System.getLogger(LlmChannelSummariser.class.getName());

    private static final String SYSTEM_PROMPT_EDIT = """
            You are a channel summariser. Given the current summary of a conversation \
            channel and a batch of new messages, produce an updated summary that \
            integrates the new information. You may rewrite any part of the existing \
            summary that the new messages change — discussions that are now resolved, \
            plans that are now confirmed, concerns that are now addressed. \
            Be concise. Use plain text.""";

    private static final String SYSTEM_PROMPT_APPEND = """
            You are a channel summariser. Given the current summary of a conversation \
            channel and a batch of new messages, append a brief update section \
            summarising the new messages. Do not modify the existing summary. \
            Be concise. Use plain text.""";

    private final AgentProvider agentProvider;
    private final SummaryMode mode;

    @Inject
    public LlmChannelSummariser(AgentProvider agentProvider,
                                 @ConfigProperty(name = "casehub.blocks.channel.summary.mode",
                                                  defaultValue = "EDIT")
                                 SummaryMode mode) {
        this.agentProvider = agentProvider;
        this.mode = mode;
    }

    public LlmChannelSummariser(AgentProvider agentProvider, SummaryMode mode) {
        this.agentProvider = agentProvider;
        this.mode = mode;
    }

    @Override
    public String update(SummaryUpdateContext context) {
        if (context.recentMessages() == null || context.recentMessages().isEmpty()) {
            return context.currentSummary();
        }

        try {
            var userPrompt = buildUserPrompt(context);
            var systemPrompt = mode == SummaryMode.EDIT ? SYSTEM_PROMPT_EDIT : SYSTEM_PROMPT_APPEND;
            var config = AgentSessionConfig.of(systemPrompt, userPrompt);

            return agentProvider.invoke(config)
                    .filter(e -> e instanceof AgentEvent.TextDelta)
                    .map(e -> ((AgentEvent.TextDelta) e).text())
                    .collect().with(Collectors.joining())
                    .await().indefinitely();
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING,
                    "LLM summarisation failed for channel " + context.channelName(), e);
            throw e;
        }
    }

    private String buildUserPrompt(SummaryUpdateContext context) {
        var sb = new StringBuilder();
        sb.append("Channel: ").append(context.channelName()).append("\n\n");

        if (context.currentSummary() != null && !context.currentSummary().isBlank()) {
            sb.append("Current summary:\n").append(context.currentSummary()).append("\n\n");
        }

        sb.append("New messages (").append(context.recentMessages().size()).append("):\n");
        for (Message msg : context.recentMessages()) {
            sb.append("[").append(msg.sender()).append("] ").append(msg.content()).append("\n");
        }
        return sb.toString();
    }
}
```

Note: check that `jakarta.alternative.Priority` is the correct import.
It may be `jakarta.annotation.Priority` depending on the CDI version.
Use `ide_find_class` to locate `Priority` in the project dependencies and
use the correct import.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn --batch-mode test -pl . -Dtest=LlmChannelSummariserTest#emptyMessages_returnsCurrentSummaryWithoutLlmCall`
Expected: PASS

- [ ] **Step 5: Write test — EDIT mode prompt construction**

Add to test class using `ide_insert_member`:

```java
@Test
void editMode_promptContainsCurrentSummaryAndMessages() {
    var msg = message("alice", "We decided on Redis.");
    var ctx = new SummaryUpdateContext(
            UUID.randomUUID(), "dev-channel", "tenant-1",
            "Discussing caching options.", null, 1,
            List.of(msg), q -> List.of());

    when(agentProvider.invoke(any()))
            .thenReturn(Multi.createFrom().item(
                    new AgentEvent.TextDelta("Updated summary.")));

    var result = summariser.update(ctx);
    assertThat(result).isEqualTo("Updated summary.");

    var configCaptor = ArgumentCaptor.forClass(AgentSessionConfig.class);
    verify(agentProvider).invoke(configCaptor.capture());

    var config = configCaptor.getValue();
    assertThat(config.systemPrompt()).contains("rewrite");
    assertThat(config.userPrompt()).contains("Discussing caching options.");
    assertThat(config.userPrompt()).contains("[alice] We decided on Redis.");
}
```

Add imports: `org.mockito.ArgumentCaptor`, `io.smallrye.mutiny.Multi`,
`io.casehub.platform.agent.AgentEvent`, `io.casehub.platform.agent.AgentSessionConfig`.

Add `message` helper:

```java
private static Message message(String sender, String content) {
    return Message.builder()
            .id(1L).channelId(UUID.randomUUID())
            .sender(sender).content(content)
            .messageType(MessageType.STANDARD)
            .createdAt(Instant.now()).build();
}
```

- [ ] **Step 6: Run test — should pass**

Run: `mvn --batch-mode test -pl . -Dtest=LlmChannelSummariserTest#editMode_promptContainsCurrentSummaryAndMessages`
Expected: PASS

- [ ] **Step 7: Write test — APPEND mode uses different system prompt**

```java
@Test
void appendMode_promptDoesNotInstructRewrite() {
    var appendSummariser = new LlmChannelSummariser(agentProvider, SummaryMode.APPEND);
    var msg = message("bob", "Agreed.");
    var ctx = new SummaryUpdateContext(
            UUID.randomUUID(), "dev", "tenant-1",
            "Prior summary.", null, 1,
            List.of(msg), q -> List.of());

    when(agentProvider.invoke(any()))
            .thenReturn(Multi.createFrom().item(
                    new AgentEvent.TextDelta("Appended.")));

    appendSummariser.update(ctx);

    var configCaptor = ArgumentCaptor.forClass(AgentSessionConfig.class);
    verify(agentProvider).invoke(configCaptor.capture());
    assertThat(configCaptor.getValue().systemPrompt()).contains("Do not modify");
    assertThat(configCaptor.getValue().systemPrompt()).doesNotContain("rewrite");
}
```

- [ ] **Step 8: Run test — should pass**

Run: `mvn --batch-mode test -pl . -Dtest=LlmChannelSummariserTest#appendMode_promptDoesNotInstructRewrite`
Expected: PASS

- [ ] **Step 9: Write test — null currentSummary omits summary section**

```java
@Test
void nullCurrentSummary_promptOmitsSummarySection() {
    var msg = message("alice", "Hello");
    var ctx = new SummaryUpdateContext(
            UUID.randomUUID(), "new-channel", "tenant-1",
            null, null, 1,
            List.of(msg), q -> List.of());

    when(agentProvider.invoke(any()))
            .thenReturn(Multi.createFrom().item(
                    new AgentEvent.TextDelta("Fresh summary.")));

    summariser.update(ctx);

    var configCaptor = ArgumentCaptor.forClass(AgentSessionConfig.class);
    verify(agentProvider).invoke(configCaptor.capture());
    assertThat(configCaptor.getValue().userPrompt()).doesNotContain("Current summary:");
}
```

- [ ] **Step 10: Run test — should pass**

Run: `mvn --batch-mode test -pl . -Dtest=LlmChannelSummariserTest#nullCurrentSummary_promptOmitsSummarySection`
Expected: PASS

- [ ] **Step 11: Write test — AgentProvider failure propagates**

```java
@Test
void agentProviderFailure_propagatesException() {
    var msg = message("alice", "Hello");
    var ctx = new SummaryUpdateContext(
            UUID.randomUUID(), "channel", "tenant-1",
            null, null, 1,
            List.of(msg), q -> List.of());

    when(agentProvider.invoke(any()))
            .thenReturn(Multi.createFrom().failure(
                    new RuntimeException("LLM unavailable")));

    assertThatThrownBy(() -> summariser.update(ctx))
            .hasMessageContaining("LLM unavailable");
}
```

Add import: `static org.assertj.core.api.Assertions.assertThatThrownBy`.

- [ ] **Step 12: Run test — should pass**

Run: `mvn --batch-mode test -pl . -Dtest=LlmChannelSummariserTest#agentProviderFailure_propagatesException`
Expected: PASS

- [ ] **Step 13: Run all tests**

Run: `mvn --batch-mode test`
Expected: all pass

- [ ] **Step 14: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add \
  src/main/java/io/casehub/blocks/channel/summary/LlmChannelSummariser.java \
  src/test/java/io/casehub/blocks/channel/summary/LlmChannelSummariserTest.java
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#64): LlmChannelSummariser — @Alternative LLM-powered summary hook

Edit-mode by default — rewrites surrounding context when new messages
change the picture. APPEND mode via config. Delegates to AgentProvider.
Activates when AgentProvider is on the classpath.

Refs casehubio/blocks#64"
```

---

### Task 4: CLAUDE.md update (blocks)

**Files:**
- Modify: `/Users/mdproctor/claude/casehub/blocks/CLAUDE.md`

**Interfaces:**
- None — documentation only

- [ ] **Step 1: Update CLAUDE.md package table**

Add the new package to the `## Package: io.casehub.blocks.channel` section
or create a new section:

```markdown
## Package: `io.casehub.blocks.channel.summary`

| Class | What it does |
|-------|-------------|
| `SummaryMode` | Enum: `APPEND` (delta only) or `EDIT` (rewrite entire summary) |
| `HeuristicChannelSummariser` | `@DefaultBean` `SummaryUpdateHook` — append-only structural summary from message metadata (participants, count, topics, time span). Zero LLM cost. |
| `LlmChannelSummariser` | `@Alternative @Priority(1)` `SummaryUpdateHook` — LLM-powered via `AgentProvider`. Edit mode by default (rewrites surrounding context). Configurable via `casehub.blocks.channel.summary.mode`. |
```

- [ ] **Step 2: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add CLAUDE.md
git -C /Users/mdproctor/claude/casehub/blocks commit -m "docs(#64): add channel.summary package to CLAUDE.md

Refs casehubio/blocks#64"
```
