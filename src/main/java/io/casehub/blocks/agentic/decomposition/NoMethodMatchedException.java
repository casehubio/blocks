package io.casehub.blocks.agentic.decomposition;

public class NoMethodMatchedException extends IllegalStateException {

    private final String taskName;
    private final int    methodCount;

    public NoMethodMatchedException(String taskName) {
        this(taskName, 0);
    }

    public NoMethodMatchedException(String taskName, int methodCount) {
        super("No decomposition method guard matched for task '" + taskName
              + "' (" + methodCount + " method(s) evaluated)");
        this.taskName    = taskName;
        this.methodCount = methodCount;
    }

    public String taskName() {
        return taskName;
    }

    public int methodCount() {
        return methodCount;
    }
}
