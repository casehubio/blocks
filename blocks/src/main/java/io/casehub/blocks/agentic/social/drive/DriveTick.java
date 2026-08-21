package io.casehub.blocks.agentic.social.drive;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public sealed interface DriveTick {
    record NoChange(@Nullable String reason) implements DriveTick {}
    record Updated(DriveProfile previous, DriveProfile current,
                   List<DriveAxis> changed) implements DriveTick {
        public Updated {
            Objects.requireNonNull(previous, "previous required");
            Objects.requireNonNull(current, "current required");
            Objects.requireNonNull(changed, "changed required");
            changed = List.copyOf(changed);
        }
    }
}
