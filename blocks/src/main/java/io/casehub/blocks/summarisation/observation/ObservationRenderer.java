package io.casehub.blocks.summarisation.observation;

import io.casehub.blocks.summarisation.LevelEvent;

import java.util.List;
import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface ObservationRenderer<E> {
    CompletionStage<ObservationResult> render(
            List<LevelEvent<E>> events, ObservationContext context);
}
