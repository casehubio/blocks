package io.casehub.blocks.summarisation.observation;

import java.util.Map;

public record PartitionedDrain<K>(
        ObservationResult currentPartition,
        Map<K, RememberedPartition> rememberedPartitions) {}
