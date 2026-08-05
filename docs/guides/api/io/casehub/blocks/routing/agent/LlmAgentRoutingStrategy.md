# io.casehub.blocks.routing.agent.LlmAgentRoutingStrategy

**Package:** `io.casehub.blocks.routing.agent`

**Kind:** `class`

`AgentRoutingStrategy` that uses LLM reasoning to select agents from a candidate pool,
with optional trust-based classification and filtering.

<h3>Operation modes</h3>

<ul>
  <li><b>Pure LLM mode</b>: when trust services are unavailable, delegates selection entirely to
      the LLM. Never escalates to oversight.
  <li><b>Trust-filtered LLM mode</b>: when `TrustCandidateClassifier`, `TrustScoreSource`, and `TrustRoutingPolicyProvider` are available, applies
      trust-based pre-screening before LLM selection.
</ul>

<h3>Trust classification flow</h3>

When trust services are present, follows the four-phase trust maturity model:

<ol>
  <li>Classify all candidates via `TrustCandidateClassifier`.
  <li>Apply bootstrap guard: if `bootstrapEscalationRequired` is `true` and only
      BOOTSTRAP candidates exist, escalate with `EscalationReason.NO_QUALIFIED_AGENT`.
  <li>Filter eligible candidates: exclude BORDERLINE, EXCLUDED_PHASE2B, and EXCLUDED_PHASE3.
      Also exclude BOOTSTRAP when `bootstrapEscalationRequired` is `true`.
  <li>If eligible pool is empty after filtering, delegate escalation decision to `TrustCandidateClassifier.decide`.
  <li>Otherwise, invoke LLM with the filtered pool.
</ol>

<h3>LLM invocation</h3>

Delegates to `RoutingSupport` for prompt construction, LLM invocation, and response
parsing. Returns `AgentAssignment.Unresolvable` on LLM failure, unparseable response, or
unknown agent selection.

<p>All blocking work runs on `Infrastructure.getDefaultWorkerPool()` via `Uni.emitOn()`.

## Fields

### `LOG` (`java.lang.System.Logger`)

### `agentProvider` (`AgentProvider`)

### `classifier` (`TrustCandidateClassifier`)

### `policyProvider` (`TrustRoutingPolicyProvider`)

### `promptAssembler` (`RoutingPromptAssembler`)

### `scoreSource` (`TrustScoreSource`)

### `systemPromptCustomiser` (`io.casehub.blocks.prompt.SystemPromptCustomiser`)

## Constructors

### `public LlmAgentRoutingStrategy(AgentProvider agentProvider, TrustCandidateClassifier classifier, TrustScoreSource scoreSource, TrustRoutingPolicyProvider policyProvider, RoutingPromptAssembler promptAssembler, io.casehub.blocks.prompt.SystemPromptCustomiser systemPromptCustomiser)`

#### Parameters

- `agentProvider` (`AgentProvider`)
- `classifier` (`TrustCandidateClassifier`)
- `scoreSource` (`TrustScoreSource`)
- `policyProvider` (`TrustRoutingPolicyProvider`)
- `promptAssembler` (`RoutingPromptAssembler`)
- `systemPromptCustomiser` (`io.casehub.blocks.prompt.SystemPromptCustomiser`)

### `public LlmAgentRoutingStrategy(Instance<AgentProvider> agentProvider, Instance<TrustCandidateClassifier> classifier, Instance<TrustScoreSource> scoreSource, Instance<TrustRoutingPolicyProvider> policyProvider, RoutingPromptAssembler promptAssembler, Instance<io.casehub.blocks.prompt.SystemPromptCustomiser> systemPromptCustomiser)`

#### Parameters

- `agentProvider` (`Instance<AgentProvider>`)
- `classifier` (`Instance<TrustCandidateClassifier>`)
- `scoreSource` (`Instance<TrustScoreSource>`)
- `policyProvider` (`Instance<TrustRoutingPolicyProvider>`)
- `promptAssembler` (`RoutingPromptAssembler`)
- `systemPromptCustomiser` (`Instance<io.casehub.blocks.prompt.SystemPromptCustomiser>`)

## Methods

### `private RoutingResult doSelect(AgentRoutingContext context, java.util.List<AgentCandidate> candidates)`

#### Parameters

- `context` (`AgentRoutingContext`)
- `candidates` (`java.util.List<AgentCandidate>`)

### `public java.lang.String id()`

### `public RoutingResult select(AgentRoutingContext context, java.util.List<AgentCandidate> candidates)`

#### Parameters

- `context` (`AgentRoutingContext`)
- `candidates` (`java.util.List<AgentCandidate>`)
