package io.casehub.blocks.conversation;

import io.casehub.work.progress.ProgressInstance;

@FunctionalInterface
public interface ProgressRenderer {
    String render(ProgressInstance progress);
}
