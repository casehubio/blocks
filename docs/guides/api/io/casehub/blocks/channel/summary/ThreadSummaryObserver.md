# io.casehub.blocks.channel.summary.ThreadSummaryObserver

**Package:** `io.casehub.blocks.channel.summary`

**Kind:** `class`

## Fields

### `LOG` (`java.lang.System.Logger`)

### `MAX_THREAD_MESSAGES` (`int`)

### `contentSummariser` (`io.casehub.blocks.summarisation.ContentSummariser<Message>`)

### `executor` (`ManagedExecutor`)

### `inFlight` (`java.util.Set<java.lang.String>`)

### `messageStore` (`CrossTenantMessageStore`)

### `summaryEvents` (`Event<ThreadSummaryUpdatedEvent>`)

### `threadSummaryStore` (`ThreadSummaryStore`)

## Constructors

### `public ThreadSummaryObserver(io.casehub.blocks.summarisation.ContentSummariser<Message> contentSummariser, CrossTenantMessageStore messageStore, ThreadSummaryStore threadSummaryStore, Event<ThreadSummaryUpdatedEvent> summaryEvents)`

#### Parameters

- `contentSummariser` (`io.casehub.blocks.summarisation.ContentSummariser<Message>`)
- `messageStore` (`CrossTenantMessageStore`)
- `threadSummaryStore` (`ThreadSummaryStore`)
- `summaryEvents` (`Event<ThreadSummaryUpdatedEvent>`)

## Methods

### `void onTerminalMessage(MessageReceivedEvent event)`

#### Parameters

- `event` (`MessageReceivedEvent`)

### `void summariseThread(java.util.UUID channelId, java.lang.String correlationId, java.lang.String channelName, java.lang.String tenancyId)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `correlationId` (`java.lang.String`)
- `channelName` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)
