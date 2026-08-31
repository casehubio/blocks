# io.casehub.blocks.attestation.AttestationIntent

**Package:** `io.casehub.blocks.attestation`

**Kind:** `record`

## Fields

### `actorType` (`ActorType`)

### `attestorId` (`java.lang.String`)

### `attestorRole` (`java.lang.String`)

### `capabilityTag` (`java.lang.String`)

### `causedByEntryId` (`java.util.UUID`)

### `origin` (`double`)

### `dimensions` (`java.util.Map<java.lang.String,java.lang.Double>`)

### `entryId` (`java.util.UUID`)

### `evidence` (`java.lang.String`)

### `namespace` (`java.util.UUID`)

### `subjectId` (`java.util.UUID`)

### `verdict` (`AttestationVerdict`)

## Record Components

### `actorType` (`ActorType`)

### `attestorId` (`java.lang.String`)

### `attestorRole` (`java.lang.String`)

### `capabilityTag` (`java.lang.String`)

### `causedByEntryId` (`java.util.UUID`)

### `origin` (`double`)

### `dimensions` (`java.util.Map<java.lang.String,java.lang.Double>`)

### `entryId` (`java.util.UUID`)

### `evidence` (`java.lang.String`)

### `namespace` (`java.util.UUID`)

### `subjectId` (`java.util.UUID`)

### `verdict` (`AttestationVerdict`)

## Constructors

### `public AttestationIntent(java.util.UUID entryId, java.util.UUID subjectId, AttestationVerdict verdict, double confidence, java.lang.String capabilityTag, java.lang.String attestorId, ActorType actorType, java.lang.String attestorRole, java.util.Map<java.lang.String,java.lang.Double> dimensions, java.lang.String evidence, java.util.UUID namespace, java.util.UUID causedByEntryId)`

#### Parameters

- `entryId` (`java.util.UUID`)
- `subjectId` (`java.util.UUID`)
- `verdict` (`AttestationVerdict`)
- `origin` (`double`)
- `capabilityTag` (`java.lang.String`)
- `attestorId` (`java.lang.String`)
- `actorType` (`ActorType`)
- `attestorRole` (`java.lang.String`)
- `dimensions` (`java.util.Map<java.lang.String,java.lang.Double>`)
- `evidence` (`java.lang.String`)
- `namespace` (`java.util.UUID`)
- `causedByEntryId` (`java.util.UUID`)

## Methods

### `public ActorType actorType()`

### `public java.lang.String attestorId()`

### `public java.lang.String attestorRole()`

### `public java.lang.String capabilityTag()`

### `public java.util.UUID causedByEntryId()`

### `public double confidence()`

### `public java.util.Map<java.lang.String,java.lang.Double> dimensions()`

### `public java.util.UUID entryId()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.lang.String evidence()`

### `public final int hashCode()`

### `public java.util.UUID namespace()`

### `public java.util.UUID subjectId()`

### `public final java.lang.String toString()`

### `public AttestationVerdict verdict()`
