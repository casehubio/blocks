# io.casehub.blocks.conversation.DefaultProgressRenderer

**Package:** `io.casehub.blocks.conversation`

**Kind:** `class`

## Fields

### `MAPPER` (`ObjectMapper`)

### `STEP_GLYPHS` (`java.util.Map<StepStatus,java.lang.String>`)

## Constructors

### `public DefaultProgressRenderer()`

## Methods

### `private java.lang.String extractLabel(ProgressInstance pi)`

#### Parameters

- `pi` (`ProgressInstance`)

### `public java.lang.String render(ProgressInstance progress)`

#### Parameters

- `progress` (`ProgressInstance`)

### `private java.lang.String renderCount(java.lang.String label, ProgressInstance pi)`

#### Parameters

- `label` (`java.lang.String`)
- `pi` (`ProgressInstance`)

### `private java.lang.String renderPercentage(java.lang.String label, ProgressInstance pi)`

#### Parameters

- `label` (`java.lang.String`)
- `pi` (`ProgressInstance`)

### `private java.lang.String renderSteps(ProgressInstance pi)`

#### Parameters

- `pi` (`ProgressInstance`)

### `private java.lang.String statusSuffix(ProgressStatus status)`

#### Parameters

- `status` (`ProgressStatus`)
