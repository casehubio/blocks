---
layout: post
title: "Composable routing enrichment"
date: 2026-07-06
type: phase-update
entry_type: note
subtype: diary
projects: [casehub-blocks]
tags: [routing, cbr, llm, spi]
series: issue-016-rag-enriched-routing
---

The LLM routing strategy could reason about agent descriptors. The CBR strategy could tally historical success rates. Neither could do both. The obvious fix — a new `"rag"` strategy combining them — would have started a combinatorial problem. Every new signal source (semantic similarity, trust score display, domain regulatory context) crossed with every decision mechanism means M×N strategies. Two signal sources and two mechanisms is four strategies. Three of each is nine. The proliferation isn't hypothetical; it's exactly how the existing code ended up with duplicate trust filtering in both strategies.

The real fix was separating the two axes. Signal enrichment is a composable concern — the LLM strategy discovers all available `RoutingPromptSection` implementations via CDI, renders each one, and appends the results to the prompt. CBR history is just the first section. When semantic similarity enrichment arrives, it's another section. The LLM strategy doesn't change.

A `RoutingPromptAssembler` sits between the strategy and the sections — sorts by `@Priority`, catches rendering failures per section so one broken enricher can't take down routing, returns null when no section has anything to say. The cold-start case (no CBR history at all) degrades cleanly to the existing behaviour: the LLM sees agent descriptors and case context, nothing more.

The other half is the feedback loop. Routing decisions go into the CBR store as `PlanCbrCase` entries — problem text, selected agent, outcome. Future queries retrieve similar cases and show the LLM empirical evidence alongside agent briefings. The recording happens in engine's `WorkflowExecutionCompletedHandler`, via a new `RoutingOutcomeRecorder` SPI that blocks owns the implementation for.

The design review earned its keep here. I'd originally planned to integrate at `WorkerOutcomeResolvedEvent` — the name suggests "all outcomes resolved." It only fires for failures. The Javadoc says so, but the name doesn't. Using it would have silently accumulated a failure-only CBR store where every historical case was a negative example. The reviewer caught it by reading the actual source, not the name. The correct integration point is `WorkflowExecutionCompleted`, which fires for all four outcome types.

The review also caught the context-snapshot timing problem. The success path applies worker output to the case context before the recorder fires — so the "problem description" recorded would include the worker's own answer, not the case state at routing time. The fix: snapshot before output application, record from the snapshot.

Feature extraction stayed deliberately simple. A `RoutingFeatureExtractor` SPI returns problem text and structured features from the routing context. The default implementation uses `caseContext.toString()` for text similarity and empty features. Domain repos override when they need structured matching — aml might extract transaction type and amount, clinical might extract diagnosis codes. The SPI exists so the vocabulary is consistent between queries and recordings, but nobody needs to implement it until they need structured CBR.
