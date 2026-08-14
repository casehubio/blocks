# Pattern Catalog — CaseHub Agentic-AI Patterns

> Complete catalog of explicit patterns, compositional patterns, and coverage gaps
> across the blocks platform (blocks + engine + qhorus + work).

Last updated: 2026-08-14

---

## Primitives Inventory

The patterns in this catalog emerge from combining these independently composable primitives.

### Five Agentic SPIs (casehub-blocks)

| SPI | Implementations |
|-----|----------------|
| **Routing** | FirstMatch, RoundRobin, Sequential, LlmSelected, StageAwareCandidateSupplier |
| **Decomposition** | Identity, Static, ForwardReasoning (SHOP), LLM, Hybrid (static+LLM), GOAP (backward-chaining), Heuristic (scored methods) |
| **Activation** | OnExplicitDispatch, MaxIterationsGuard |
| **Aggregation** | PassThrough, CollectAll, MajorityVote |
| **Termination** | GoalReached, MaxIterations, JudgeConvergence (LLM judge), ConvergenceTermination (epistemic+structural) |

### Speech Acts (9, casehub-qhorus)

QUERY, COMMAND, RESPONSE, STATUS, DECLINE, HANDOFF, DONE, FAILURE, EVENT

### Channel Semantics (5, casehub-qhorus)

APPEND, COLLECT, BARRIER, EPHEMERAL, LAST_WRITE

### Commitment Lifecycle (7 states, casehub-qhorus)

OPEN → ACKNOWLEDGED → FULFILLED / FAILED / DECLINED / DELEGATED / EXPIRED

### Conversation Protocol (casehub-blocks)

Entry types: RAISE, AGREE, COUNTER, DISPUTE, QUALIFY, FLAG_HUMAN, DECLINED, MEMO, SUB_TASK_REQUEST, SUB_TASK_FINDING, SUB_TASK_ERROR, RESTART_CONTEXT

Epistemic rules: explicitAcknowledgement, tacitAcceptance, commitmentResolution (composable via and/or)

Convergence states: PROGRESSING, CONVERGING, CONSENSUS, DEADLOCK, DIMINISHING_RETURNS

### Routing Signal Providers (casehub-engine + blocks)

Workload, Trust, Experience, Personality/Disposition, Semantic (embedding), LLM, CBR, PlanComposition, Predecessor, Coordination

### Agent Types (5, casehub-blocks)

WorkerAgent, ChannelAgent, HumanAgent, ExternalAgent, ComposedAgent (nested ExecutionModel — recursive composition)

### Execution Drivers (2)

OrchestratedDriver (imperative while-loop), ChoreographedDriver (event-reactive)

### Mesh Participation (3)

ACTIVE, REACTIVE, SILENT

### Oversight

ActionRiskClassifier, RiskDecision (Autonomous/GateRequired), QuorumConfig (M-of-N), OversightGateService

### Trust Maturity Phases

BOOTSTRAP, BORDERLINE, QUALIFIED, EXCLUDED_PHASE2B, EXCLUDED_PHASE3

### Watchdog Conditions (11)

BARRIER_STUCK, APPROVAL_PENDING, AGENT_STALE, CHANNEL_IDLE, QUEUE_DEPTH, CONTEXT_PRESSURE, LOOP_DETECTED, OBLIGATION_FAN_OUT, CONVERSATION_STALL, ECHO_CHAMBER, CIRCULAR_DELEGATION

---

## Explicit Patterns

Patterns directly named and implemented in the platform.

### Orchestration Patterns (8, via PatternType enum)

| Pattern | Builder | Routing | Aggregation | Termination |
|---------|---------|---------|-------------|-------------|
| **SEQUENCE** | `SequenceBuilder` | Sequential (advances in order) | PassThrough | After agentCount iterations |
| **PARALLEL** | `ParallelBuilder` | All candidates simultaneously | CollectAll | After single iteration |
| **LOOP** | `LoopBuilder` | RoundRobin (cycles via AtomicInteger) | PassThrough | maxIterations or exitCondition |
| **CONDITIONAL** | `ConditionalBuilder` | FirstMatch with when(Predicate, AgentRef) | PassThrough | After single match |
| **SUPERVISOR** | `SupervisorBuilder` | LlmSelected (when AgentProvider given) | PassThrough | MaxIterations(10) default |
| **DEBATE** | `DebateBuilder` | RoundRobin among debaters | PassThrough | judge(AgentRef) OR convergence (mutually exclusive) |
| **VOTING** | `VotingBuilder` | All evaluators | MajorityVote (or custom) | After single round |
| **HTN** | `HtnBuilder` | Determined by task tree | Determined by flattened DAG | Determined by decomposed plan |

