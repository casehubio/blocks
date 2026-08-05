# io.casehub.blocks.routing.agent.CbrAgentRoutingStrategy

**Package:** `io.casehub.blocks.routing.agent`

**Kind:** `class`

`AgentRoutingStrategy` that uses case-based reasoning (CBR) to select agents based on
historical success patterns, with optional trust-based classification and filtering.

<p>Reads pre-retrieved experiences from `AgentRoutingContext.experiences()` — the engine's
`CbrRetrievalService` populates these based on the case definition's `CbrConfig`.
This strategy analyses plan traces from retrieved experiences to identify which workers have the
highest historical success rate for the target capability.

<p>Falls back to `AgentGraphQuery` when CBR produces no match.

## Fields

### `LOG` (`java.lang.System.Logger`)

### `classifier` (`TrustCandidateClassifier`)

### `graphQuery` (`AgentGraphQuery`)

### `outcomeWeights` (`io.casehub.blocks.routing.agent.CbrOutcomeWeights`)

### `policyProvider` (`TrustRoutingPolicyProvider`)

### `scoreSource` (`TrustScoreSource`)

### `signalAssembler` (`RoutingSignalAssembler`)

## Constructors

### `CbrAgentRoutingStrategy(AgentGraphQuery graphQuery, TrustCandidateClassifier classifier, TrustScoreSource scoreSource, TrustRoutingPolicyProvider policyProvider, io.casehub.blocks.routing.agent.CbrOutcomeWeights outcomeWeights, RoutingSignalAssembler signalAssembler)`

#### Parameters

- `graphQuery` (`AgentGraphQuery`)
- `classifier` (`TrustCandidateClassifier`)
- `scoreSource` (`TrustScoreSource`)
- `policyProvider` (`TrustRoutingPolicyProvider`)
- `outcomeWeights` (`io.casehub.blocks.routing.agent.CbrOutcomeWeights`)
- `signalAssembler` (`RoutingSignalAssembler`)

### `public CbrAgentRoutingStrategy(Instance<AgentGraphQuery> graphQuery, Instance<TrustCandidateClassifier> classifier, Instance<TrustScoreSource> scoreSource, Instance<TrustRoutingPolicyProvider> policyProvider, io.casehub.blocks.routing.agent.CbrOutcomeWeights outcomeWeights, Instance<RoutingSignalAssembler> signalAssembler)`

#### Parameters

- `graphQuery` (`Instance<AgentGraphQuery>`)
- `classifier` (`Instance<TrustCandidateClassifier>`)
- `scoreSource` (`Instance<TrustScoreSource>`)
- `policyProvider` (`Instance<TrustRoutingPolicyProvider>`)
- `outcomeWeights` (`io.casehub.blocks.routing.agent.CbrOutcomeWeights`)
- `signalAssembler` (`Instance<RoutingSignalAssembler>`)

## Methods

### `java.util.Map<java.lang.String,java.lang.Double> analyseExperiences(java.util.List<RetrievedExperience> experiences, java.util.Set<java.lang.String> eligibleIds, java.lang.String capabilityName)`

#### Parameters

- `experiences` (`java.util.List<RetrievedExperience>`)
- `eligibleIds` (`java.util.Set<java.lang.String>`)
- `capabilityName` (`java.lang.String`)

### `private RoutingResult doSelect(AgentRoutingContext context, java.util.List<AgentCandidate> candidates)`

#### Parameters

- `context` (`AgentRoutingContext`)
- `candidates` (`java.util.List<AgentCandidate>`)

### `public java.lang.String id()`

### `public RoutingResult select(AgentRoutingContext context, java.util.List<AgentCandidate> candidates)`

#### Parameters

- `context` (`AgentRoutingContext`)
- `candidates` (`java.util.List<AgentCandidate>`)

### `private java.lang.String tryGraphQuery(AgentRoutingContext context, java.util.Set<java.lang.String> eligibleIds)`

#### Parameters

- `context` (`AgentRoutingContext`)
- `eligibleIds` (`java.util.Set<java.lang.String>`)
