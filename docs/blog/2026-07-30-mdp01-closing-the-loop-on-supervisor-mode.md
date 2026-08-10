---
layout: post
title: "Closing the Loop on Supervisor Mode"
date: 2026-07-30
type: phase-update
entry_type: note
subtype: diary
projects: [casehub-blocks]
tags: [agentic, orchestration, architecture]
---

When I started blocks' agentic package a few months back, it was extracted from a single issue on engine — engine#101, "LLM supervisor mode." That issue described three gaps in CaseHub's orchestration model: no LLM-backed planning strategy, no natural-language descriptions on agent capabilities, and no dynamic goal decomposition. Today I closed engine#101 — and its blocks-side companion epic #10 — because everything it described has been delivered.

The interesting part is how different the result looks from the original plan. Engine#101 proposed a `casehub-supervisor` module in engine with an `LlmPlanningStrategy` that reads CaseContext and picks the next worker. What actually shipped is a compositional framework across eight sub-packages in blocks, with five SPIs — routing, decomposition, activation, aggregation, termination — plus execution drivers, pattern builders, and accountability listeners. The three gaps became seventy classes, and the "LLM picks a worker" primitive expanded into SHOP-style forward reasoning, GOAP backward-chaining, hybrid decomposition with static-to-LLM fallback, case-based reasoning with similarity-weighted scoring, and team composition analysis with adaptation-guided retrieval.

The reason the work landed in blocks rather than engine is worth noting. Blocks sits between foundation and application — it composes across qhorus, engine, work, and eidos APIs. An `LlmPlanningStrategy` inside engine would have created a circular dependency the moment it needed to reference agent identity from eidos or channel coordination from qhorus. Blocks can reach all four without cycles. The architecture found its own home.

Reviewing epic #44 — the broader agentic planning architecture — showed it's also mostly done. Eight of eleven children closed, including both P0 and P1 tiers. What remains are two engine-side infrastructure items (multi-level recovery and plan versioning) and two P4 research directions (LLM-generated HTN heuristics and DSPy-style prompt optimisation). I set up work-slots for all three blocks-side items that are ready to start: #54 (recursive LLM decomposition), #47 (HTN heuristics), and #48 (prompt optimisation).

A question came in from clinical about their #86 — wiring `ProtocolAmendmentAdvisor` to the LLM planning infrastructure. The issue had been blocked on engine#101, expecting an `LlmPlanningStrategy` SPI. What shipped doesn't fit their use case: they're not routing between multiple advisors or decomposing a goal into subtasks. They need a single LLM call that reasons about accumulated trial data and returns a recommendation. The right primitive is `AgentProvider` from platform-agent-api — the direct LLM invocation interface. It's been available for a while; the blocker was never real, just misidentified.

That clinical question is a useful signal. When a framework grows from three gaps to seventy classes, the consumers who were waiting for it may not recognise what arrived. The gap between "LLM supervisor mode" (the engine#101 framing) and "compositional agentic orchestration with five SPIs" (what blocks delivers) is wide enough that a downstream team reading the original issue wouldn't naturally find their way to the right class. Something to address — maybe a routing guide or a "which block do I need?" decision tree.
