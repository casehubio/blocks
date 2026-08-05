# io.casehub.blocks.agentic.termination.ConvergenceTermination

**Package:** `io.casehub.blocks.agentic.termination`

**Kind:** `class`

## Fields

### `confidenceThreshold` (`double`)

### `epistemicRule` (`io.casehub.blocks.conversation.EpistemicRule`)

### `policy` (`io.casehub.blocks.conversation.ConvergencePolicy`)

### `recentWindow` (`int`)

### `stateExtractor` (`java.util.function.Function<T,io.casehub.blocks.conversation.ConversationState>`)

### `terminateOn` (`java.util.Set<io.casehub.blocks.conversation.ConvergenceState>`)

## Constructors

### `public ConvergenceTermination(java.util.function.Function<T,io.casehub.blocks.conversation.ConversationState> stateExtractor, io.casehub.blocks.conversation.EpistemicRule epistemicRule, io.casehub.blocks.conversation.ConvergencePolicy policy, int recentWindow, double confidenceThreshold, java.util.Set<io.casehub.blocks.conversation.ConvergenceState> terminateOn)`

#### Parameters

- `stateExtractor` (`java.util.function.Function<T,io.casehub.blocks.conversation.ConversationState>`)
- `epistemicRule` (`io.casehub.blocks.conversation.EpistemicRule`)
- `policy` (`io.casehub.blocks.conversation.ConvergencePolicy`)
- `recentWindow` (`int`)
- `confidenceThreshold` (`double`)
- `terminateOn` (`java.util.Set<io.casehub.blocks.conversation.ConvergenceState>`)

## Methods

### `public Uni<io.casehub.blocks.agentic.termination.TerminationDecision> evaluate(io.casehub.blocks.agentic.termination.TerminationContext<T> context)`

#### Parameters

- `context` (`io.casehub.blocks.agentic.termination.TerminationContext<T>`)

### `private io.casehub.blocks.agentic.termination.TerminationDecision toDecision(io.casehub.blocks.conversation.ConvergenceSignal signal)`

#### Parameters

- `signal` (`io.casehub.blocks.conversation.ConvergenceSignal`)
