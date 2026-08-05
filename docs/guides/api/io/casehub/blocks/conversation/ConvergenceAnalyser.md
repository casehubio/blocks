# io.casehub.blocks.conversation.ConvergenceAnalyser

**Package:** `io.casehub.blocks.conversation`

**Kind:** `class`

## Constructors

### `private ConvergenceAnalyser()`

## Methods

### `public static io.casehub.blocks.conversation.ConvergenceSignal analyse(io.casehub.blocks.conversation.ConversationState state, io.casehub.blocks.conversation.CommonGroundState commonGround, io.casehub.blocks.conversation.ConvergencePolicy policy, int recentWindow)`

#### Parameters

- `state` (`io.casehub.blocks.conversation.ConversationState`)
- `commonGround` (`io.casehub.blocks.conversation.CommonGroundState`)
- `policy` (`io.casehub.blocks.conversation.ConvergencePolicy`)
- `recentWindow` (`int`)

### `static io.casehub.blocks.conversation.ConvergenceContext buildContext(io.casehub.blocks.conversation.ConversationState state, io.casehub.blocks.conversation.CommonGroundState commonGround, int recentWindow)`

#### Parameters

- `state` (`io.casehub.blocks.conversation.ConversationState`)
- `commonGround` (`io.casehub.blocks.conversation.CommonGroundState`)
- `recentWindow` (`int`)

### `private static double computeLengthTrend(java.util.List<io.casehub.blocks.conversation.ThreadEntry> all, java.util.List<io.casehub.blocks.conversation.ThreadEntry> recent)`

#### Parameters

- `all` (`java.util.List<io.casehub.blocks.conversation.ThreadEntry>`)
- `recent` (`java.util.List<io.casehub.blocks.conversation.ThreadEntry>`)

### `private static double computeSimilarity(java.util.List<io.casehub.blocks.conversation.ThreadEntry> recent)`

#### Parameters

- `recent` (`java.util.List<io.casehub.blocks.conversation.ThreadEntry>`)

### `private static java.util.List<io.casehub.blocks.conversation.ThreadEntry> flattenAndSort(io.casehub.blocks.conversation.ConversationState state)`

#### Parameters

- `state` (`io.casehub.blocks.conversation.ConversationState`)

### `static double jaccardSimilarity(java.util.Set<java.lang.String> a, java.util.Set<java.lang.String> b)`

#### Parameters

- `a` (`java.util.Set<java.lang.String>`)
- `b` (`java.util.Set<java.lang.String>`)

### `private static int lastNewPointRound(io.casehub.blocks.conversation.ConversationState state)`

#### Parameters

- `state` (`io.casehub.blocks.conversation.ConversationState`)

### `private static int lastStatusChangeRound(io.casehub.blocks.conversation.ConversationState state, io.casehub.blocks.conversation.CommonGroundState commonGround)`

#### Parameters

- `state` (`io.casehub.blocks.conversation.ConversationState`)
- `commonGround` (`io.casehub.blocks.conversation.CommonGroundState`)

### `static java.util.Set<java.lang.String> tokenize(java.lang.String text)`

#### Parameters

- `text` (`java.lang.String`)