### Engine Patterns

| Pattern | What it is |
|---------|-----------|
| **Choreographed Case** | Event-reactive via ChoreographedDriver; contextChange triggers + binding guards |
| **Orchestrated Case** | Imperative via OrchestratedDriver; suspend/resume semantics |
| **Blackboard** | Hayes-Roth 1985; CaseContext is shared state; bindings react to context changes |

### Conversation Patterns

| Pattern | What it is |
|---------|-----------|
| **Structured Deliberation** | ConversationProtocol + ConversationProjection + ConversationFold; round-based multi-agent discourse |
| **Epistemic Common Ground** | CommonGroundAnalyser partitions points into established/pending/disputed |
| **Convergence Detection** | ConvergenceAnalyser evaluates PROGRESSING/CONVERGING/CONSENSUS/DEADLOCK/DIMINISHING_RETURNS |

### Summarisation Patterns

| Pattern | What it is |
|---------|-----------|
| **Tiered Summarisation** | Volume-adaptive: verbatim (small) / grouped (medium) / LLM-synthesised (large) |
| **Observation Pipeline** | Demand-driven rendering: ObservationAccumulator + TieredObservationRenderer |
| **Affordance Grounding** | Per-entity observation with action vocabulary for LLM agent context |

### Infrastructure Patterns

| Pattern | What it is |
|---------|-----------|
| **Agent Mesh (3-channel)** | Normative work/observe/oversight layout with participation strategies |
| **Trust-Weighted Routing** | Bayesian Beta trust scoring + 4-phase maturity classification |
| **CBR-Informed Routing** | Feature extraction + similarity retrieval + outcome recording |
| **Oversight Gate** | PlannedAction + risk classification + human approval + M-of-N quorum |
| **Commitment Lifecycle** | 7-state obligation tracking with temporal enforcement |
| **Normative Accountability** | 4-layer framework: Illocutionary + Commitment + Temporal + Enforcement |
| **Peer Attestation** | Multi-agent ledger verification: ENDORSED / CHALLENGED |

---

## Compositional Patterns

Patterns that emerge from combining primitives. Not explicitly named in the platform but expressible through composition.

### Coordination Protocols

| Pattern | Composing Primitives | Notes |
|---------|---------------------|-------|
| **Contract Net** | PARALLEL routing (broadcast to candidates) + COMMAND speech act (obligation) + commitment lifecycle (proposals as OPEN commitments) + CollectAll + selection | FIPA Contract Net IP |
| **Iterated Contract Net** | Contract Net + LOOP + convergence termination | Multiple proposal rounds |
| **Auction (sealed-bid)** | ComposableAgentRoutingStrategy (weighted signal blend) + commitment fulfillment | Functionally equivalent to sealed-bid; competitive scoring replaces explicit bidding |
| **Brokering** | ComposableAgentRoutingStrategy + multiple RoutingSignalProviders | Routing pipeline IS a broker |
| **Recruiting** | WorkerProvisioner SPI + CandidateMatchingStrategy | Dynamic agent provisioning |
| **Consensus Gate (M-of-N)** | CompletionSemantics.MofN OR VOTING + MajorityVote OR work item requiredCount/completedCount | Multiple mechanisms express M-of-N |

### Escalation and Oversight

| Pattern | Composing Primitives | Notes |
|---------|---------------------|-------|
| **Escalation Chain** | HANDOFF speech act + commitment.EXPIRED + watchdog OBLIGATION_FAN_OUT + OutcomePolicy.REROUTE | Cascading re-dispatch with progressive authority |
| **Tiered Escalation** | Escalation Chain + trust maturity phases + ActionRiskClassifier + QuorumConfig | Human gates at higher tiers, automatic at lower |
| **Pipeline with Gates** | SEQUENCE + CONDITIONAL + ActionRiskClassifier + OversightGateService | Linear pipeline with human approval at risky steps |
| **Accountability Chain** | EventLogListener + LedgerExecutionListener (Art.12) + MetricsListener (OTel) | Full traceability of every decision |

### Multi-Agent Discourse

