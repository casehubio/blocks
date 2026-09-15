package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.memory.relationship.RelationshipEvent;

import java.util.Map;
import java.util.Objects;

public sealed interface MentalStateSignal {
    String content();

    record VerbalCue(String content, CueType type) implements MentalStateSignal {
        public VerbalCue {
            Objects.requireNonNull(content, "content required");
            Objects.requireNonNull(type, "type required");
        }
    }

    record BehavioralCue(String content, String actionType) implements MentalStateSignal {
        public BehavioralCue {
            Objects.requireNonNull(content, "content required");
            Objects.requireNonNull(actionType, "actionType required");
        }
    }

    record ContextualCue(String content, Map<String, String> metadata) implements MentalStateSignal {
        public ContextualCue {
            Objects.requireNonNull(content, "content required");
            Objects.requireNonNull(metadata, "metadata required");
            metadata = Map.copyOf(metadata);
        }
    }

    record RelationshipCue(RelationshipEvent event) implements MentalStateSignal {
        public RelationshipCue {
            Objects.requireNonNull(event, "event required");
        }

        @Override
        public String content() {
            return event.description();
        }
    }
}
