---
layout: post
title: "The Fourth Supplementary Input"
date: 2026-08-04
type: phase-update
entry_type: note
subtype: diary
projects: [casehub-blocks]
tags: [conversation, progress, rendering]
---

Progress has been sitting in the work repo for two days, fully shipped — six new Maven modules, schema validation, rollup strategies, REST and SSE APIs. The issue that tracked it (work#237) was closed as "ideas-capture" with a body that opens "Nothing here is a spec." The actual code landed in a separate commit.

I spent twenty minutes searching for `ProgressInstance` in IntelliJ before the user pointed out I was looking in `casehub-work-api` when the types live in `casehub-work-progress-api`. A separate module, not in the parent POM's dependency management. The class search returns nothing because blocks doesn't depend on it — and there's no documentation anywhere saying the split exists.

Once the dependency was wired, the implementation was anticlimactic. `RenderContext` already carries three supplementary render-time inputs — reactions, common ground, convergence. Each follows the same pattern: a field on the record, a toggle on `ConversationRendererConfig`, null-guarding in the renderer. Progress is the fourth. The pattern absorbed it without friction.

The one design choice worth noting is the `ProgressRenderer` SPI. `ProgressInstance` stores state as a `JsonNode` — the platform deliberately doesn't interpret what progress means. The `shapeType` field tells you the shape (percentage, count, or step), and the renderer has to parse each differently. A `@FunctionalInterface` with a built-in `DefaultProgressRenderer` handles the three shapes and falls back to raw status for anything it doesn't recognise. Consumers with custom shapes override the SPI.

The step shape is the interesting one. Definition is a `List<StepDefinition>` giving the DAG order; state has per-step status entries. The renderer produces an arrow chain: `unpack ✓ → assembly ✓ → calibration ⏳ → testing ○`. Five glyphs for five statuses. Building the test data for this surfaced a Jackson gotcha: `putPOJO("dependsOn", List.of())` creates a `POJONode`, not an `ArrayNode`. It serialises identically but can't be deserialised back. The tests passed for percentage and count (no array fields) and silently fell back to status-only rendering for steps. `node.set("key", mapper.createArrayNode())` is the fix.

The pattern that's emerging across blocks' conversation package is worth naming: supplementary render-time inputs are how the renderer stays extensible without the projection growing. `ConversationState` captures what the fold produces from the message stream. Everything else — reactions from qhorus, epistemic analysis, convergence detection, and now structured progress — arrives at render time as a snapshot the caller assembles. The projection is bounded by active state; the renderer is bounded by what the caller provides. Four inputs now, and the record is still readable. A fifth would be worth reconsidering the approach — a builder or a map — but four is fine.