| Pattern | Composing Primitives | Notes |
|---------|---------------------|-------|
| **Peer Review** | PARALLEL + CommonGroundAnalyser + PeerAttestationEvent + ENDORSED/CHALLENGED | Multiple independent reviewers converge |
| **Maker-Checker** | SEQUENCE (maker → checker) + GoalReached + OutcomePolicy.REROUTE on FAILED | Two-stage quality assurance |
| **Multi-Perspective Analysis** | PARALLEL + CollectAll + JudgeConvergence (LLM synthesises) | Fan-out to specialists, fan-in via judge |
| **Mediation** | SUPERVISOR (mediator) + deliberation channel + ConvergenceTermination | Third-party facilitator guiding agreement |
| **Facilitated Group Decision** | SUPERVISOR + DEBATE + VOTING | Debate explores options, vote decides |
| **Round-Robin Discussion** | LOOP + RoundRobinRouting + MaxIterations + shared channel | Agents take turns contributing |
| **Socratic Dialogue** | LOOP + QUERY/RESPONSE + tacitAcceptance + convergence monitoring | Iterative questioning toward understanding |
| **Brainstorming / Ideation** | PARALLEL + CollectAll (no convergence filter) | Divergent generation; all ideas retained |

### Agent Lifecycle

| Pattern | Composing Primitives | Notes |
|---------|---------------------|-------|
| **Hierarchical Supervision** | SUPERVISOR + ComposedAgent (nested ExecutionModel) | Supervisor delegates to sub-supervisors recursively |
| **Sub-Case Delegation** | subCase binding + SubCaseCompletionStrategy + parent DELEGATED state | Parent spawns child case; blocks until complete |
| **Dynamic Team Formation** | WorkerProvisioner + CandidateMatchingStrategy + CandidateSetStrategy (JQ-based) | Dynamically assemble agents by capability |
| **Guard-Gated Activation** | StageGate + StagedBindings + StageAwareCandidateSupplier | Agents activated only when their stage becomes active |
| **Disposition-Based Matching** | DispositionAwareRouting + DispositionProfile + AgentDisposition | Personality/style matching for task-agent fit |
| **Compound Plan** | Compound PlanItemDefinition + CompletionSemantics (All/MofN/FirstWins) + DispatchMode | Container for child tasks with configurable completion |

### LLM-Specific Compositions

