# io.casehub.blocks.agentic.decomposition.OutputContract

**Package:** `io.casehub.blocks.agentic.decomposition`

**Kind:** `interface`

Declares the expected shape of a task's output, enabling validation gates between tasks.
Failed validation triggers retry at task granularity rather than pipeline restart.

<p>Composable via `.and(OutputContract)`.

## Methods

### `public default io.casehub.blocks.agentic.decomposition.OutputContract and(io.casehub.blocks.agentic.decomposition.OutputContract other)`

#### Parameters

- `other` (`io.casehub.blocks.agentic.decomposition.OutputContract`)

### `public static io.casehub.blocks.agentic.decomposition.OutputContract nonNull()`

### `public static io.casehub.blocks.agentic.decomposition.OutputContract of(java.util.function.Predicate<java.lang.Object> test)`

#### Parameters

- `test` (`java.util.function.Predicate<java.lang.Object>`)

### `public static io.casehub.blocks.agentic.decomposition.OutputContract type(java.lang.Class<?> expected)`

#### Parameters

- `expected` (`java.lang.Class<?>`)

### `public abstract boolean validate(java.lang.Object output)`

#### Parameters

- `output` (`java.lang.Object`)
