## D1: Location and module split

**Choice:** Two-part split:
- Shared LLM test infrastructure (AgentProvider adapter, orchestrator wiring from cognition.yaml) → `agentic-yaml` test scope in blocks
- Historical encounter showcase with UI → `casehub/examples/historical-encounter` as a Quarkus app (like wacky-manor)

**Alternatives:**
- New `showcase/` module in blocks — functionally the same as examples/, false distinction
- Everything in `agentic-yaml` test scope — can't have UI, blocks must not have UI dependencies
- Everything in examples/ only — loses the shared test infrastructure that all YAML examples need

**Rationale:** blocks is a pure library with no Quarkus runtime or UI. The LLM test utilities (no UI) belong there. The showcase app (UI, CDI, full orchestration) belongs in examples/ alongside wacky-manor. The split also means the shared test infrastructure benefits all six YAML examples, not just the historical encounter.

**Trade-offs:** Two repos touched — blocks (test infra) and examples (showcase app). Coordination needed at implementation time.

**Sources:** wacky-manor in casehub/examples/ (reference pattern), blocks CLAUDE.md (no Quarkus runtime constraint)

**Exploration:** quick
**Status:** captured

## D2: LLM test gating in blocks

**Choice:** Maven profile (e.g., `-Pllm`) in `agentic-yaml` that activates LLM test dependencies (langchain4j-google-ai-gemini, platform-agent-api). LLM test classes also use `@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY")` as a second guard. A thin test-only class wraps a langchain4j `ChatModel` implementing `AgentProvider` — no CDI, no routing, just invoke → ChatRequest → TextDelta. Default `mvn test` skips LLM tests entirely (no heavy deps on classpath, no env var needed).

**Alternatives:**
- Always include LLM deps in test scope, gate only via `@EnabledIfEnvironmentVariable` — deps always on classpath even when unused, slower resolution
- Skip LLM tests in blocks entirely, only test in examples/ — loses fast feedback on orchestrator wiring without the full app

**Rationale:** Maven profile keeps the default test classpath lean. `@EnabledIfEnvironmentVariable` provides a safety net if the profile is active but credentials are missing. The thin wrapper avoids CDI dependency in blocks test scope.

**Trade-offs:** Two-level gating is slightly more ceremony than a single mechanism. Developers must remember `-Pllm` to run these tests.

**Depends on:** D1 (location split — LLM tests live in blocks agentic-yaml)

**Sources:** wacky-manor AgentInvocationService (reference for AgentProvider usage pattern), platform ChatModelAgentProvider (CDI-managed, not usable without CDI)

**Exploration:** quick
**Status:** captured

## D3: Orchestrator wiring from cognition.yaml

**Choice:** A `CognitionStack` builder/factory class in `agentic-yaml` test scope. Takes a `CompiledCognition` (from `CognitionCompiler.compile()`) and an `AgentProvider`, constructs all 12 orchestrators with dependencies wired. Returns a composition object the test can `tick(agentId, tenantId, descriptor)` and query for state. Reusable across all YAML examples.

**Alternatives:**
- Manual construction per test — no shared abstraction, ~100 lines of boilerplate per test, fragile to constructor changes

**Rationale:** Single wiring point. Constructor changes to orchestrators only break one place. Also useful as a reference for how consumers compose the cognition stack — could be promoted to main scope if the pattern proves out.

**Trade-offs:** The builder must track orchestrator constructor evolution — any new parameter added to an orchestrator constructor breaks the builder until updated.

**Depends on:** D2 (LLM test gating — CognitionStack is part of the test infrastructure)

**Sources:** wacky-manor agent/ package (manual wiring reference), blocks social cognition orchestrators (constructor signatures)

**Exploration:** quick
**Status:** captured

## D4: Eidos descriptor loading

**Choice:** Add `casehub-eidos-runtime` as test-scope dependency (under `-Pllm` profile). Use `ClasspathYamlDescriptorRegistrar.loadFrom(stream, vocabRegistry)` with a real `VocabularyRegistry` from eidos-runtime. Use `SystemPromptRenderer` to produce system prompts from descriptors. Same code path as production.

**Alternatives:**
- Parse descriptors manually, skip eidos-runtime — loses MBTI resolution, vocabulary validation, SystemPromptRenderer. Defeats the purpose of demonstrating the eidos stack.

**Rationale:** The showcase must demonstrate eidos personality wiring end-to-end. MBTI type resolution (INFP → Jungian 8-function weighted profile) is a core eidos feature. Using the real code path proves it works.

**Trade-offs:** eidos-runtime brings transitive dependencies into test scope. Acceptable under the `-Pllm` profile since these tests already need the full stack.

**Depends on:** D2 (Maven profile gating)

**Sources:** wacky-manor ProfileAwareDescriptorRegistrar, eidos ClasspathYamlDescriptorRegistrar.loadFrom(), eidos SystemPromptRenderer

**Exploration:** quick
**Status:** captured

## D5: Conversation loop structure

**Choice:** Use `ConversationOrchestrator` from `blocks.conversation.orchestration` directly. Wire with `RoundRobinTurnPolicy`, epistemic rules and convergence policy from `conversation.yaml` (resolved via registries), termination conditions from `pattern.yaml`. Each turn: tick all cognition orchestrators → assemble prompt via `SocialPromptAssembler` → invoke AgentProvider → fold response into conversation state. Same production path.

**Alternatives:**
- Custom while-loop (like wacky-manor's `CharacterAgentLoop`) — simpler but duplicates ConversationOrchestrator and doesn't exercise the blocks conversation infrastructure

**Rationale:** The showcase must prove that ConversationOrchestrator + SocialPromptAssembler + cognition ticks compose correctly. A custom loop would bypass the very infrastructure being demonstrated.

**Trade-offs:** ConversationOrchestrator expects `MessageView` and `MessageDispatcher` abstractions — the test needs lightweight in-memory implementations of these qhorus interfaces.

**Depends on:** D3 (CognitionStack provides orchestrator composition), D4 (eidos descriptors for SocialPromptAssembler)

**Sources:** blocks ConversationOrchestrator, blocks SocialPromptAssembler, wacky-manor CharacterAgentLoop (anti-reference — what NOT to duplicate)

**Exploration:** quick
**Status:** captured