| Pattern | Composing Primitives | Notes |
|---------|---------------------|-------|
| **ReAct Loop** | LOOP + ExternalAgent (tool invocation) + GoalReached termination | Reason-Act cycle; baseline agentic pattern |
| **Reflection** | LOOP + JudgeConvergence (self-evaluation) | Self-correction via iterative self-assessment |
| **Group Chat** | LOOP + RoundRobinRouting + shared channel + speaker selection | Dominant LLM multi-agent pattern (AutoGen's primary abstraction) |
| **Magentic (Dynamic Planning)** | SUPERVISOR + LLM decomposition + dynamic plan generation | Microsoft Agent Framework pattern |
| **Competitive Selection** | PARALLEL + MajorityVote or JudgeConvergence + scored aggregation | Multiple agents produce candidates; best selected |

### Event and Integration

| Pattern | Composing Primitives | Notes |
|---------|---------------------|-------|
| **Channel Event Bridge** | ChannelEventAdapter (channel→bus) + ChannelEventPublisher (bus→channel) | Bidirectional qhorus↔summarisation bridge |
| **Event-Reactive Choreography** | ChoreographedDriver + CDI events + QhorusMessageSignalBridge | Case progression driven by events |
| **Context-Driven Activation** | contextChange trigger + binding.when() guard (JQ) + CaseContext layers | Agents activated when context conditions become true |
| **Publish-Subscribe** | EventStreamBus + MessageObserver (LOCAL/CLUSTER) + ChannelActivityBroadcaster | Event distribution across agents and nodes |
| **Human-Agent Collaboration** | HumanParticipatingChannelBackend + InboundNormaliser + full MessageType participation | Human on equal footing with agents |
| **A2A Federation** | /.well-known/agent-card.json + /a2a/message:send + SSE streaming | Cross-platform agent discovery and delegation |
| **Resilient Execution** | FailurePolicy (RETRY_BROADER/ESCALATE) + AgentRetryPolicy + DLQ + PoisonPill detection | Fault-tolerant execution with progressive recovery |
| **Watchdog-Driven Monitoring** | 11 watchdog conditions + configurable thresholds + WatchdogAlertEvent | Continuous health monitoring with automated alerts |

---

## Coverage Gaps

Patterns that are NOT expressible through composition of existing primitives.

| Pattern | Source | Why it's missing | Impact |
|---------|--------|-----------------|--------|
| **English Auction (ascending bids)** | FIPA00031, Game theory | No iterative bidding round mechanism; routing does sealed-bid scoring but not ascending price dynamics | Low — only matters if task allocation needs explicit bidding |
| **Dutch Auction (descending price)** | FIPA00032, Game theory | No descending price mechanism | Low — same as English |
| **Vickrey / Second-Price** | Game theory | Routing selects by highest score; no second-price payment semantics | Very low — irrelevant unless building economic mechanisms |
| **Coalition Formation** | MAS theory (Sandholm) | No self-organization protocol for agents forming dynamic coalitions; teams are configured externally | Medium — matters for emergent team composition |
| **Joint Intentions** | BDI (Bratman, Cohen & Levesque) | No formal shared mental state model; agents coordinate via shared context but don't form joint commitments to shared plans | Low — shared context + convergence approximates this |
| **Formal Belief Revision** | AGM theory | CaseContext updates incrementally but has no formal belief revision operators (contraction, expansion, revision) | Low — practical for LLM agents |
| **Normative Conflict Resolution** | Deontic logic | Watchdogs detect conflicts but no formal protocol for resolving conflicting norms | Medium — matters when multiple normative rules contradict |

---

## Cross-Reference: Patterns to Example Slices

| Slice | Explicit Patterns | Compositional Patterns |
|-------|------------------|----------------------|
| **1. Helpdesk** | SEQUENCE, CONDITIONAL, Choreographed Case, Blackboard | Context-Driven Activation, Event-Reactive Choreography, Human-Agent Collaboration |
| **2. Code Review** | DEBATE, LOOP, VOTING, Structured Deliberation, Epistemic Common Ground, Convergence Detection, Peer Attestation | Peer Review, Maker-Checker, Round-Robin Discussion, Socratic Dialogue, Reflection |
| **3. Incident Response** | SUPERVISOR, Trust-Weighted Routing, CBR-Informed Routing, Oversight Gate, Normative Accountability | Escalation Chain, Tiered Escalation, Pipeline with Gates, Accountability Chain, Hierarchical Supervision, Multi-Perspective Analysis, Brokering |
| **4. Project Planning** | HTN, GOAP Decomposition, Compound Plan | Sub-Case Delegation, Guard-Gated Activation, Brainstorming, Magentic (Dynamic Planning) |
| **5. Negotiation** | Commitment Lifecycle | Contract Net, Iterated Contract Net, Mediation, Auction (sealed-bid) |
| **6. Collaboration** | Agent Mesh, Affordance Grounding, Publish-Subscribe | Group Chat, Dynamic Team Formation, Disposition Matching, Competitive Selection, A2A Federation |
| **7. Summarisation** | Tiered Summarisation, Observation Pipeline | Channel Event Bridge |

### Unmapped Patterns (supported but no slice demonstrates)

These are expressible through composition but not yet assigned to a teaching slice:

- Resilient Execution (DLQ, retry, PoisonPill)
- Watchdog-Driven Monitoring (all 11 conditions)
- Recruiting (WorkerProvisioner)
- Facilitated Group Decision (SUPERVISOR + DEBATE + VOTING)
- ReAct Loop (baseline single-agent pattern)

---

## Open Questions

- **Is inter-agent communication a missing sixth SPI?** Currently, whether agents see each other's work during execution is hard-wired per pattern (DEBATE shares a channel, PARALLEL isolates, SUPERVISOR sees all). If the slices reveal cases where communication topology needs to be composed independently of the orchestration pattern, this becomes a new SPI. To be validated by use cases, not theory.

## Coverage Statistics

- **Total patterns cataloged:** 88
- **Direct platform coverage:** 38 (43%)
- **Compositional coverage:** 38 (43%)
- **Partial coverage:** 6 (7%)
- **Missing from platform:** 7 (8%)
- **Mapped to a slice:** 69 (78%)
- **Unmapped (supported, no slice):** 12 (14%)

---

## Sources

- FIPA specifications (ACL, interaction protocols FIPA00026-FIPA00036)
- BDI architecture (Bratman, Rao & Georgeff, Cohen & Levesque)
- Argumentation frameworks (Dung 1995, Walton & Krabbe)
- Deontic logic and normative MAS
- Organizational theory (Mintzberg coordination mechanisms)
- CSCW patterns (delegation, escalation, mediation)
- Game theory and mechanism design (auctions, markets)
- LLM multi-agent frameworks (AutoGen, CrewAI, LangGraph, OpenAI Agents SDK, Microsoft Azure Agent Framework)
- Hayes-Roth 1985 (Blackboard Architecture)
