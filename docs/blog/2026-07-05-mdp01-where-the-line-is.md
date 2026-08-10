---
layout: post
title: "Where the Line Is"
date: 2026-07-05
type: phase-update
entry_type: note
subtype: diary
projects: [casehub-blocks]
tags: [consolidation, compliance, type-design]
---

The compliance record consolidation from blocks #17 had been sitting as a trailing obligation for weeks — "shared types exist but aml and devtown haven't migrated." Except the shared types didn't exist. CLAUDE.md said they did. The handover said they did. Neither was true. The `io.casehub.blocks.routing` package had the `agent/` sub-package from the AI routing work, but `RequirementStatus`, `RoutingDecisionRecord`, and `TrustRoutingRequirement` were aspirational documentation, not code.

The interesting question wasn't whether to extract these types — that was obvious — but where to draw the line between shared and domain-specific. Both repos had the same three types, but they weren't identical.

AML's `RoutingDecisionRecord` carried seven fields. DevTown's had five. The extra two — `reconstructed` and `observerFailed` — tracked whether AML's attestation observer reliably captured the routing decision. These are evidence quality metadata: did we observe this decision directly, or was it reconstructed from cache after the observer failed? FATF requires demonstrable evidence chains, so a reconstructed attestation downgrades the compliance status from CLOSED to PARTIAL.

The temptation was a superset record — all fields, some nullable. DevTown would carry two boolean fields it never uses. Every future domain gets AML's attestation concerns baked in whether they need them or not.

I went the other way. The record captures routing decision facts: who was selected, what score, what threshold, which evidence entry. Five fields. The evidence quality assessment stays in AML's service, reading from the attestation objects directly — which is where those flags originated before being copied onto the record in the first place. The round-trip through the record was unnecessary; the service already had the attestation stream when it determined status.

`TrustRoutingRequirement` was cleaner. Both repos had the same shape — requirement ID, citation, mechanism, status, list of decisions. The only difference was the constants: AML cites FATF Recommendation 20, devtown cites EU AI Act Article 14. The blocks type takes those as constructor parameters. Each domain supplies its own regulatory reference. No constants in the shared type.

`RequirementStatus` was identical everywhere. Four values, no variation. Trivially shared.

We migrated ten files in each repo. Three deleted, seven updated with new imports. AML's test suite lost two assertions that checked `.observerFailed()` and `.reconstructed()` on the record — but the status tests (PARTIAL when observer fails, PARTIAL when reconstructed) still pass because that logic reads from the attestation stream, not the record.

Both repos had pre-existing compilation failures on their branches — missing ledger and work dependencies unrelated to the compliance types. The compliance imports resolve correctly; the branch issues are their own problem.

One useful side-effect: checking neural-text#20 revealed it's CLOSED, which unblocks blocks #16 (RAG-enriched routing via `CbrCaseMemoryStore`). The `CbrAgentRoutingStrategy` already exists with a fallback path, and now the real case retriever contract is available.
