# io.casehub.blocks.prompt.runtime.InMemoryPromptVariantStore

**Package:** `io.casehub.blocks.prompt.runtime`

**Kind:** `class`

## Fields

### `activeSlots` (`java.util.Map<java.lang.String,java.util.Map<java.lang.String,io.casehub.blocks.prompt.PromptVariant>>`)

### `history` (`java.util.Map<java.lang.String,java.util.List<io.casehub.blocks.prompt.PromptVariant>>`)

## Constructors

### `public InMemoryPromptVariantStore()`

## Methods

### `public void activate(java.lang.String signatureId, java.lang.String variantId, java.lang.String variantSlot)`

#### Parameters

- `signatureId` (`java.lang.String`)
- `variantId` (`java.lang.String`)
- `variantSlot` (`java.lang.String`)

### `public io.casehub.blocks.prompt.PromptVariant getActive(java.lang.String signatureId, java.lang.String variantSlot)`

#### Parameters

- `signatureId` (`java.lang.String`)
- `variantSlot` (`java.lang.String`)

### `public java.util.List<io.casehub.blocks.prompt.PromptVariant> getHistory(java.lang.String signatureId, int limit)`

#### Parameters

- `signatureId` (`java.lang.String`)
- `limit` (`int`)

### `public void store(io.casehub.blocks.prompt.PromptVariant variant)`

#### Parameters

- `variant` (`io.casehub.blocks.prompt.PromptVariant`)
