# Decisions — Avatar Social Cognition (#198–#203 + narrative, goals)

## D1: Avatar identity provisioning

**Choice:** Config-driven — AvatarConfig gains `agentId` + `tenantId`. SpeechWebSocket resolves AgentDescriptor from eidos AgentRegistry at session open.
**Alternatives:**
- Per-session negotiation — client sends identity in Start message. More flexible but pushes agent resolution to the client.
- Fixed singleton — one global avatar identity. Simplest but prevents multi-tenant or multi-avatar deployments.
**Rationale:** Matches how other platform agents get their identity (YAML-driven AgentDescriptorRegistrar → AgentRegistry). Config is explicit, testable, and multi-tenant ready.
**Trade-offs:** Requires eidos dependency in speech-ws (provided scope). Single avatar per deployment unless config becomes tenant-aware.
**Sources:** AvatarConfig.java:8, AgentRegistry (eidos-api), capability-ownership.md §Agent descriptor registration
**Exploration:** quick
**Status:** captured

## D2: Module boundary for social prompt enrichment

**Choice:** Move PromptAssembler + AssembledPrompt + ConversationTurn to speech-api (pure Java). blocks adds speech-api as a compile dependency and provides the social prompt assembly.
**Alternatives:**
- New speech-social bridge module — clean separation but adds module management overhead.
- speech-ws gains blocks dependency — couples a leaf module to foundation.
**Rationale:** speech-api is a zero-framework pure-Java module. PromptAssembler and AssembledPrompt are pure-Java types with no Quarkus dependencies. Moving them to speech-api makes them available to blocks without framework coupling. blocks is the right home for social cognition prompt enrichment since it owns all the orchestrators.
**Trade-offs:** blocks gains a new compile dependency on speech-api. Types move out of speech-ws (consumers that import from speech-ws need to update imports).
**Sources:** speech-ws/pom.xml, speech-api/pom.xml, boundary-rules.md, GE-20260729-172d18 (evolve existing SPIs)
**Exploration:** quick
**Depends on:** D1 (identity must exist for orchestrators to query state)
**Status:** captured

## D3: Composition model for prompt enrichment

**Choice:** PromptSection SPI — a functional interface in speech-api: `@Nullable String contribute(String agentId, String tenantId)`. Each orchestrator gets one PromptSection implementation in blocks. A CompositePromptAssembler in blocks collects all sections and composes the system prompt.
**Alternatives:**
- Single monolithic assembler — simpler initially but becomes a god class as orchestrators accumulate.
- Decorator chain — N layers of decoration for N orchestrators. Awkward ordering semantics.
**Rationale:** Each issue (#198–#203) adds one focused PromptSection class. The composition is explicit, testable in isolation, and extensible. Follows the existing CognitiveObservationSections pattern of rendering social cognition state into text sections.
**Trade-offs:** More classes than a monolithic assembler. CDI discovery adds startup cost (negligible for 6 beans).
**Sources:** CognitiveObservationSections.java, PromptAssembler.java, DefaultPromptAssembler.java
**Exploration:** quick
**Depends on:** D2 (PromptSection lives in speech-api alongside PromptAssembler)
**Status:** captured

## D4: Proactive speech trigger mechanism

**Choice:** InnerLifeOrchestrator integration. SpeechSession periodically ticks InnerLifeOrchestrator. When tick returns Initiated(content, channelHint, score), the session speaks the content proactively.
**Alternatives:**
- Tick-based polling with a separate ProactiveSpeechTrigger SPI — simple and predictable but doesn't reuse existing infrastructure.
- Event-driven via CDI — more responsive but requires wiring CDI events across module boundaries.
**Rationale:** InnerLifeOrchestrator already implements the full proactive initiation pipeline: drive evaluation, civility constraints, content quality gates, LLM motivation scoring, and initiation content generation. It returns ready-to-speak content. Reusing it avoids rebuilding this entire pipeline.
**Trade-offs:** Couples speech to the full inner life pipeline (drives, reflection, civility). Tick frequency must be tuned to avoid excessive LLM calls. InnerLifeOrchestrator requires AgentProvider for LLM motivation scoring.
**Sources:** InnerLifeOrchestrator.java:88-194, InnerLifeTick.java (Initiated/Silent), DriveOrchestrator.java
**Exploration:** quick
**Depends on:** D1 (InnerLifeOrchestrator requires AgentDescriptor)
**Status:** captured

## D5: Full capability coverage — 9 social cognition capabilities

**Choice:** Design covers all 9 avatar-relevant social cognition capabilities, not just the 6 in the issue queue. 8 capabilities contribute PromptSections (personality, mood, drives, mental model, user model, strategy, narrative, goals). 1 capability provides proactive initiation (inner life). File new issues for narrative and goals.
**Alternatives:**
- Cover only the 6 queued issues — leaves narrative identity and autonomous goals out of the avatar. These are among the most human-like capabilities.
- Implement all 9 without issues — loses tracking and traceability.
**Rationale:** The user goal is maximal human-like avatar integration. Narrative identity gives the avatar a coherent self-story. Autonomous goals give it conversational direction. Both are already built — they just need PromptSection wiring.
**Trade-offs:** Scope increases from 6 to 9 capabilities. Two new issues needed. CognitiveObservationSections already has rendering methods for drives, narrative, and goals — reuse reduces implementation cost.
**Sources:** NarrativeOrchestrator.java (currentNarrative), GoalProposalOrchestrator.java (currentProposals), CognitiveObservationSections.java (narrativeSection, motivationalStateSection, goalsSection)
**Exploration:** quick
**Depends on:** D3 (PromptSection SPI is the composition mechanism)
**Status:** captured

## D6: Signal feedback loop — conversation events feed back into orchestrators

**Choice:** SpeechSession records interaction events back into the social cognition orchestrators after each conversation turn. User speech → InteractionSignal (UserModelOrchestrator), MentalStateSignal (MentalModelOrchestrator), EngagementSignal (StrategyLearningOrchestrator), MoodSignal (MoodOrchestrator). The avatar is both a reader and writer of social cognition state.
**Alternatives:**
- Read-only integration — orchestrators provide state but never receive signals from the avatar conversation. Simpler but the avatar never learns or adapts.
- Full bidirectional with personality evolution — record BehavioralSignals for personality drift too. More complete but personality evolution requires domain events, not conversation turns.
**Rationale:** A human-like avatar must learn from interactions. Without feedback, mood never shifts, user models never update, strategies never adapt. The signal SPIs already exist — each orchestrator has a `record()` method. SpeechSession just needs to call them after each turn.
**Trade-offs:** Adds complexity to SpeechSession — each turn dispatches 4 signals. Signal construction requires mapping conversation outcomes to the right signal types. Must handle gracefully when orchestrators are not available (Instance<> optional injection).
**Sources:** UserModelOrchestrator.record(InteractionSignal), MentalModelOrchestrator.record(MentalStateSignal), StrategyLearningOrchestrator.record(EngagementSignal), MoodOrchestrator.record(MoodSignal)
**Exploration:** quick
**Depends on:** D1 (signals require agentId/tenantId), D5 (covers all 9 capabilities)
**Status:** captured
