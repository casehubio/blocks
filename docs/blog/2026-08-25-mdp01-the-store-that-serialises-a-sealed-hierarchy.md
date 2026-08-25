---
layout: post
title: "The Store That Serialises a Sealed Hierarchy"
date: 2026-08-25
entry_type: note
subtype: diary
projects: [casehubio/blocks]
tags: [narrative-identity, cbr, social-emergence, drive-modulation]
series: issue-142-narrative-identity
---

# The Store That Serialises a Sealed Hierarchy

An agent's narrative — episodes, group experiences, themes — lives in a sealed type hierarchy. Three subtypes, each with different fields: emotional valence on episodes, consensus levels on group events, drive modulation weights on themes. Storing this in a flat key-value CBR feature map required a decision.

The existing stores (UserProfile, MentalModel, Strategy) all serialise flat records. Each field maps to a `FeatureValue`. NarrativeState is different: a `List<NarrativeFragment>` where each element could be an `IndividualEpisode`, a `GroupEpisode`, or a `DerivedTheme`. The CBR feature model doesn't support nested objects.

We went with hand-rolled JSON in a single string feature, using type discriminators — `"type":"episode"`, `"type":"group_episode"`, `"type":"theme"` — and pattern-matched dispatch on deserialization. The depth-based brace-counting parser from `MentalModelSchema` extended naturally to handle three polymorphic subtypes with nested collections (sets, maps, enum-keyed maps). Not elegant, but consistent with the codebase and dependency-free.

The narrative feedback loop was the satisfying wiring. `DriveComposer.compose()` already accepted a `@Nullable Map<DriveAxis, Double>` for narrative modulation — added speculatively in the drive architecture. All that was needed: inject `NarrativeOrchestrator` into `DriveOrchestrator` via `Instance<>`, read the current narrative, call `NarrativeModulation.compute()`, and pass the result instead of `null`. One parameter that was always intended to be filled.

The norm detector was a different kind of problem. Social norms emerge from repeated multi-agent interaction — "verify before escalating" becomes a norm not because anyone programmed it, but because agents consistently follow it. The `SocialNormDetector` reads `NormObservation` cases from CBR, groups by behavioural pattern, and classifies strength: EMERGING when the pattern appears but adherence is inconsistent, ESTABLISHED when it crosses a threshold, DECLINING when a previously-established norm starts to fade. That last transition — DECLINING — requires memory of the previous state, which is why the detector caches per-tenant and compares across ticks.

Three layers of the social cognition stack now connect: narrative identity shapes drive intensity, which shapes goal proposals. An agent whose self-story includes "I help people through crises" will have amplified affiliation and competence drives in crisis contexts. The architecture was designed for this — the modulation parameter existed before the narrative system did. Filling it in was a single method reference: `NarrativeModulation::compute`.
