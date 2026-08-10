---
layout: post
title: "Teaching a Planner to Rank Its Own Options"
date: 2026-08-01
type: phase-update
entry_type: note
subtype: diary
projects: [casehub-blocks]
tags: [htn, heuristics, decomposition, planning]
---

Most planning systems face the same fork: let the LLM generate the plan (flexible, unsound) or write rigid symbolic rules (sound, brittle). A recent paper from the Pytrich team found a third option — have the LLM generate *heuristic functions* that guide a symbolic planner's search. The planner still enforces correctness. The LLM only affects which paths get tried first.

The results are striking: 131 of 139 benchmark problems solved, with 83% of those requiring fewer node expansions than the baseline. Soundness stays with the planner. The LLM contributes judgment, not authority.

CaseHub already has this fork. `StaticDecomposition` picks the first method whose guard predicate passes — deterministic, fast, but blind to quality when multiple methods are valid. `LlmDecomposition` hands the whole thing to an LLM — flexible, but the plan's correctness depends entirely on the model's output. `HybridDecomposition` chains them: static first, LLM fallback when nothing matches. None of these rank methods by fitness for the current state.

### The hard part: where does the heuristic live?

The obvious answer — add a scoring function to `HeuristicDecomposition` that ranks methods before trying them — is straightforward. The interesting design question is how that scoring propagates through nested compound tasks.

`StaticDecomposition` delegates to `SequenceStrategy` for compound tasks with children. `SequenceStrategy` internally hardcodes `new StaticDecomposition<>()` for resolving nested compounds. Any heuristic applied at the root dies one level down — the children revert to first-match.

I considered three ways to fix this. Threading the heuristic through constructor injection on every strategy class. Pattern matching on `instanceof SequenceStrategy` to intercept children (the approach `ForwardReasoningDecomposition` already uses for effect propagation). Or carrying the active decomposer in the context itself.

The context-carried decomposer won out. `AgenticDecompositionContext` gains a nullable `decomposer` field. `SequenceStrategy` checks for it and delegates child resolution through whatever strategy is active — falling back to `StaticDecomposition` when null. `HeuristicDecomposition` sets itself as the decomposer before calling any method's strategy, and heuristic ranking propagates to every level of the tree without a single `instanceof` check.

This emerged from the adversarial design review, not the original brainstorm. Claude caught that the original spec would add two more `instanceof SequenceStrategy` consumers to an already-strained coupling pattern — production code pattern-matching on a package-private type to work around its hardcoded internal strategy. The context-carried decomposer eliminates the coupling for `HeuristicDecomposition` and establishes the right pattern for future strategies.

### Backtracking that isn't free

The other non-obvious design choice: what happens when the best-ranked method fails downstream? `StaticDecomposition` picks one method and commits — if it fails deep in the subtree, the whole decomposition fails. `HeuristicDecomposition` catches `NoMethodMatchedException` from the best method's subtree and tries the next-ranked alternative.

The spec originally described this as "free" since planning has no side effects. Claude's review caught the gap: when subtrees contain `LlmDecomposition` or `HybridDecomposition`, discarded expansions carry real latency and token cost. In the worst case with N root methods and M child methods, backtracking explores up to N × M subtrees. The backtracking is structurally safe — no state mutation — but computationally expensive when LLM calls are in the tree. The spec now documents this explicitly so consumers can make informed choices about where to place LLM-involving strategies in their decomposition trees.

### Three heuristics, one SPI

`DecompositionHeuristic<T>` is the scoring interface — batch-native (receives all eligible methods at once), async, higher-score-is-better to match CaseHub's routing conventions. Three implementations ship:

`StructuralCostHeuristic` walks the task tree and estimates plan cost from structure alone — leaf tasks cost 1, sequences sum children, nested compounds take the minimum across their methods. Zero LLM cost. This is the CaseHub equivalent of the Pytrich paper's strongest heuristic pattern: a precomputed minimum-decomposition cost table.

`LlmDecompositionHeuristic` sends the compound task and its methods to an LLM for online evaluation. The LLM sees auto-generated method descriptions extracted from the task tree structure — no `DecompositionMethod.name()` field needed. On failure, it degrades gracefully to equal scores, preserving declaration order as a fallback.

`CompositeHeuristic` combines delegates with normalized weighted sum. Normalization is the key detail — structural cost returns negative integers while LLM returns [0, 1]. Without normalizing each delegate's scores to a common scale before weighting, the larger absolute values dominate regardless of what the weights say.

### Where this goes

The immediate value is modest — most CaseHub compound tasks have one or two methods, making heuristic guidance unnecessary. The value is architectural: when the HTN domain model matures and compound tasks gain multiple methods with overlapping guards, the scoring infrastructure is already in place. The structural heuristic works today at zero cost. The LLM heuristic works today at token cost. The composite combines them. And the `DecompositionHeuristic` SPI is open for implementations we haven't built yet — CBR-based scoring from historical case outcomes, offline heuristic generation (the paper's actual approach), or domain-specific hand-coded ranking functions.

The paper's deeper insight holds: LLMs are better at *judging* plans than *making* them. Letting the symbolic planner own correctness while the LLM owns preference is a division of labour that plays to each system's strengths.
