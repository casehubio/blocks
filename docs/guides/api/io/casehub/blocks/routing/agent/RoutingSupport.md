# io.casehub.blocks.routing.agent.RoutingSupport

**Package:** `io.casehub.blocks.routing.agent`

**Kind:** `class`

## Fields

### `MAPPER` (`ObjectMapper`)

### `SYSTEM_PROMPT` (`java.lang.String`)

## Constructors

### `private RoutingSupport()`

## Methods

### `static io.casehub.blocks.routing.agent.RoutingSupport.TrustFilterOutcome applyTrustFilter(TrustCandidateClassifier classifier, TrustScoreSource scoreSource, TrustRoutingPolicyProvider policyProvider, AgentRoutingContext context, java.util.List<AgentCandidate> candidates)`

#### Parameters

- `classifier` (`TrustCandidateClassifier`)
- `scoreSource` (`TrustScoreSource`)
- `policyProvider` (`TrustRoutingPolicyProvider`)
- `context` (`AgentRoutingContext`)
- `candidates` (`java.util.List<AgentCandidate>`)

### `static java.lang.String buildCandidateCard(AgentCandidate candidate)`

#### Parameters

- `candidate` (`AgentCandidate`)

### `static java.lang.String buildUserPrompt(java.lang.String capabilityName, java.lang.String caseContextSummary, java.util.List<AgentCandidate> candidates)`

#### Parameters

- `capabilityName` (`java.lang.String`)
- `caseContextSummary` (`java.lang.String`)
- `candidates` (`java.util.List<AgentCandidate>`)

### `static java.lang.String extractAgentName(java.lang.String text)`

#### Parameters

- `text` (`java.lang.String`)

### `static java.lang.String invokeAndCollect(AgentProvider provider, java.lang.String systemPrompt, java.lang.String userPrompt)`

#### Parameters

- `provider` (`AgentProvider`)
- `systemPrompt` (`java.lang.String`)
- `userPrompt` (`java.lang.String`)

### `static java.lang.String parseSelection(java.lang.String llmResponse, java.util.List<AgentCandidate> candidates)`

#### Parameters

- `llmResponse` (`java.lang.String`)
- `candidates` (`java.util.List<AgentCandidate>`)
