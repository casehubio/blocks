---
layout: post
title: "The Build Extension That Trusts the Builder"
date: 2026-08-23
entry_type: article
subtype: diary
projects: [casehubio/blocks]
tags: [annotations, quarkus, build-extension, jandex, architecture]
series: issue-116-blocks-annotations
---

# The Build Extension That Trusts the Builder

*Continues from [Annotation Boundaries Follow Execution Models](2026-08-22-mdp01-annotation-boundaries-follow-execution-models.md).*

The annotations exist. Eight pattern annotations, four role annotations, four governance meta-annotations — all from yesterday's session. But annotations sitting in a runtime jar don't do anything. A Quarkus build extension has to find them, validate them, and turn them into `ExecutionModel` CDI beans at `RUNTIME_INIT`.

The interesting part wasn't the scanning. It was what the scanner found.

## The scanner is the validator

`PatternAnnotationStep` scans the Jandex index for pattern-annotated methods. The scanning itself is mechanical — find all methods with `@Supervisor`, `@Debate`, etc. What matters is what it rejects.

Five validation rules, all enforced at build time:

No method carries two pattern annotations. No method carries both `@Worker` and a pattern annotation — patterns are standalone interfaces, not worker implementations. Every `AgentRef` parameter must carry a role annotation. Every role annotation must specify exactly one of `systemPrompt` or `agentId` — specifying both is ambiguous, specifying neither is incomplete.

These aren't defensive checks. They're the annotation model's type system, enforced where Java's type system can't reach. A developer who writes `@Debate @Supervisor` on the same method gets a clear build error, not a runtime surprise.

## SequenceBuilder doesn't play by the rules

The recorder maps each `PatternDescriptor` to its corresponding builder — `Patterns.supervisor()`, `Patterns.debate()`, and so on. Most builders set their routing and termination in the constructor. You call `agents()`, then `build()`, and everything works.

`SequenceBuilder` is different. Its constructor sets `routing` and `termination` to `null`. These are only initialised inside `agents()` — because sequential routing and count-based termination both need to know how many agents there are.

Call `build()` without calling `agents()` first and you get an NPE from `Objects.requireNonNull(routing)`. Every other builder forgives this ordering. `SequenceBuilder` does not.

The recorder handles this correctly — it calls `agents()` before `build()` for every pattern type. But the asymmetry is the kind of thing that burns an hour when you're wiring a new consumer.

## ConditionalBuilder and the protected method

Seven of the eight builders expose a public `agents()` method. `ConditionalBuilder` does not — its public API is `when(Predicate, AgentRef)`, designed for programmatic use where you provide the routing predicate.

For annotation-driven patterns, there's no predicate at compile time — the annotation model handles the 80% case, `@Customize` handles the rest. The recorder constructs the `ExecutionModel` directly instead of going through the builder, matching the builder's defaults: `FirstMatchRouting`, `IdentityDecomposition`, `OnExplicitDispatch`, `PassThrough`, single-iteration termination.

It works. But there's a design signal here: when a recorder has to bypass the builder to produce the same output, the builder's public API surface might be too narrow. A follow-up to add a public `agents()` override to `ConditionalBuilder` would clean this up.

## Governance scans compose, they don't merge

`GovernanceAnnotationStep` is a separate scanner from `PatternAnnotationStep`. It finds `@OversightGate`, `@TrustRouted`, `@CbrRouted`, `@Attestation` and validates each has either a `@Worker` or a pattern annotation on the same method. The two scanners produce independent descriptor lists — `PatternDescriptor` and `GovernanceDescriptor` — which the build step can compose when wiring CDI beans.

The validation constraint matters: a governance annotation without a target is a build error. `@OversightGate` on a bare method with no `@Worker` and no pattern annotation means "govern nothing" — which is always a mistake.

## Testing at 60ms, not 6 seconds

Build extension tests typically use `QuarkusUnitTest`, which bootstraps the full Quarkus container. For validation logic that only touches the Jandex index, that's unnecessary overhead.

We used the Jandex `Indexer` API directly. Define fixture interfaces as inner classes of the test, index them from the classpath via `getResourceAsStream()`, and pass the `Index` to the scanner:

```java
private Index indexClasses(Class<?>... classes) throws IOException {
    Indexer indexer = new Indexer();
    for (Class<?> clazz : classes) {
        String path = "/" + clazz.getName().replace('.', '/') + ".class";
        try (var stream = clazz.getResourceAsStream(path)) {
            if (stream != null) indexer.index(stream);
        }
    }
    return indexer.complete();
}
```

Each test runs in about 60ms. The full suite completes in under a second. Fast enough that TDD iteration on validation rules feels instantaneous — write the fixture, write the assertion, run, see the failure, implement, run, green.

The trade-off is real: these tests don't exercise `SyntheticBeanBuildItem` production or CDI container wiring. That integration happens when `EngineAnnotationsCompleteBuildItem` is available for `@Consume` ordering — a dependency that doesn't exist yet. The Jandex-only tests cover the 90% that matters for the annotation model's correctness.
