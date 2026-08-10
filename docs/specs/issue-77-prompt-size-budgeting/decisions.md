## D1: Budget location — engine-api assembler vs blocks wrapper

**Choice:** Modify `RoutingPromptAssembler` in engine-api (approach A)
**Alternatives:**
- Budget-aware wrapper in blocks — no engine change needed, but budget is a core assembly concern
**Rationale:** Budget is a natural extension of the assembler's priority-ordered rendering. Every consumer benefits.
**Trade-offs:** Cross-repo change required (engine#891, now landed)
**Exploration:** quick
**Status:** captured

## D2: Budget unit — chars vs tokens

**Choice:** Character count with configurable limit
**Alternatives:**
- Token count — accurate but requires tokenizer dependency
**Rationale:** Chars are a good-enough proxy (~4:1 ratio). Avoids tokenizer dependency in engine-api.
**Trade-offs:** Slight imprecision vs actual token count
**Exploration:** quick
**Status:** captured

## D3: Overflow behavior — skip vs break

**Choice:** Skip (continue to next section) — greedy fill by priority
**Alternatives:**
- Break (stop at first overflow) — simpler but wastes budget when a large section is followed by a small one
**Rationale:** Greedy fill maximizes information density within budget
**Trade-offs:** Slightly more complex loop, but trivial
**Exploration:** quick
**Status:** captured
