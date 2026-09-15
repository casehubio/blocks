package io.casehub.blocks.memory;

import java.util.List;

public sealed interface MaintenanceTick {
    record Completed(HygieneTick hygiene, int reflectionsGenerated,
                     int crossLinksCreated,
                     List<IntegrityViolation> violations) implements MaintenanceTick {
        public Completed { violations = List.copyOf(violations); }
    }
    record Failed(String stage, String reason) implements MaintenanceTick {}
}
