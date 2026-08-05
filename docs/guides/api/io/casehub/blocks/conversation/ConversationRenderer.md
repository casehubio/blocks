# io.casehub.blocks.conversation.ConversationRenderer

**Package:** `io.casehub.blocks.conversation`

**Kind:** `class`

## Fields

### `DEFAULT_EMOJI` (`java.lang.String`)

### `config` (`io.casehub.blocks.conversation.ConversationRendererConfig`)

### `progressRenderer` (`io.casehub.blocks.conversation.ProgressRenderer`)

## Constructors

### `public ConversationRenderer(io.casehub.blocks.conversation.ConversationRendererConfig config)`

#### Parameters

- `config` (`io.casehub.blocks.conversation.ConversationRendererConfig`)

### `public ConversationRenderer(io.casehub.blocks.conversation.ConversationRendererConfig config, io.casehub.blocks.conversation.ProgressRenderer progressRenderer)`

#### Parameters

- `config` (`io.casehub.blocks.conversation.ConversationRendererConfig`)
- `progressRenderer` (`io.casehub.blocks.conversation.ProgressRenderer`)

## Methods

### `public java.lang.String render(io.casehub.blocks.conversation.ConversationState state)`

#### Parameters

- `state` (`io.casehub.blocks.conversation.ConversationState`)

### `public java.lang.String render(io.casehub.blocks.conversation.ConversationState state, io.casehub.blocks.conversation.RenderContext ctx)`

#### Parameters

- `state` (`io.casehub.blocks.conversation.ConversationState`)
- `ctx` (`io.casehub.blocks.conversation.RenderContext`)

### `private void renderByTopic(java.lang.StringBuilder sb, io.casehub.blocks.conversation.ConversationState state, io.casehub.blocks.conversation.RenderContext ctx)`

#### Parameters

- `sb` (`java.lang.StringBuilder`)
- `state` (`io.casehub.blocks.conversation.ConversationState`)
- `ctx` (`io.casehub.blocks.conversation.RenderContext`)

### `private void renderEpistemicBadge(java.lang.StringBuilder sb, io.casehub.blocks.conversation.ConversationPoint point, io.casehub.blocks.conversation.CommonGroundState cg)`

#### Parameters

- `sb` (`java.lang.StringBuilder`)
- `point` (`io.casehub.blocks.conversation.ConversationPoint`)
- `cg` (`io.casehub.blocks.conversation.CommonGroundState`)

### `private java.lang.String renderFinding(io.casehub.blocks.conversation.SubTaskFinding f)`

#### Parameters

- `f` (`io.casehub.blocks.conversation.SubTaskFinding`)

### `private void renderFlat(java.lang.StringBuilder sb, io.casehub.blocks.conversation.ConversationState state, io.casehub.blocks.conversation.RenderContext ctx)`

#### Parameters

- `sb` (`java.lang.StringBuilder`)
- `state` (`io.casehub.blocks.conversation.ConversationState`)
- `ctx` (`io.casehub.blocks.conversation.RenderContext`)

### `private java.lang.String renderObligationChain(java.util.List<io.casehub.blocks.conversation.ConversationPoint> points)`

#### Parameters

- `points` (`java.util.List<io.casehub.blocks.conversation.ConversationPoint>`)

### `private void renderPoints(java.lang.StringBuilder sb, java.util.List<io.casehub.blocks.conversation.ConversationPoint> points, io.casehub.blocks.conversation.ConversationState state, boolean strikethrough, io.casehub.blocks.conversation.RenderContext ctx)`

#### Parameters

- `sb` (`java.lang.StringBuilder`)
- `points` (`java.util.List<io.casehub.blocks.conversation.ConversationPoint>`)
- `state` (`io.casehub.blocks.conversation.ConversationState`)
- `strikethrough` (`boolean`)
- `ctx` (`io.casehub.blocks.conversation.RenderContext`)
