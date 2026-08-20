package io.casehub.blocks.agentic.social;

public sealed interface CivilityCheck {
    record Permitted() implements CivilityCheck {}
    record Denied(String reason) implements CivilityCheck {}
}
