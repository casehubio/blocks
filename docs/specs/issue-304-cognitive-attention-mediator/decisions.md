## D1: Mediator module placement

**Choice:** blocks-core — alongside CognitionCore
**Alternatives:**
- blocks module — conventional CDI location, but splits attention code across two modules
**Rationale:** Keeps the attention subsystem self-contained. blocks-core already uses CDI annotations (SocialCognitionDefaultBeans).
**Trade-offs:** blocks-core gains another CDI bean, but it already has several.
**Sources:** SocialCognitionDefaultBeans.java, BlocksBeans.java
**Exploration:** quick
**Status:** captured

## D2: CognitionConfig attentionEnabled flag

**Choice:** Add `attentionEnabled` boolean to CognitionConfig
**Alternatives:**
- No flag — rely on mediator nullability as the gate
**Rationale:** Consistency with all other subsystems (moodEnabled, drivesEnabled, etc.). Lets operators disable attention without removing the mediator from classpath.
**Trade-offs:** One more boolean in the record.
**Refinement (light review):** Always drain the mediator queue in tick() regardless of config (prevents unbounded queue growth when disabled). Only set lastBriefing when attentionEnabled=true. Drain-and-discard when disabled.
**Sources:** CognitionConfig.java (existing pattern)
**Exploration:** quick
**Status:** captured

## D3: Section gating override — AttentionRelevance with typed EnumSets

**Choice:** Reverse mapping via `AttentionRelevance` utility class. Define subsystem → relevant signals as `EnumSet<SignalCategory>` constants. CognitionCore uses `isEnabled(configFlag, relevanceSet)` helper.
**Alternatives:**
- Hard-coded switch in promptSections() — scattered, scales poorly
- EnumMap<SignalCategory, UnaryOperator<CognitionConfig>> "effective config" — centralized but string-based subsystem names, not compile-time type-safe, creates N CognitionConfig copies
**Rationale:** Fully type-safe (enum values only, no strings), centralized (constants are the mapping), clean at call site (`isEnabled(flag, GOALS)`), testable (pure static utility with synthetic briefings), efficient (EnumSet O(1) bitwise). The reverse direction ("which signals are relevant to goals?") is more natural than ("which subsystem does URGENCY_SPIKE override?").
**Trade-offs:** One new utility class. If a runtime needs custom overrides (e.g., wacky-manor wants AFFECT_CHANGE to also enable narrative), requires code change — but runtime customization is deferred to follow-up (spec §9).
**Sources:** neocortex spec §7.5, SignalCategory.java, CognitionConfig.java
**Exploration:** deep-analysis
**Status:** captured
