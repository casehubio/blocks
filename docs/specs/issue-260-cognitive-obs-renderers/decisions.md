## D1: Input types — blocks-native, not MindMapNode

**Choice:** Use blocks-native types for all four renderers. Consumer maps MindMapNode → blocks types at the call site.
**Alternatives:**
- MindMapNode directly — couples rendering layer to neocortex internals; rendering must know property key conventions
- Thin wrapper records (BeliefView, PrincipleView) — maximum decoupling but more types to maintain without clear benefit
**Rationale:** Same pattern as existing sections (AgentGoal, Memory, DriveProfile). Blocks renders; consumer maps. SocialAvatarCognition already does this mapping for goals and memories.
**Trade-offs:** Consumer must write ~5 lines of mapping per type. Acceptable — mapping is trivial and keeps the rendering layer clean.
**Sources:** CognitiveObservationSections.java, SocialAvatarCognition pattern, MindMapNode.java
**Exploration:** quick
**Status:** captured

## D2: Trust input type — new TrustSummary record

**Choice:** Create `record TrustSummary(String subjectName, TrustLevel level, @Nullable String reason)` with `enum TrustLevel { HIGH, MODERATE, LOW, UNKNOWN }`. Lives in the affordance package alongside CognitiveObservationSections.
**Alternatives:**
- Generic Pair<String, String> — weakly typed, consumers must know string conventions
- MindMapNode directly for trust only — couples to neocortex for one method
**Rationale:** Purpose-built for rendering. Minimal record, clear semantics, consistent with blocks-native approach from D1.
**Trade-offs:** Two new types (record + enum). Both are trivial and live close to their single consumer.
**Sources:** Issue #260 spec ("Per-relationship trust levels, HIGH/MODERATE/LOW per character")
**Exploration:** quick
**Depends on:** D1 (blocks-native type approach)
**Status:** captured

## D3: Principle input type — text + optional category

**Choice:** Create `record Principle(String text, @Nullable String category)`. Renders as `[Category] text` when category present, plain text otherwise.
**Alternatives:**
- Text only (List<String>) — simplest but limits future grouping
- Text + category + source — overengineered for a rendering input
**Rationale:** Minimal but allows grouping by domain (e.g., "Honesty", "Loyalty"). Category is optional so consumers can omit it.
**Trade-offs:** One new type. Could have reused List<String> but category grouping is worth it for readability.
**Sources:** Issue #260 spec ("Stable behavioral rules — character values/constraints")
**Exploration:** quick
**Depends on:** D1 (blocks-native type approach)
**Status:** captured

## D4: Belief revision markers — Set parameter, not Belief mutation

**Choice:** `beliefsSection(List<Belief<?>>, Set<String> revisedKeys)`. Consumer determines revision status externally; Belief<T> unchanged.
**Alternatives:**
- Wrap in BeliefView(Belief<?>, boolean revised) — more structured but adds another type for no clear benefit
**Rationale:** Belief<T> is an AGM belief revision type, not a rendering type. Adding a `revised` flag would conflate concerns. The Set<String> parameter is simple and keeps Belief clean.
**Trade-offs:** Consumer must build the set. Trivial — compare updatedAt against a threshold.
**Sources:** BeliefSet.java, Belief.java (AGM revision semantics)
**Exploration:** quick
**Depends on:** D1 (blocks-native type approach)
**Status:** captured
