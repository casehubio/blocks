---
layout: post
title: "What belongs in a shared library — and what doesn't"
date: 2026-07-02
type: phase-update
entry_type: note
subtype: diary
projects: [casehub-blocks]
tags: [consolidation, architecture, scope-criteria]
---

## What belongs in a shared library — and what doesn't

I wanted to find every duplicated pattern across our 26 repos and centralise the ones worth sharing. The question that kept coming back wasn't "is this duplicated?" but "does it belong here?"

We opened all 26 repos in a single IntelliJ workspace and fanned out searches across four dimensions: routing and dispatch, gate and oversight, agent and worker orchestration, and data and config utilities. The scan was thorough — every repo searched for class names, SPIs, and structural patterns that appeared in more than one place.

The haul was larger than I expected. Trust routing policy configuration duplicated across five repos. A `GatePolicy` enum independently defined in seven repos with near-identical threshold classification logic copy-pasted between aml and soc. Debate channel infrastructure in drafthouse that devtown, clinical, and aml all need. An oversight gate lifecycle independently implemented in openclaw and life. Backoff delay computation — the exact same stateless function — in platform, workers, and engine. CloudEvent adapters with identical CDI boilerplate in qhorus, connectors, work, and iot. A sliding-window rate limiter with the same `ConcurrentHashMap<K, Deque<Instant>>` pruning pattern in qhorus and claudony. `LeastLoaded` selection reimplemented for different SPIs in engine and work. `RoundRobin` appearing in three places. `AgentProviderChatModel` adapters in platform and eidos. `ValidationResult` records in engine and platform. `CurrentPrincipal` test doubles copied across three repos. Even a `MissingTenancyExceptionMapper` in two repos returning different HTTP status codes — one 403, the other 400.

Not all duplication is worth centralising. A `BackoffDelayCalculator` doesn't need AI integration or foundational platform wiring. It's a stateless function that belongs wherever it already lives. The same goes for CloudEvent adapters, rate limiters, and validation records — small utilities that work fine in platform or engine.

The test we landed on: a pattern belongs in blocks if it needs an LLM in the loop, uses classical AI (planning, Bayesian, CEP), or requires integration across foundational platform parts. Everything else stays where it is. A shared library that absorbs every duplicated utility becomes a dependency magnet that nobody wants to version.

Six patterns passed the test. Trust routing policy configuration — five repos independently parsing the same YAML-to-policy boilerplate. Debate channel infrastructure from drafthouse — structured multi-agent deliberation with typed entries, projections, and dispatch. Oversight gate lifecycle with risk classification — the full classify-gate-fulfill cycle duplicated between openclaw and life, with threshold classifiers copy-pasted across seven consumer repos. Universal pluggable routing strategy — consolidating the ad-hoc routing mechanisms scattered across engine and work. Worker data coordination patterns — DataExchange and DataChannel as shared primitives alongside the existing Blackboard. And a layered event summarisation framework that quarkmind is building — temporal abstraction from raw event streams through CEP-driven moments and phases up to LLM-generated narrative arcs.

We implemented the first: trust routing YAML consolidation. The boilerplate was identical across aml, devtown, and life — read preferences by scope, parse threshold and quality floors, fall back to defaults. A `TrustRoutingPolicyKeys` class parameterised by scope prefix and a `TrustRoutingPolicyResolver` utility eliminated the duplicated parsing. The domain providers still own their config sources — aml reads pure YAML, devtown and life mix domain registries with YAML overlays — but the common machinery is shared.

The scope criteria turned out to be more valuable than any single extraction. Without it, every session will re-litigate whether a duplicated utility belongs in blocks or should stay put. With it, the answer is mechanical: does it involve AI or foundational integration? If not, leave it alone.
