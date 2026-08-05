# io.casehub.blocks.prompt.optimiser.InstructionOptimiser

**Package:** `io.casehub.blocks.prompt.optimiser`

**Kind:** `class`

## Fields

### `LOG` (`java.lang.System.Logger`)

### `META_SYSTEM_PROMPT` (`java.lang.String`)

### `agentProvider` (`AgentProvider`)

## Constructors

### `public InstructionOptimiser(AgentProvider agentProvider)`

#### Parameters

- `agentProvider` (`AgentProvider`)

## Methods

### `private java.lang.String buildMetaPrompt(io.casehub.blocks.prompt.PromptSignature signature, java.util.List<io.casehub.blocks.prompt.VariantOutcome> outcomes)`

#### Parameters

- `signature` (`io.casehub.blocks.prompt.PromptSignature`)
- `outcomes` (`java.util.List<io.casehub.blocks.prompt.VariantOutcome>`)

### `public java.lang.String id()`

### `public java.util.concurrent.CompletionStage<io.casehub.blocks.prompt.OptimiserResult> optimise(io.casehub.blocks.prompt.PromptSignature signature, io.casehub.blocks.prompt.PromptVariant currentVariant, io.casehub.blocks.prompt.OptimisationDataset dataset, io.casehub.blocks.prompt.OptimiserConfig config)`

#### Parameters

- `signature` (`io.casehub.blocks.prompt.PromptSignature`)
- `currentVariant` (`io.casehub.blocks.prompt.PromptVariant`)
- `dataset` (`io.casehub.blocks.prompt.OptimisationDataset`)
- `config` (`io.casehub.blocks.prompt.OptimiserConfig`)
