# io.casehub.blocks.summarisation.WindowPolicy

**Package:** `io.casehub.blocks.summarisation`

**Kind:** `record`

## Fields

### `maxAge` (`long`)

### `maxCount` (`int`)

## Record Components

### `maxAge` (`long`)

### `maxCount` (`int`)

## Constructors

### `public WindowPolicy(long maxAge, int maxCount)`

#### Parameters

- `maxAge` (`long`)
- `maxCount` (`int`)

## Methods

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public long maxAge()`

### `public int maxCount()`

### `public static io.casehub.blocks.summarisation.WindowPolicy of(long maxAgeMs, int maxCount)`

#### Parameters

- `maxAgeMs` (`long`)
- `maxCount` (`int`)

### `public static io.casehub.blocks.summarisation.WindowPolicy ofAge(long maxAgeMs)`

#### Parameters

- `maxAgeMs` (`long`)

### `public static io.casehub.blocks.summarisation.WindowPolicy ofCount(int maxCount)`

#### Parameters

- `maxCount` (`int`)

### `public final java.lang.String toString()`
