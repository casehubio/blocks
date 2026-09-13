# Historical Encounter Showcase — Design Spec

## Goal

Turn the historical-encounter YAML example (#257) into a running showcase
where Leonardo da Vinci and Nikola Tesla converse via LLM, build mental
models, develop narrative threads, and generate autonomous goals —
demonstrating the full blocks + eidos + neurocortex stack.

Two deliverables:
1. **Shared LLM test infrastructure** in blocks (`agentic-yaml` test scope)
   — reusable by all YAML examples
2. **Showcase app** in `casehub/examples/historical-encounter` — Quarkus
   app with UI (follow-on, same pattern as wacky-manor)

This spec covers deliverable 1. The examples/ app is a separate issue
once the test infrastructure proves out.

## Architecture

### Layered composition

```
descriptors.yaml  ──→  eidos-runtime  ──→  AgentDescriptor + SystemPromptRenderer
                                                    │
cognition.yaml    ──→  CognitionCompiler  ──→  CognitionStack (12 orchestrators)
                                                    │
world.yaml        ──→  WorldCompiler      ──→  CompiledWorld + AffordanceRenderer
                                                    │
conversation.yaml ──→  Registries         ──→  TurnPolicy + EpistemicRule + ConvergencePolicy
                                                    │
pattern.yaml      ──→  PatternCompiler    ──→  ExecutionModel (debate + termination + judgment)
                                                    │
                                              ConversationOrchestrator
                                                    │
                                              AgentProvider (LLM)
```

Each YAML file compiles through its existing compiler. The test
infrastructure provides the glue that wires them together and drives
the conversation.

### File layout

```
agentic-yaml/
├── pom.xml                               # -Pllm profile adds test deps
└── src/test/java/.../yaml/
    ├── examples/
    │   ├── ExampleTestBase.java          # existing — compilation tests
    │   ├── HistoricalEncounterExampleTest.java  # existing — compilation tests
    │   └── HistoricalEncounterLlmTest.java      # NEW — LLM-powered tests
    └── llm/                              # NEW — shared LLM test infrastructure
        ├── TestAgentProvider.java         # thin AgentProvider wrapping ChatModel
        ├── CognitionStack.java            # orchestrator builder from CompiledCognition
        ├── ConversationRunner.java        # wires ConversationOrchestrator + CognitionStack
        └── InMemoryMessageStore.java      # lightweight MessageView/MessageDispatcher
```

## Components

### TestAgentProvider

Thin `AgentProvider` implementation for test scope. No CDI, no routing,
no semaphore — just wraps a langchain4j `ChatModel`.

```java
class TestAgentProvider implements AgentProvider {
    private final ChatModel chatModel;

    // invoke() → ChatRequest → chatModel.chat() → TextDelta
    // openSession() → throws UnsupportedOperationException (tests use invoke only)
}
```

Construction: `TestAgentProvider.google(apiKey)` factory using
`GoogleAiGeminiChatModel.builder()`.

### CognitionStack

Builder that takes `CompiledCognition` + `AgentProvider` and constructs
all 12 orchestrators wired together:

| Orchestrator | Dependencies |
|-------------|-------------|
| `PersonalityEvolutionOrchestrator` | — (standalone) |
| `MoodOrchestrator` | — (standalone) |
| `DriveOrchestrator` | MemoryHygieneOrchestrator (optional), NarrativeOrchestrator (optional), MoodOrchestrator, DriveComposer |
| `UserModelOrchestrator` | AgentProvider, UserProfileStore (CbrUserProfileStore @DefaultBean) |
| `MentalModelOrchestrator` | AgentProvider, MentalModelStore (CbrMentalModelStore @DefaultBean) |
| `StrategyLearningOrchestrator` | AgentProvider, StrategyStore (CbrStrategyStore @DefaultBean) |
| `NarrativeOrchestrator` | NarrativeSynthesiser (needs AgentProvider), NarrativeStore (CbrNarrativeStore @DefaultBean) |
| `GoalProposalOrchestrator` | DriveOrchestrator (for drive sources), GoalProposalConfig |
| `GoalEscalationPolicy` | NarrativeOrchestrator, GoalEscalationConfig |
| `SocialNormDetector` | — (reads NormObservation cases) |
| `CollectiveGoalFormation` | DriveOrchestrator (for drive profiles) |
| `MemoryHygieneOrchestrator` | ConfidenceScorer, RetentionConfig |

API:
```java
CognitionStack stack = CognitionStack.from(compiledCognition, agentProvider);
stack.tick(agentId, tenantId, descriptor);  // ticks all orchestrators in correct order
stack.promptSections(agentId, tenantId, subjectId);  // returns PromptSection list
stack.socialPromptAssembler();  // returns SocialPromptAssembler for ConversationOrchestrator
```

The constructor reads config values from `CompiledCognition` fields
(drive weights, mood baseline, personality params, etc.) — each
orchestrator gets its matching config record.

### ConversationRunner

Wires `ConversationOrchestrator` with the `CognitionStack` and drives
the conversation. Encapsulates the plumbing that every YAML example
needs to run a conversation with LLM.

```java
ConversationRunner runner = ConversationRunner.builder()
    .pattern(compiledPattern)
    .cognition(cognitionStack)
    .world(compiledWorld)
    .conversation(turnPolicy, epistemicRule, convergencePolicy)
    .descriptors(List.of(leonardoDescriptor, nikolaDescriptor, historianDescriptor))
    .agentProvider(agentProvider)
    .build();

ConversationOutcome outcome = runner.run();
```

Internally:
1. Creates `AgentParticipant` per descriptor (using SystemPromptRenderer output)
2. Creates a `PromptAssembler` that composes:
   - Base system prompt from `SystemPromptRenderer`
   - Cognition sections from `CognitionStack.promptSections()`
   - World observation from `AffordanceRenderer` + `ObservationPipeline`
3. Builds termination conditions from the pattern spec
4. Constructs `ConversationOrchestrator` with all components
5. Runs the conversation, ticking cognition on each dispatch

Each turn triggers:
- `CognitionStack.tick()` for all 12 subsystems
- World observation rendering (with perception filtering)
- Prompt assembly via `SocialPromptAssembler`
- LLM invocation via `AgentProvider`
- Response folding into conversation state
- Console output of dialogue + state changes

### InMemoryMessageStore

Lightweight in-memory implementations of `MessageView` and
`MessageDispatcher` for the `ConversationOrchestrator`. The orchestrator
expects qhorus message abstractions — these test implementations store
messages in a `CopyOnWriteArrayList` and dispatch via direct method call.

### Observable output

Each turn prints to stdout:
```
=== Turn 3 — leonardo ===
[dialogue] "The water does not move the wheel by force alone — it is the
weight of water falling through height that converts to rotation..."

[drives] CURIOSITY: 0.82 (+0.04)  COMPETENCE: 0.61  AFFILIATION: 0.45 (+0.08)
[mood] pleasure: 0.72 (+0.05)  arousal: 0.58  dominance: 0.52
[mental-model] Leonardo → Tesla: believes(values universal energy, 0.78)
                                 desires(recognition for AC, 0.65)
[narrative] new episode: "Leonardo recognised Tesla's rotating field as
            analogous to his own water vortex studies"
[goals] proposed: explore-electromagnetic-analogy (curiosity-driven, 0.79)
[convergence] PROGRESSING (0.42) — 3 established, 2 pending, 0 disputed
```

This is stdout logging in the test, not a formal API. The `examples/`
app will have structured UI rendering.

## Maven profile

In `agentic-yaml/pom.xml`:

```xml
<profile>
    <id>llm</id>
    <dependencies>
        <dependency>
            <groupId>io.casehub</groupId>
            <artifactId>casehub-eidos-runtime</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.casehub</groupId>
            <artifactId>casehub-platform-agent-api</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-google-ai-gemini</artifactId>
            <scope>test</scope>
        </dependency>
        <!-- neurocortex memory stores for CBR-backed orchestrators -->
        <dependency>
            <groupId>io.casehub</groupId>
            <artifactId>casehub-neocortex-memory-api</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</profile>
```

Run: `mvn test -Pllm` (requires `GOOGLE_API_KEY` env var).

## Test structure

### Existing tests (unchanged)

`HistoricalEncounterExampleTest` — compilation + structure tests:
- `patternCompiles()` — YAML → ExecutionModel
- `fullCognitionCompiles()` — all 12 subsystem configs parse
- `immersiveWorldCompiles()` — entities, sections, affordances
- `worldRendersWithVisibilityGating()` — deep-trust perception filtering
- `conversationSpecCompiles()` — turn policy, epistemic rules, convergence
- `jointIntentionParses()` — shared intention spec

### New LLM tests

`HistoricalEncounterLlmTest`:

```java
@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", disabledReason = "No LLM credentials")
class HistoricalEncounterLlmTest extends ExampleTestBase {

    // Full end-to-end: loads all YAML, wires CognitionStack,
    // runs ConversationRunner for N turns, asserts on outcome
    @Test
    void fullConversationProducesConvergence() { ... }

    // Verify eidos descriptors load and render system prompts
    @Test
    void descriptorsProduceSystemPrompts() { ... }

    // Verify cognition stack ticks produce state changes
    @Test
    void cognitionTicksProduceDriveAndMoodChanges() { ... }

    // Verify world observation with perception filtering
    @Test
    void worldObservationRendersPerAgent() { ... }
}
```

## Dependencies (test scope, under -Pllm profile)

| Dependency | Why |
|-----------|-----|
| `casehub-eidos-runtime` | ClasspathYamlDescriptorRegistrar, VocabularyRegistry, SystemPromptRenderer |
| `casehub-platform-agent-api` | AgentProvider, AgentSessionConfig, AgentEvent |
| `casehub-neocortex-memory-api` | CBR store interfaces for orchestrator stores |
| `langchain4j-google-ai-gemini` | GoogleAiGeminiChatModel for TestAgentProvider |
| `langchain4j-core` | ChatModel, ChatRequest, ChatResponse base types |

## What this does NOT cover

- **UI** — the `examples/historical-encounter` Quarkus app with web frontend is a follow-on issue
- **Multi-vendor** — TestAgentProvider wraps Google Gemini only; the examples/ app will use RoutingAgentProvider with the full model registry
- **Persistent stores** — all CBR stores use in-memory @DefaultBean implementations
- **Joint intentions** — the `joint-intention.yaml` is parsed but not wired into the conversation loop (no `JointIntention` lifecycle integration in ConversationOrchestrator yet)

## References

- `agentic-yaml/src/test/resources/examples/historical-encounter/` — 6 YAML files from #257
- `agentic-yaml/src/test/java/.../examples/HistoricalEncounterExampleTest.java` — existing compilation tests
- `agentic-yaml/src/test/java/.../examples/ExampleTestBase.java` — shared test infrastructure
- `casehub/examples/wacky-manor/` — reference Quarkus app (ProfileAwareDescriptorRegistrar, CharacterAgentLoop, ObservationBuilder)
- `eidos/runtime/src/.../ClasspathYamlDescriptorRegistrar.java` — descriptor loading
- `eidos/api/src/.../SystemPromptRenderer.java` — prompt rendering from descriptors
- `platform/agent-api/src/.../AgentProvider.java` — LLM invocation SPI
- `platform/agent-langchain4j/src/.../ChatModelAgentProvider.java` — langchain4j AgentBackend (CDI reference)
- `platform/llm-config/` — model registry, vendor clients (#286, #287, #291)
- `blocks/.../conversation/orchestration/ConversationOrchestrator.java` — conversation loop
- `blocks/.../agentic/social/prompt/SocialPromptAssembler.java` — cognition prompt composition
