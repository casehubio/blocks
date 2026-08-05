# io.casehub.blocks.agentic.decomposition.GoalOrientedDecomposition

**Package:** `io.casehub.blocks.agentic.decomposition`

**Kind:** `class`

## Constructors

### `public GoalOrientedDecomposition()`

## Methods

### `private java.util.List<io.casehub.blocks.agentic.decomposition.GoalOrientedDecomposition.GoapAction> buildActions(java.util.List<io.casehub.blocks.agentic.RoutingCandidate> agents)`

#### Parameters

- `agents` (`java.util.List<io.casehub.blocks.agentic.RoutingCandidate>`)

### `private DagPlan<TaskNode.LeafTask<T>> buildDag(java.util.List<io.casehub.blocks.agentic.decomposition.GoalOrientedDecomposition.GoapAction> selectedActions, java.util.Map<java.lang.String,io.casehub.blocks.agentic.decomposition.GoalOrientedDecomposition.GoapAction> typeProducers, java.util.Set<java.lang.String> availableTypes)`

#### Parameters

- `selectedActions` (`java.util.List<io.casehub.blocks.agentic.decomposition.GoalOrientedDecomposition.GoapAction>`)
- `typeProducers` (`java.util.Map<java.lang.String,io.casehub.blocks.agentic.decomposition.GoalOrientedDecomposition.GoapAction>`)
- `availableTypes` (`java.util.Set<java.lang.String>`)

### `public Uni<DagPlan<TaskNode.LeafTask<T>>> decompose(TaskNode<T> task, DecompositionContext<T> context)`

#### Parameters

- `task` (`TaskNode<T>`)
- `context` (`DecompositionContext<T>`)

### `private static io.casehub.blocks.agentic.decomposition.GoalOrientedDecomposition.GoapAction findProducer(java.lang.String type, java.util.List<io.casehub.blocks.agentic.decomposition.GoalOrientedDecomposition.GoapAction> actions)`

#### Parameters

- `type` (`java.lang.String`)
- `actions` (`java.util.List<io.casehub.blocks.agentic.decomposition.GoalOrientedDecomposition.GoapAction>`)

### `public java.lang.String id()`

### `private DagPlan<TaskNode.LeafTask<T>> plan(java.lang.String taskName, io.casehub.blocks.agentic.decomposition.GoapDecompositionContext<T> ctx)`

#### Parameters

- `taskName` (`java.lang.String`)
- `ctx` (`io.casehub.blocks.agentic.decomposition.GoapDecompositionContext<T>`)
