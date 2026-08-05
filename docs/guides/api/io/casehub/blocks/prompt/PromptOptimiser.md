# io.casehub.blocks.prompt.PromptOptimiser

**Package:** `io.casehub.blocks.prompt`

**Kind:** `interface`

## Methods

### `public abstract java.lang.String id()`

### `public abstract java.util.concurrent.CompletionStage<io.casehub.blocks.prompt.OptimiserResult> optimise(io.casehub.blocks.prompt.PromptSignature signature, io.casehub.blocks.prompt.PromptVariant currentVariant, io.casehub.blocks.prompt.OptimisationDataset dataset, io.casehub.blocks.prompt.OptimiserConfig config)`

#### Parameters

- `signature` (`io.casehub.blocks.prompt.PromptSignature`)
- `currentVariant` (`io.casehub.blocks.prompt.PromptVariant`)
- `dataset` (`io.casehub.blocks.prompt.OptimisationDataset`)
- `config` (`io.casehub.blocks.prompt.OptimiserConfig`)
