# io.casehub.blocks.conversation.RenderContext

**Package:** `io.casehub.blocks.conversation`

**Kind:** `record`

## Fields

### `EMPTY` (`io.casehub.blocks.conversation.RenderContext`)

### `commonGround` (`io.casehub.blocks.conversation.CommonGroundState`)

### `convergence` (`io.casehub.blocks.conversation.ConvergenceSignal`)

### `progress` (`java.util.Map<java.lang.String,java.util.List<ProgressInstance>>`)

### `reactions` (`java.util.Map<java.lang.Long,java.util.List<ReactionGroup>>`)

## Record Components

### `commonGround` (`io.casehub.blocks.conversation.CommonGroundState`)

### `convergence` (`io.casehub.blocks.conversation.ConvergenceSignal`)

### `progress` (`java.util.Map<java.lang.String,java.util.List<ProgressInstance>>`)

### `reactions` (`java.util.Map<java.lang.Long,java.util.List<ReactionGroup>>`)

## Constructors

### `public RenderContext(java.util.Map<java.lang.Long,java.util.List<ReactionGroup>> reactions, io.casehub.blocks.conversation.CommonGroundState commonGround, io.casehub.blocks.conversation.ConvergenceSignal convergence, java.util.Map<java.lang.String,java.util.List<ProgressInstance>> progress)`

#### Parameters

- `reactions` (`java.util.Map<java.lang.Long,java.util.List<ReactionGroup>>`)
- `commonGround` (`io.casehub.blocks.conversation.CommonGroundState`)
- `convergence` (`io.casehub.blocks.conversation.ConvergenceSignal`)
- `progress` (`java.util.Map<java.lang.String,java.util.List<ProgressInstance>>`)

## Methods

### `public io.casehub.blocks.conversation.CommonGroundState commonGround()`

### `public io.casehub.blocks.conversation.ConvergenceSignal convergence()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.util.Map<java.lang.String,java.util.List<ProgressInstance>> progress()`

### `public java.util.Map<java.lang.Long,java.util.List<ReactionGroup>> reactions()`

### `public final java.lang.String toString()`

### `public static io.casehub.blocks.conversation.RenderContext withProgress(java.util.Map<java.lang.String,java.util.List<ProgressInstance>> progress)`

#### Parameters

- `progress` (`java.util.Map<java.lang.String,java.util.List<ProgressInstance>>`)

### `public static io.casehub.blocks.conversation.RenderContext withReactions(java.util.Map<java.lang.Long,java.util.List<ReactionGroup>> reactions)`

#### Parameters

- `reactions` (`java.util.Map<java.lang.Long,java.util.List<ReactionGroup>>`)
