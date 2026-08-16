# Autonomous Agent Patterns — Landscape Analysis and Platform Mapping

**Date:** 2026-08-16
**Context:** Research for QuarkMind restructure — evolving CaseHub's agentic capabilities to support autonomous characters that live in virtual worlds and social platforms (Discord, Minecraft, 3D life sims).

## 1. Motivation

QuarkMind (CaseHub's SC2 game AI living lab) is being restructured into a multi-world autonomous agent platform. The question: what capabilities does an autonomous character need to feel alive, and how many of them does CaseHub already provide?

The answer, after exhaustive audit: almost all of them. The platform has relationship memory, reflection, personality evolution tracking, belief models, epistemic rules, commitment lifecycle, trust scoring, conversation orchestration, affordance systems, and more. What's missing isn't infrastructure — it's named patterns that compose these capabilities into recognisable agent behaviours.

## 2. Named Patterns

Six composition patterns identified. Each wires together existing CaseHub capabilities into a reusable agent behaviour. These belong in casehub-blocks as pattern classes alongside the existing `Patterns.sequence()` / `Patterns.debate()` / `Patterns.supervisor()` family.

### 2.1 Inner Life

**What it does:** A background thought loop that runs between interactions. The agent observes its environment, reflects on what it perceives, builds up things to say, and evaluates whether it has sufficient motivation to act unprompted.

**Why it matters:** Without it, the agent is reactive — responds when spoken to. With it, the agent is a participant — posts when it has something to say, notices things, initiates. CHI 2025 research (Liu et al., "Proactive Conversational Agents with Inner Thoughts") showed 82% user preference for agents with inner thoughts over reactive-only agents.

**Composes:**
- `ReflectionOrchestrator` (neocortex) — generates reflective thoughts from accumulated observations
- `Affordance` / `AffordanceRenderer` (blocks/summarisation) — what actions are available right now
- `ActivationRule` / `ActivationContext` (blocks/agentic) — evaluation of whether to act
- `Watchdog` (qhorus) — civility guard against over-posting, echo chamber detection
- `JobScheduler` (engine) — periodic execution of the thought cycle

**Configuration surface:**
- Motivation threshold (0.0–1.0) — how strongly the agent must want to speak
- Observation window — how much channel activity to consider
- Thought cycle frequency — how often the background loop runs
- Civility constraints — max messages per time window, minimum gap between unprompted messages

**Key academic references:**
- Liu, Fang, Shi, Wu, Igarashi, Chen — "Proactive Conversational Agents with Inner Thoughts" (CHI 2025, arXiv:2501.00383)
- Deng et al. — "Human-Centered Proactive Agents: Intelligence, Adaptivity, Civility" (AAAI 2025)

### 2.2 Memory Hygiene

**What it does:** Manages memory lifecycle — consolidation (merging related memories), forgetting (dropping unimportant ones), importance scoring, and idle-time maintenance. Prevents memory stores from growing unbounded while retaining emotionally significant and practically useful memories.

**Why it matters:** Long-running agents accumulate vast amounts of trivial interaction data. Research shows retaining less than 10% of conversation based on psychological importance scoring (arousal, surprise) significantly improves user experience (LUFY, 2024). MemGPT's "Sleeptime Agents" concept — consolidation during idle periods using a stronger model — is directly applicable.

**Composes:**
- `SummarisationRunner` / `TieredContentSummariser` (blocks/summarisation) — merges related events
- `CbrRetentionPolicy` / `CbrRetentionScheduler` (neocortex) — retention decisions
- `TemporalDecay` / `ScopeDecay` (neocortex) — time-based confidence degradation
- `ReflectionOrchestrator` (neocortex) — generates abstract insights from raw memories
- `EventAccumulator` / `WindowPolicy` (blocks/summarisation) — windowed processing

**Configuration surface:**
- Retention target — what percentage of memories to keep (default: 10%)
- Importance scorer — pluggable scoring function (arousal, surprise, relevance, recency x frequency)
- Consolidation window — how often idle-time maintenance runs
- Merge strategy — how related memories are combined
- Eviction policy — when a memory is permanently removed vs archived

**Requires (foundation addition):**
- Importance scoring functions — arousal scorer (emotional intensity), surprise scorer (information-theoretic unexpectedness). New implementations of existing retention SPI, not a new system.

**Key academic references:**
- Sumida, Inoue, Kawahara — "LUFY: Should RAG Chatbots Forget Unimportant Conversations?" (2024, arXiv:2409.12524)
- Packer, Wooders et al. — "MemGPT / Letta: Sleeptime Agents" (ICLR 2024; Letta 2025)
- ACT-R memory activation model — power-law decay (recency x frequency)
- Kang, Ji, Zhao, Bai — "Memory OS of AI Agent" (EMNLP 2025)

### 2.3 Mood

**What it does:** Maintains a dynamic emotional state (Pleasure-Arousal-Dominance model) that modulates memory retrieval and response generation. A happy agent recalls more positive memories. A frustrated agent's responses carry that tone. Mood decays toward a personality-defined baseline over time.

**Why it matters:** Static personality (Eidos dispositions) defines who the agent IS. Dynamic mood captures how it FEELS right now. The REMT paper (2026) shows mood-modulated retrieval creates natural emotional continuity — the agent doesn't whiplash between emotional states because retrieval bias reinforces the current mood gradually.

**Composes:**
- `PersonalityWeightedRetrieval` (neocortex) — existing static personality bias, extended with mood dimension
- `DispositionEvolution` (eidos) — personality baseline that mood decays toward
- `AgentDisposition` (eidos) — personality traits define baseline mood and mood range

**Configuration surface:**
- PAD axes — pleasure, arousal, dominance (each -1.0 to 1.0)
- Decay rate — how quickly mood returns to personality baseline
- Retrieval bias strength — how much mood affects memory recall weighting
- Appraisal integration — how interaction events shift mood values
- Bounds — maximum mood displacement from baseline (prevents spiralling)

**Requires (foundation addition):**
- `MoodState` type — dynamic PAD values alongside static personality
- Mood-modulated retrieval decorator — extends `PersonalityWeightedRetrieval` to incorporate current mood

**Key academic references:**
- REMT — "Realtime Editable Memory Topology" (Frontiers in AI, 2026)
- DAM-LLM — "Dynamic Affective Memory Management" (2025, arXiv:2510.27418)
- Chain-of-Emotion Architecture (PMC, 2024)

### 2.4 User Model

**What it does:** Synthesises a structured per-user profile from accumulated interaction signals across memory types. Tracks preferences, communication style, relationship stage (stranger → acquaintance → friend → confidant), topics of interest, and behavioural patterns.

**Why it matters:** CBR stores individual cases but doesn't synthesise a holistic view of a person. The persistent memory paper (arXiv:2510.07925) shows dynamically-updated user profiles significantly improve personalisation. Relationship stage tracking enables appropriate behaviour — more formal early, more personal over time.

**Composes:**
- `RelationshipEvent` / `RelationshipQuery` / `QualitySignal` (neocortex) — relationship quality tracking
- `ExperienceRecorder` / `ExperienceQuery` (neocortex) — interaction history
- `TrendAnalyzer` / `TrendProfile` (neocortex CBR) — behavioural trend detection
- `CbrCaseMemoryStore` (neocortex) — case retrieval for this specific user

**Configuration surface:**
- Profile schema — what fields to track (JSON schema, extensible per domain)
- Update frequency — after every interaction or periodically
- Relationship stage thresholds — interaction count/quality thresholds for stage transitions
- Staleness policy — how quickly profile fields decay without fresh signal

**Key academic references:**
- "Enabling Personalized Long-term Interactions through Persistent Memory and User Profiles" (2025, arXiv:2510.07925)
- LD-Agent — modular long-term dialogue agent (NAACL 2025)
- Relationship science — perceived partner responsiveness (Smith, Bradbury, Karney, 2025)

### 2.5 Mental Model (Theory of Mind)

**What it does:** Maintains a BDI (Beliefs, Desires, Intentions) model per actor the agent interacts with. Tracks what others know, want, and plan to do. Feeds into GOAP planning so the agent can reason about other people's states, not just its own goals.

**Why it matters:** Without Theory of Mind, the agent optimises only for its own goals. With it, the agent can reason: "User X seems stressed today → prioritise supportive responses over playful ones" or "User Y mentioned they're working on a deadline → don't suggest time-consuming activities."

**Composes:**
- `BeliefSet` / `ConsistencyChecker` (blocks/agentic) — belief tracking with consistency verification
- `RelationshipEvent` / `RelationshipQuery` (neocortex) — stores per-user mental state observations
- `GoapPlanner` / `GoapWorldState` (engine) — planning that incorporates other actors' states
- `EpistemicRule` / `EpistemicStatus` (blocks/conversation) — knowledge and belief tracking in dialogue

**Configuration surface:**
- BDI dimensions — which aspects of mental state to track
- Inference strategy — how to infer beliefs/desires from conversational cues
- Temporal window — how quickly inferred states decay without fresh signal
- GOAP integration depth — which planning goals consult mental models

**Key academic references:**
- Hwang et al. — "ToMA: Infusing Theory of Mind into Socially Intelligent LLM Agents" (Findings of ACL 2026)
- Jafari et al. — "Beyond Words: ToM-Informed Alignment" (Findings of ACL 2025)
- Hou et al. — "TimeToM: Temporal reasoning in Theory of Mind" (Findings of ACL 2024)
- DPT-Agent — Dual Process Theory with Theory of Mind (2025)

### 2.6 Strategy Learning

**What it does:** Multi-level reflection on whether the agent's interaction strategies are working. Per-response: "was that too aggressive?" Per-conversation: "racing topics engage User X." Per-week: "I monologue too much, should ask more questions." Adjusts approach based on engagement outcomes.

**Why it matters:** An agent that doesn't learn from social outcomes repeats the same mistakes. Research shows multi-level reflection (individual actions → interaction episodes → overall strategy) creates a hierarchy of self-improvement (EMNLP 2025). This is the mechanism by which an agent gets better at being social over time.

**Composes:**
- `ReflectionOrchestrator` / `ReflectionSynthesizer` (neocortex) — generates reflective insights
- `SummarisationRunner` / tiered summarisation (blocks) — multi-level event hierarchy
- `CbrCaseMemoryStore` with outcome weighting (neocortex) — stores interaction cases with engagement scores
- `TrendAnalyzer` (neocortex CBR) — detects patterns in what works vs what doesn't

**Configuration surface:**
- Reflection tiers — which levels of reflection to run (per-response, per-conversation, periodic)
- Engagement signals — what counts as "that worked" (response rate, message length, conversation continuation, sentiment shift)
- Strategy dimensions — what aspects of communication to adjust (formality, humor, verbosity, topic selection, initiative level)
- Learning rate — how quickly strategy shifts in response to outcomes

**Requires (foundation addition):**
- Conversational engagement scoring — standardised outcome definitions for social interactions. New outcome type definitions for experience recording SPI.

**Key academic references:**
- Liu, Van Der Schaar — "Truly Self-Improving Agents Require Intrinsic Metacognitive Learning" (ICML 2025)
- "Self-Learning Agents Enhanced by Multi-level Reflection" (EMNLP 2025)
- Shinn et al. — "Reflexion: Language Agents with Verbal Reinforcement Learning" (NeurIPS 2023)
- MIRROR — "Cognitive Inner Monologue Between Conversational Turns" (2025, arXiv:2506.00430)

## 3. Existing Platform Capabilities Audit

### What already exists (comprehensive)

This section documents every existing CaseHub capability that the patterns above compose. This is the result of an exhaustive audit across all foundation modules.

### 3.1 Memory (casehub-neocortex)

**Core memory types:**
- `CaseMemoryStore` — store/retrieve/search/erase memory by domain, entity, time
- `GraphCaseMemoryStore` — graph-based memory with relationship traversal
- `ExperienceEvent` / `ExperienceRecorder` — Observation → Action → Outcome recording
- `ReflectionEvent` / `ReflectionOrchestrator` / `ReflectionSynthesizer` — autonomous self-reflection
- `RelationshipEvent` / `QualitySignal` — relationship quality tracking between actors
- `PersonalityWeightedRetrieval` — biases memory recall by personality profile
- `PersonalityTransitionSchema` — tracks personality evolution across cases

**CBR (Case-Based Reasoning):**
- `CbrCase` with feature vectors, textual cases, plan cases (with plan traces)
- `CbrSimilarityScorer` — DtwSimilarity, EditDistanceSimilarity, LbKeogh
- `TemporalDecay` / `ScopeDecay` decorators on stores
- `TrustWeightedCbrCaseMemoryStore` — trust-biased case retrieval
- `TrendAnalyzer` / `TrendProfile` — detects behavioural trends over case history
- `CbrRetentionPolicy` / `CbrRetentionScheduler` — lifecycle management
- `ExplanationRenderer` — explains why a case was retrieved
- `PlanAdapter` / `PlanEnsembleAnalyzer` — adapts retrieved plans to current situation
- `OutcomeWeightingFunction` — weights retrieval by outcome quality

**RAG:**
- `QueryExpander` (LLM, StepBack, Template) — query expansion
- `CrossEncoderRelevanceEvaluator` — reranking
- `CorrectiveCaseRetriever` — CRAG pattern (corrective RAG)
- `HybridCaseRetriever` — dense + sparse (BM25) fusion
- `CorrelationGraph` — document-query correlation tracking
- `ColBertRelevanceEvaluator` — late interaction scoring

**Storage backends:** InMemory, JPA, SQLite, Qdrant (vector + sparse), Graphiti (graph), Mem0 (external)

### 3.2 Personality (casehub-eidos)

**Descriptor model:**
- `AgentDescriptor` — full identity: name, slot, disposition, capabilities, goals, constraints, briefing, templates
- `AgentDisposition` — 5 axes: socialOrient, ruleFollowing, riskAppetite, autonomy, conflictMode
- `AgentGoal` — named goals with PUBLIC/PRIVATE visibility
- `AgentConstraint` — named constraints with severity

**Vocabulary frameworks (11):**
- MBTI (16 types), Big Five (OCEAN), DISC, Belbin Team Roles, Enneagram, SDI, SVO, Thomas-Kilmann, Conscientiousness, Jungian Functions (with cognitive stacks), CaseHub slots

**Evolution tracking:**
- `DispositionEvolution` — tracks how disposition changes over time
- `GoalEvolution` / `GoalOutcomeCounts` — goal achievement tracking
- `BehavioralSignal` / `BehavioralExpectations` — behavioural compliance

**Evaluation:**
- 10+ judges: BehavioralJudge, BriefingCoherenceJudge, DispositionPresenceJudge, FunctionActivationJudge, MbtiAlignmentJudge, PairContrastJudge, PersonalityEvolutionJudge, ProximityJudge, TraitExpressionJudge, VocabularyExpressivenessJudge

### 3.3 Conversation & Social (casehub-blocks)

**Agentic patterns:**
- Sequence, Parallel, Loop, Voting, Debate, Supervisor, Conditional, HTN
- `ActivationRule` / `ActivationContext` / `MaxIterationsGuard`
- `AggregationStrategy` — CollectAll, MajorityVote, AuctionAggregation
- Decomposition — Static, LLM, ForwardReasoning, Hybrid, Heuristic, CapabilityDependency

**Conversation orchestration:**
- `ConversationOrchestrator` — multi-party conversation management
- `TurnPolicy` — FreeTurn, RoundRobin, AddressedTurn, PointAddressed
- `CommonGroundAnalyser` / `GroundedFact` — mutual understanding tracking
- `ConvergenceAnalyser` / `ConvergencePolicy` — conversation convergence detection
- `EpistemicRule` / `EpistemicStatus` — knowledge and belief tracking in dialogue

**Social cognition:**
- `BeliefSet` / `ConsistencyChecker` — belief model
- `JointIntention` / `IntentionMonitor` — shared goals between agents
- `CoalitionEvaluator` — alliance formation
- `NormDecision` / `NormResolution` — normative reasoning (priority, specificity, recency)
- `NegotiationProtocol` — multi-party negotiation with acceptance policies

**Summarisation:**
- `SummarisationRunner` / `KeyedSummarisationRunner` — event hierarchy
- `TieredContentSummariser` — multi-level summarisation
- `ObservationAccumulator` / tiered observation — selective attention
- `Affordance` / `ActionDescriptor` — available actions in context

**Prompt optimisation:**
- `PromptOptimiser` — few-shot selection, instruction optimisation
- `VariantSelector` / `VariantOutcome` — A/B testing of prompt variants

### 3.4 Trust (casehub-ledger)

- Bayesian Beta trust scoring
- EigenTrust mesh (distributed trust)
- Per-actor, per-capability, per-dimension scoring
- Trust gates (minimum thresholds)
- Trust federation (cross-tenant)
- Decay functions (exponential)
- Merkle tree integrity verification
- Anomaly detection (sequence gaps, reconciliation mismatches)

### 3.5 Messaging (casehub-qhorus)

- Typed messages (INFORM, REQUEST, PROPOSE, ACCEPT, REJECT, COMMIT, ASSESS, ESCALATE)
- Commitment lifecycle (PROPOSED → ACCEPTED → FULFILLED → VIOLATED → DECLINED → EXPIRED)
- Watchdog — 12 condition types: CHANNEL_IDLE, CONVERSATION_STALL, LOOP_DETECTED, ECHO_CHAMBER, QUEUE_DEPTH, DELIVERY_LAG, AGENT_STALE, CIRCULAR_DELEGATION, OBLIGATION_FAN_OUT, APPROVAL_PENDING, BARRIER_STUCK, CONTEXT_PRESSURE
- Collusion-aware credibility scoring
- Peer review auto-trigger
- Built-in protocols: RequestResponse, RoundRobin, ContributionRequired, TaskCompletion

### 3.6 Connectors (casehub-connectors)

**Discord (full-featured):**
- REST API v10: messages, embeds, reactions, channels, members, guilds, attachments
- Gateway v10: WebSocket with HELLO, IDENTIFY, HEARTBEAT, DISPATCH, RESUME, reconnection
- Presence cache from gateway events
- All 8 ChatPlatform capabilities: Messaging, Threading, Discovery, Reactions, Presence, Members, ChannelManagement, MessageHistory
- InboundConnector/Translator for event-driven ingestion

**Also:** Slack, IRC, Email, Calendar (Google), Webhooks, MCP tool exposure

### 3.7 Planning (casehub-engine)

- GOAP planner (A* over boolean world state)
- HTN planning (blocks/agentic)
- LLM decomposition (blocks)
- Plan adaptation (EveryStepTrigger, OnFailureTrigger, ForwardReplanRevision)
- Goal formation and revision (LLM-driven)
- Goal abandonment evaluation
- Sub-case execution (hierarchical cases)
- Quorum (multi-agent consensus)
- Desired state reconciliation (continuous actual-vs-desired comparison)

### 3.8 Agent Infrastructure (casehub-platform)

- Multiple LLM providers (Claude, OpenAI, Gemini, Codex, LangChain4j)
- Routing agent provider (selects best provider)
- Agent gate (rate limiting via TokenBucket/SlidingWindow, concurrency control)
- A2A protocol support (Google Agent-to-Agent)
- MCP client/server integration
- DID-based identity (Key, Web, SCIM methods)

## 4. Gap Analysis — Features vs Strategies

A critical insight from this analysis: almost everything needed for an autonomous character is a **strategy** (composition of existing capabilities), not a **feature** (new capability to build).

| Need | Feature or Strategy? | Detail |
|---|---|---|
| Proactive behaviour | Strategy | Compose reflection + affordances + activation rules + watchdog + scheduler |
| Memory consolidation | Strategy | Configure summarisation + CBR retention + temporal decay with importance scoring |
| Per-user adaptation | Strategy | Aggregate relationship memory + experience memory + CBR trends |
| Theory of Mind | Strategy | Store BDI in belief model + relationship memory, feed into GOAP |
| Strategy improvement | Strategy | Configure reflection at multiple tiers with engagement signal inputs |
| Communication style | Strategy | CBR cases with engagement outcomes + trend analysis |
| Fast path (System 1/2) | Strategy | Activation rules for routine interactions, fall through to full pipeline |
| Dynamic mood | **Small feature** | MoodState type + mood-modulated retrieval decorator |
| Engagement scoring | **Small feature** | Standardised outcome definitions for social interactions |

Only two genuine platform additions required. Everything else is composition.

## 5. Academic References (by capability area)

### Memory Architecture
- Park et al. — Generative Agents (Stanford, 2023) — memory stream + importance + reflection
- Park et al. — 1,000 People simulation (Stanford, Nov 2024, arXiv:2411.10109)
- Packer, Wooders et al. — MemGPT/Letta (ICLR 2024) — three-tier memory with autonomous management
- Rasmussen et al. — Zep/Graphiti (Jan 2025, arXiv:2501.13956) — temporal knowledge graph
- Xu et al. — A-MEM (NeurIPS 2025, arXiv:2502.12110) — Zettelkasten-style cross-linked memories
- HippoRAG (NeurIPS 2024) + HippoRAG 2 (ICML 2025) — spreading activation retrieval
- MemoRAG (TheWebConf 2025, arXiv:2409.05591) — global memory overview
- Memory OS (EMNLP 2025, arXiv:2506.06326) — three-tier cognitive memory
- Pink et al. — episodic memory position paper (Feb 2025, arXiv:2502.06975)

### Memory Decay and Forgetting
- LUFY (2024, arXiv:2409.12524) — arousal-based forgetting, <10% retention
- ACT-R memory model (HAI 2024) — power-law decay (recency x frequency)
- Four-lever framework — importance, merge, decay, eviction

### Emotional Memory
- DAM-LLM (Oct 2025, arXiv:2510.27418) — affect-modulated memory
- MemEmo benchmark (Feb 2026, arXiv:2602.23944) — emotional memory evaluation
- REMT (Frontiers in AI, 2026) — Mood Index for retrieval bias

### Personality
- Zeng et al. — dynamic personality (ACL Findings 2025) — interaction-driven evolution
- Takata et al. — spontaneous personality emergence (2024)
- BFI-Adapt (2026) — event-induced personality change benchmark
- Li et al. — Cognition-Emotion-Growth (2024) — feedback-loop personality development

### Theory of Mind
- ToMA (Findings of ACL 2026) — ToM with dialogue lookahead
- BDI alignment (Findings of ACL 2025) — beliefs, desires, intentions
- TimeToM (Findings of ACL 2024) — temporal belief tracking
- DPT-Agent (2025) — System 1/2 with Theory of Mind

### Emotional Modeling
- Chain-of-Emotion (PMC, 2024) — pre-response emotion appraisal
- EmpLLM (Springer, 2025) — psychologist simulation for empathy
- Emotional Cognitive Modeling (Oct 2025, arXiv:2510.13195) — desire-driven emotions

### Social Skills
- SocialCoach (2026) — RL-based social skill adaptation
- LD-Agent (NAACL 2025) — modular long-term dialogue
- Persistent Memory + User Profiles (Oct 2025, arXiv:2510.07925)
- Flexible personality (2025) — user-driven dynamic adjustment

### Engagement and Trust
- Inner Thoughts (CHI 2025, arXiv:2501.00383) — proactive with motivation threshold
- Proactive agents survey (ACM TOIS, 2025)
- Retention cliff research — week 3 plateau, memory upgrades lift retention
- Trust simulation (NeurIPS 2024) — LLMs simulate human trust patterns
- Trust and value similarity (Nature, 2025)
- Relationship science applied to AI (Perspectives on Psychological Science, 2025)

### Self-Reflection and Metacognition
- Reflexion (NeurIPS 2023) — verbal reinforcement learning
- MIRROR (2025, arXiv:2506.00430) — inner monologue between turns
- Metacognitive learning (ICML 2025) — knowledge, planning, evaluation
- Multi-level reflection (EMNLP 2025) — per-action, per-episode, per-strategy

### Integrative Frameworks
- CoALA (TMLR 2024) — cognitive architectures for language agents
- SELAgents (Scientific Reports, 2026) — emotion + ToM + social learning
- CBR-LLM integration survey (April 2025, arXiv:2504.06943)

## 6. Application Context

### QuarkMind Platforms

These patterns apply across all QuarkMind worlds:

| Platform | Architecture | Key patterns used |
|---|---|---|
| quarkmind-sc2 | Sequential tick, omniscient | StrategyLearning, Mood |
| quarkmind-town | Async clients, Sims-like | All six patterns |
| quarkmind-minecraft | Async client, embodied | InnerLife, UserModel, MentalModel, Mood |
| quarkmind-discord | Async observer, social | All six patterns (primary showcase) |

### Discord Bot Specifically

The Discord bot is the lowest-friction, highest-impact showcase for these patterns because:
- Bridge is already built (casehub-connectors Discord adapter)
- No game world to build or maintain
- Long-running (weeks/months) — demonstrates memory hygiene and relationship evolution
- Real people with real relationships — genuine test of UserModel and MentalModel
- Proactive initiation is natural (Discord channels are conversational)
- Engagement signals are measurable (message patterns, reactions, thread participation)

### Relationship to Existing Research

The landscape analysis at `wacky-manor/docs/llm-autonomy-landscape-2026.md` covers Smallville, Emergence World, AI Town, Concordia, and includes a capability taxonomy. The structured personality composition paper at `wacky-manor/docs/structured-personality-composition-in-llm-agents.md` covers Eidos architecture. The agent learning memory architecture at `engine/docs/specs/issue-800-agent-learning-memory/` covers memory, reflection, and goal lifecycle. This document extends that work with the composition patterns and academic mapping.
