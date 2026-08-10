---
title: The glue that was always missing
date: 2026-07-23
author: mdp
tags: [blocks, qhorus, summarisation, channel-summary]
entry_type: note
subtype: diary
phase: implementation
---

# The glue that was always missing

Blocks has had a summarisation framework for weeks. Qhorus has had a channel
summary slot for weeks. Neither talked to the other. The `SummaryUpdateHook`
SPI existed in qhorus-api — with a `NoOpSummaryUpdateHook` that returned
`currentSummary` unchanged. The plumbing was there. The connector wasn't.

That's what #64 is. Not new infrastructure. Not a new framework. Glue.

## The SPI gap

The first thing we hit: `SummaryUpdateContext` tells you *how many* messages
arrived since the last update, but not *what they say*. A summariser that
can't read messages can't summarise. The hook needed the actual content.

Two additions to the qhorus-api record: `recentMessages` (pre-fetched by
qhorus — the 90% path) and `messageQuery` (a channel-scoped query function
for custom access patterns like sliding windows or full re-summarisation).
The combination means simple hooks just use the messages they're given, and
complex hooks can ask for more.

This is a cross-repo change — qhorus SPI enrichment committed to qhorus
main, `mvn install`, then blocks implements the hook. Same pattern as the
oversight and routing consolidations.

## Two implementations, one SPI

The design question was: heuristic or LLM? The answer was both, layered
with CDI.

`HeuristicChannelSummariser` is the `@DefaultBean`. Append-only —
participant names, message counts, time spans, topics. Structural signals
extracted from message metadata. Zero LLM cost, deterministic, always
works. It's the baseline that every deployment gets.

`LlmChannelSummariser` is `@Alternative @Priority(1)`. Edit-mode by
default — it rewrites the *entire* summary when new messages change the
picture. A discussion that was unresolved becomes a decision. A tentative
plan becomes confirmed. The LLM naturally integrates new information into
the existing narrative rather than appending a delta.

The append-vs-edit distinction matters. Append-only summaries accumulate
redundancy — the same topic discussed across multiple windows gets
mentioned in each delta separately. Edit mode produces a single coherent
summary at the cost of an LLM call. The `SummaryMode` enum makes this
a configuration choice.

## What I didn't build

I didn't wire this through the streaming pipeline (`SummarisationRunner`,
`EventAccumulator`, windowing). The hook is pull-based — qhorus calls it
when a threshold is crossed, passes a batch, expects a string back. The
pipeline is push-based — continuous event flow with windowed accumulation.
Different integration patterns for different use cases.

The hook uses `Summariser` (the functional interface) directly. No
pipeline machinery. The hook IS the tick — one invocation, one
summarisation, one result.
