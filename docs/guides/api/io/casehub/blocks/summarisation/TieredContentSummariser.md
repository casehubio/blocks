# io.casehub.blocks.summarisation.TieredContentSummariser

**Package:** `io.casehub.blocks.summarisation`

**Kind:** `class`

## Fields

### `large` (`io.casehub.blocks.summarisation.ContentSummariser<T>`)

### `medium` (`io.casehub.blocks.summarisation.ContentSummariser<T>`)

### `mediumThreshold` (`int`)

### `small` (`io.casehub.blocks.summarisation.ContentSummariser<T>`)

### `smallThreshold` (`int`)

## Constructors

### `public TieredContentSummariser(io.casehub.blocks.summarisation.ContentSummariser<T> small, io.casehub.blocks.summarisation.ContentSummariser<T> large, int smallThreshold)`

#### Parameters

- `small` (`io.casehub.blocks.summarisation.ContentSummariser<T>`)
- `large` (`io.casehub.blocks.summarisation.ContentSummariser<T>`)
- `smallThreshold` (`int`)

### `public TieredContentSummariser(io.casehub.blocks.summarisation.ContentSummariser<T> small, io.casehub.blocks.summarisation.ContentSummariser<T> medium, io.casehub.blocks.summarisation.ContentSummariser<T> large, int smallThreshold, int mediumThreshold)`

#### Parameters

- `small` (`io.casehub.blocks.summarisation.ContentSummariser<T>`)
- `medium` (`io.casehub.blocks.summarisation.ContentSummariser<T>`)
- `large` (`io.casehub.blocks.summarisation.ContentSummariser<T>`)
- `smallThreshold` (`int`)
- `mediumThreshold` (`int`)

## Methods

### `public java.util.concurrent.CompletionStage<SummaryResult> summarise(java.util.List<T> items, SummaryResult previous)`

#### Parameters

- `items` (`java.util.List<T>`)
- `previous` (`SummaryResult`)
