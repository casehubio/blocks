# io.casehub.blocks.prompt.runtime.OptimisedFewShotSection

**Package:** `io.casehub.blocks.prompt.runtime`

**Kind:** `class`

## Fields

### `selector` (`io.casehub.blocks.prompt.VariantSelector`)

### `signatureId` (`java.lang.String`)

### `store` (`io.casehub.blocks.prompt.PromptVariantStore`)

## Constructors

### `public OptimisedFewShotSection(io.casehub.blocks.prompt.PromptVariantStore store, io.casehub.blocks.prompt.VariantSelector selector, java.lang.String signatureId)`

#### Parameters

- `store` (`io.casehub.blocks.prompt.PromptVariantStore`)
- `selector` (`io.casehub.blocks.prompt.VariantSelector`)
- `signatureId` (`java.lang.String`)

## Methods

### `private java.lang.String formatExamples(java.util.List<io.casehub.blocks.prompt.FewShotExample> examples)`

#### Parameters

- `examples` (`java.util.List<io.casehub.blocks.prompt.FewShotExample>`)

### `public java.lang.String render(AgentRoutingContext context, java.util.List<AgentCandidate> eligible)`

#### Parameters

- `context` (`AgentRoutingContext`)
- `eligible` (`java.util.List<AgentCandidate>`)
