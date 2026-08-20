package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.memory.experience.ExperienceEvent;
import io.casehub.neocortex.memory.relationship.QualitySignal;
import io.casehub.neocortex.memory.relationship.RelationshipEvent;

import java.util.Objects;

public sealed interface InteractionSignal {

    String description();

    QualitySignal quality();

    record RelationshipSignal(RelationshipEvent event) implements InteractionSignal {
        public RelationshipSignal {
            Objects.requireNonNull(event, "event required");
        }

        @Override
        public String description() {
            return event.description();
        }

        @Override
        public QualitySignal quality() {
            return event.qualitySignal();
        }
    }

    record ExperienceSignal(ExperienceEvent event, QualitySignal quality) implements InteractionSignal {
        public ExperienceSignal {
            Objects.requireNonNull(event, "event required");
            Objects.requireNonNull(quality, "quality required");
        }

        @Override
        public String description() {
            return event.description();
        }
    }

    record CustomSignal(String description, QualitySignal quality) implements InteractionSignal {
        public CustomSignal {
            Objects.requireNonNull(description, "description required");
            Objects.requireNonNull(quality, "quality required");
        }
    }
}
