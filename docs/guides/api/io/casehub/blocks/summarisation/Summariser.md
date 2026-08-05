# io.casehub.blocks.summarisation.Summariser

**Package:** `io.casehub.blocks.summarisation`

**Kind:** `interface`

## Methods

### `public static io.casehub.blocks.summarisation.Summariser<IN,OUT> ofSync(io.casehub.blocks.summarisation.Summariser.SyncSummariser<IN,OUT> sync)`

#### Parameters

- `sync` (`io.casehub.blocks.summarisation.Summariser.SyncSummariser<IN,OUT>`)

### `public abstract java.util.concurrent.CompletionStage<java.util.List<OUT>> summarise(java.util.List<io.casehub.blocks.summarisation.LevelEvent<IN>> batch)`

#### Parameters

- `batch` (`java.util.List<io.casehub.blocks.summarisation.LevelEvent<IN>>`)
