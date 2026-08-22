## D1: Tick lifecycle wiring strategy

**Choice:** Inject DriveOrchestrator into InnerLifeOrchestrator
**Alternatives:**
- Consumer-wired plain class — loosely coupled but pushes wiring burden to every consumer
- New SocialCognitionScheduler coordinator — more general but adds a concept for one integration point
**Rationale:** InnerLifeOrchestrator is the existing CDI-managed tick entry point. Drives must be computed before inner life runs (ordering constraint from the design spec). Wiring inside InnerLifeOrchestrator satisfies "wired into the agent tick lifecycle" without consumer changes. Making DriveOrchestrator CDI-managed is the prerequisite; its four drive sources and DriveComposer follow mechanically (source orchestrators are already CDI-managed). The issue scopes out InnerLife MotivationAssessment *integration* (using drive data for motivation scoring) but includes lifecycle *wiring* (calling tick) — these are distinct.
**Trade-offs:** Couples DriveOrchestrator lifecycle to InnerLifeOrchestrator — consumers that want drives without inner life can't get automatic ticking. Acceptable because drives without inner life is not a current use case, and consumers can always call tick() directly.
**Sources:** InnerLifeOrchestrator.java, DriveOrchestrator.java, issue #129 design spec (ordering constraint), issue #149 (scope: "In scope: tick lifecycle wiring. Out of scope: InnerLife MotivationAssessment integration")
**Exploration:** quick
**Status:** captured
