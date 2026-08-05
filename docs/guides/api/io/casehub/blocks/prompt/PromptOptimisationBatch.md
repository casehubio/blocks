# io.casehub.blocks.prompt.PromptOptimisationBatch

**Package:** `io.casehub.blocks.prompt`

**Kind:** `class`

## Fields

### `DEFAULT_MIN_PROMOTION_CYCLES` (`int`)

### `DEFAULT_PROMOTION_MARGIN` (`double`)

### `LOG` (`java.lang.System.Logger`)

### `metric` (`io.casehub.blocks.prompt.PromptQualityMetric`)

### `optimisers` (`java.util.List<io.casehub.blocks.prompt.PromptOptimiser>`)

### `runningSignatures` (`java.util.Set<java.lang.String>`)

### `safetyConfig` (`io.casehub.blocks.prompt.SafetyConfig`)

### `store` (`io.casehub.blocks.prompt.PromptVariantStore`)

## Constructors

### `public PromptOptimisationBatch(java.util.List<io.casehub.blocks.prompt.PromptOptimiser> optimisers, io.casehub.blocks.prompt.PromptQualityMetric metric, io.casehub.blocks.prompt.PromptVariantStore store, io.casehub.blocks.prompt.SafetyConfig safetyConfig)`

#### Parameters

- `optimisers` (`java.util.List<io.casehub.blocks.prompt.PromptOptimiser>`)
- `metric` (`io.casehub.blocks.prompt.PromptQualityMetric`)
- `store` (`io.casehub.blocks.prompt.PromptVariantStore`)
- `safetyConfig` (`io.casehub.blocks.prompt.SafetyConfig`)

## Methods

### `private java.util.concurrent.CompletionStage<io.casehub.blocks.prompt.BatchResult> doRun(io.casehub.blocks.prompt.PromptSignature signature, io.casehub.blocks.prompt.OptimisationDataset dataset, io.casehub.blocks.prompt.OptimiserConfig config)`

#### Parameters

- `signature` (`io.casehub.blocks.prompt.PromptSignature`)
- `dataset` (`io.casehub.blocks.prompt.OptimisationDataset`)
- `config` (`io.casehub.blocks.prompt.OptimiserConfig`)

### `public java.util.concurrent.CompletionStage<io.casehub.blocks.prompt.BatchResult> run(io.casehub.blocks.prompt.PromptSignature signature, io.casehub.blocks.prompt.OptimisationDataset dataset, io.casehub.blocks.prompt.OptimiserConfig config)`

#### Parameters

- `signature` (`io.casehub.blocks.prompt.PromptSignature`)
- `dataset` (`io.casehub.blocks.prompt.OptimisationDataset`)
- `config` (`io.casehub.blocks.prompt.OptimiserConfig`)

### `private java.util.concurrent.CompletionStage<io.casehub.blocks.prompt.@Nullable PromptVariant> runOptimisersAndCreateVariant(io.casehub.blocks.prompt.PromptSignature signature, io.casehub.blocks.prompt.OptimisationDataset dataset, io.casehub.blocks.prompt.OptimiserConfig config, io.casehub.blocks.prompt.PromptVariant parent)`

#### Parameters

- `signature` (`io.casehub.blocks.prompt.PromptSignature`)
- `dataset` (`io.casehub.blocks.prompt.OptimisationDataset`)
- `config` (`io.casehub.blocks.prompt.OptimiserConfig`)
- `parent` (`io.casehub.blocks.prompt.PromptVariant`)

### `private double scoreVariant(io.casehub.blocks.prompt.PromptVariant variant, java.util.Map<java.lang.String,java.util.List<io.casehub.blocks.prompt.VariantOutcome>> byVariant)`

#### Parameters

- `variant` (`io.casehub.blocks.prompt.PromptVariant`)
- `byVariant` (`java.util.Map<java.lang.String,java.util.List<io.casehub.blocks.prompt.VariantOutcome>>`)
