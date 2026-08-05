# io.casehub.blocks.routing.agent.CbrRoutingPromptSection

**Package:** `io.casehub.blocks.routing.agent`

**Kind:** `class`

`RoutingPromptSection` that renders historical CBR evidence for LLM routing prompts.

<p>Reads pre-retrieved experiences from `AgentRoutingContext.experiences()` and formats
agent outcome statistics and case details for the LLM to consider during routing.

## Constructors

### `public CbrRoutingPromptSection()`

## Methods

### `private java.lang.String format(java.util.List<RetrievedExperience> experiences, java.util.Set<java.lang.String> eligibleIds, java.lang.String capabilityName)`

#### Parameters

- `experiences` (`java.util.List<RetrievedExperience>`)
- `eligibleIds` (`java.util.Set<java.lang.String>`)
- `capabilityName` (`java.lang.String`)

### `public java.lang.String render(AgentRoutingContext context, java.util.List<AgentCandidate> eligible)`

#### Parameters

- `context` (`AgentRoutingContext`)
- `eligible` (`java.util.List<AgentCandidate>`)

### `private static java.lang.String truncate(java.lang.String s, int max)`

#### Parameters

- `s` (`java.lang.String`)
- `max` (`int`)
