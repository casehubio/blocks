package io.casehub.blocks.agentic.routing;

import java.util.Set;

public interface StageGate {
    Set<String> allStagedBindings();
    Set<String> activeStagedBindings();
}
