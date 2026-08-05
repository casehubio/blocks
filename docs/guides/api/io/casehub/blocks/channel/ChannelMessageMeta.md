# io.casehub.blocks.channel.ChannelMessageMeta

**Package:** `io.casehub.blocks.channel`

**Kind:** `class`

Encodes and decodes structured key=value metadata headers in channel message bodies.

<p>Format: `SENTINEL + "key=value|key=value\n\nbody"`. Each application chooses
its own sentinel prefix (e.g. `"DHMETA:"`) — the utility handles the format.
The SOH byte (U+0001) as the first character of the sentinel is recommended: LLM output
never begins with SOH, eliminating ambiguity between structured headers and plain text.

## Constructors

### `private ChannelMessageMeta()`

## Methods

### `public static java.lang.String bodyContent(java.lang.String sentinel, java.lang.String content)`

Strip the sentinel header and return only the body text.
Returns content unchanged if sentinel is absent. Returns null if input is null.

#### Parameters

- `sentinel` (`java.lang.String`)
- `content` (`java.lang.String`)

### `public static java.lang.String encode(java.lang.String sentinel, java.util.Map<java.lang.String,java.lang.String> meta, java.lang.String body)`

Encode a sentinel-prefixed message from metadata and body.

#### Parameters

- `sentinel` (`java.lang.String`)
- `meta` (`java.util.Map<java.lang.String,java.lang.String>`)
- `body` (`java.lang.String`)

### `public static int parseInt(java.util.Map<java.lang.String,java.lang.String> meta, java.lang.String key)`

Parse an integer field from a pre-parsed meta map.
Returns 0 if the field is absent or not a valid integer.

#### Parameters

- `meta` (`java.util.Map<java.lang.String,java.lang.String>`)
- `key` (`java.lang.String`)

### `public static java.util.Map<java.lang.String,java.lang.String> parseMeta(java.lang.String sentinel, java.lang.String content)`

Parse a sentinel-prefixed header from message content.
Returns empty map if the sentinel is absent (plain content — not an error).

#### Parameters

- `sentinel` (`java.lang.String`)
- `content` (`java.lang.String`)
