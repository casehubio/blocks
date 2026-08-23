# Annotation Capability Matrix

Maps every annotation capability to the example that demonstrates it and the test that verifies it.

## Examples

| Example | Domain | Interface | Pattern |
|---------|--------|-----------|---------|
| **Incident Triage** | Cybersecurity — supervised incident response | `IncidentTriage` | Supervisor |
| **Containment Debate** | Cybersecurity — containment strategy evaluation | `ContainmentDebate` | Debate |
| **Repair Strategy Review** | Aviation MRO — repair approach selection | `RepairStrategyReview` | Debate |
| **Wildfire Resource Consensus** | Disaster — multi-agency resource allocation | `WildfireResourceConsensus` | Voting |
| **Wildfire Deployment** | Disaster — phased task decomposition | `WildfireDeployment` | HTN |
| **Incident Sequence** | Cybersecurity — detect → triage → contain pipeline | `IncidentSequence` | Sequence |
| **Wildfire Parallel Assessment** | Disaster — concurrent multi-agency assessment | `WildfireParallelAssessment` | Parallel |
| **Aircraft Inspection Loop** | Aviation MRO — iterative airworthiness inspection | `AircraftInspectionLoop` | Loop |
| **Incident Conditional Routing** | Cybersecurity — severity-based handler routing | `IncidentConditionalRouting` | Conditional |
| **Attested Incident Triage** | Cybersecurity — supervised triage with attestation | `AttestedIncidentTriage` | Supervisor |

## Capability → Example Matrix

### Pattern Annotations

| Capability | Annotation | Incident Triage | Containment Debate | Repair Strategy | Wildfire Consensus | Wildfire Deploy | Incident Sequence | Wildfire Parallel | Aircraft Loop | Incident Conditional | Attested Triage | Validation Tests |
|-----------|------------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---|
| Supervisor pattern | `@Supervisor` | ✓ | — | — | — | — | — | — | — | — | ✓ | PatternValidationTest |
| Supervisor maxIterations | `@Supervisor(maxIterations)` | ✓ | — | — | — | — | — | — | — | — | ✓ | RecorderWiringTest |
| Debate pattern | `@Debate` | — | ✓ | ✓ | — | — | — | — | — | — | — | PatternValidationTest |
| Debate maxRounds | `@Debate(maxRounds)` | — | ✓ | ✓ | — | — | — | — | — | — | — | RecorderWiringTest |
| Voting pattern | `@Voting` | — | — | — | ✓ | — | — | — | — | — | — | PatternValidationTest |
| HTN pattern | `@Htn` | — | — | — | — | ✓ | — | — | — | — | — | PatternValidationTest |
| Sequence pattern | `@Sequence` | — | — | — | — | — | ✓ | — | — | — | — | PatternValidationTest |
| Sequence name | `@Sequence(name)` | — | — | — | — | — | ✓ | — | — | — | — | RecorderWiringTest |
| Parallel pattern | `@Parallel` | — | — | — | — | — | — | ✓ | — | — | — | PatternValidationTest |
| Loop pattern | `@Loop` | — | — | — | — | — | — | — | ✓ | — | — | PatternValidationTest |
| Loop maxIterations | `@Loop(maxIterations)` | — | — | — | — | — | — | — | ✓ | — | — | RecorderWiringTest |
| Conditional pattern | `@Conditional` | — | — | — | — | — | — | — | — | ✓ | — | PatternValidationTest |

### Role Annotations

| Capability | Annotation | Incident Triage | Containment Debate | Repair Strategy | Wildfire Consensus | Wildfire Deploy | Incident Sequence | Wildfire Parallel | Aircraft Loop | Incident Conditional | Attested Triage | Validation Tests |
|-----------|------------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---|
| Agent role | `@Agent(name, systemPrompt)` | ✓ | — | — | — | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | PatternValidationTest |
| Debater role | `@Debater(role, systemPrompt)` | — | ✓ | ✓ | — | — | — | — | — | — | — | PatternValidationTest |
| Judge role | `@Judge(systemPrompt)` | — | ✓ | ✓ | — | — | — | — | — | — | — | PatternValidationTest |
| Voter role | `@Voter(role, systemPrompt)` | — | — | — | ✓ | — | — | — | — | — | — | PatternValidationTest |

### Governance Annotations

| Capability | Annotation | Incident Triage | Containment Debate | Repair Strategy | Wildfire Consensus | Wildfire Deploy | Attested Triage | Validation Tests |
|-----------|------------|:---:|:---:|:---:|:---:|:---:|:---:|:---|
| Oversight gate | `@OversightGate(classifier)` | ✓ | — | ✓ | ✓ | — | — | GovernanceValidationTest |
| Oversight reversible | `@OversightGate(reversible)` | — | — | — | — | — | — | GovernanceValidationTest |
| Trust routing | `@TrustRouted(threshold, ...)` | ✓ | — | — | — | — | — | GovernanceValidationTest |
| CBR evidence routing | `@CbrRouted(weights)` | — | — | ✓ | — | — | — | GovernanceValidationTest |
| Attestation | `@Attestation(observer, capabilityTag)` | — | — | — | — | — | ✓ | ExamplePatternsTest |

### Build Extension

| Capability | What it does | Validation Tests |
|-----------|-------------|:---|
| Dual pattern rejection | Two pattern annotations on same method → build error | PatternValidationTest |
| @Worker + pattern rejection | `@Worker` and pattern on same method → build error | PatternValidationTest |
| Missing role rejection | `AgentRef` parameter without role annotation → build error | PatternValidationTest |
| systemPrompt/agentId exclusivity | Both specified or neither specified → build error | PatternValidationTest |
| Orphan governance rejection | Governance annotation without @Worker or pattern → build error | GovernanceValidationTest |
| @Customize builder matching | Builder type must match declaring class's pattern | CustomizeValidationTest |
| @Customize CDI validation | Non-builder parameters must be CDI-compatible types | CustomizeValidationTest |
| ExecutionModel generation | Recorder builds correct model per pattern type | RecorderWiringTest |
| Judge → JudgeConvergence wiring | `@Judge` participant triggers JudgeConvergence termination | RecorderWiringTest |
| Bean name resolution | `name` attribute overrides method name for `@Named` qualifier | RecorderWiringTest |

## Coverage Summary

| Category | Total | In Examples | In Validation Tests Only |
|----------|-------|-------------|--------------------------|
| Pattern annotations | 8 | 8 | 0 |
| Role annotations | 4 | 4 | 0 |
| Governance annotations | 4 | 4 | 0 |
| Build-time validations | 7 | — | 7 |
| Recorder wiring | 3 | — | 3 |
| **Total** | **26** | **16** | **10** |

## How to Run

```bash
# All tests (runtime + deployment)
mvn test -pl annotations/runtime,annotations/deployment

# Examples only
mvn test -pl annotations/runtime,annotations/deployment -Dtest=ExamplePatternsTest -Dsurefire.failIfNoSpecifiedTests=false

# Validation rules only
mvn test -pl annotations/runtime,annotations/deployment -Dtest=PatternValidationTest,GovernanceValidationTest,CustomizeValidationTest -Dsurefire.failIfNoSpecifiedTests=false

# Recorder wiring only
mvn test -pl annotations/runtime,annotations/deployment -Dtest=RecorderWiringTest -Dsurefire.failIfNoSpecifiedTests=false
```
