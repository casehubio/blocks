---
layout: post
title: "The Group That Decides to Want"
date: 2026-08-25
entry_type: note
subtype: diary
projects: [casehubio/blocks]
tags: [social-emergence, collective-goals, drive-alignment, group-identity, narrative-identity]
series: issue-142-narrative-identity
---

# The Group That Decides to Want

Individual agents want things. The drive architecture established that — curiosity, competence, affiliation, autonomy, each with measurable intensity derived from what the agent remembers and how its strategies are performing. But what happens when two agents want similar things?

The question isn't coordination. Coordination is assigned: a supervisor says "you two work together." The question is emergence — can the motivation to collaborate arise from the drives themselves, without anyone assigning it?

The alignment computation is straightforward. For each pair of agents, compare their drive intensities axis by axis. The formula is `1 - |intensityA - intensityB|` per axis — agents with curiosity at 0.8 and 0.7 score 0.9 on that axis. Average across all four axes for a composite. When the composite crosses a threshold, those agents are candidates for a collective goal.

The interesting part is group formation. Pairwise alignment gives you edges in a graph. Connected components give you groups. If A aligns with B and B aligns with C, all three end up in one group — even if A and C don't particularly align. The group's composite alignment is the average of all pairwise scores, so one weak link drags the average down. This is deliberate: a group should share motivation, not just be transitively connected through a broker.

Each group gets a proposal keyed to its dominant shared axis — the axis where alignment is highest and both agents care most. Two agents with high curiosity and high alignment on that axis get a "collective-exploration" proposal. The proposal bridges directly to `JointIntention.form()`, connecting the emergent motivation to the existing commitment lifecycle: form, activate, reconsider, drop, fulfill.

Cooldown prevents the same group from being re-proposed every tick. The key is the sorted agent set per tenant — `tenant-1:agent-a|agent-b` — so adding or removing a member creates a distinct cooldown entry.

The group narrative orchestrator is the other half of the picture. Individual agents already build autobiographies — episodes and themes synthesised from reflections. Groups need the same thing but scoped differently. A `GroupNarrativeOrchestrator` reads from the same `NarrativeStore` with a groupId instead of an agentId, detects new `GroupEpisode`s instead of individual ones, and caches the result per group. The key insight: `NarrativeModulation.compute()` works unchanged on a group's narrative state. Themes carry `axisModulationWeights` regardless of scope — a group theme of "we handle crises well" amplifies the same drives as an individual theme of "I handle crises well."

This closes the feedback loop between individual drives and collective identity. Agents whose drives align form groups. Groups develop shared narratives. Those narratives modulate the drives of group members. The modulation can strengthen the alignment that formed the group in the first place — or, if a theme declines, weaken it. The system is self-reinforcing but not self-sealing: declining themes and shifting drives can dissolve groups that no longer share motivation.

The architecture was speculative when the epic started. Whether emergent group behaviour would compose cleanly with the existing drive and narrative infrastructure was an open question. It did — the types fit, the modulation algebra reused, and the commitment lifecycle bridged without adaptation. The speculation paid off.
