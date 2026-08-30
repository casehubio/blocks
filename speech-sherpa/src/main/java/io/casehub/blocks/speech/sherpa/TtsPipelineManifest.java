package io.casehub.blocks.speech.sherpa;

public sealed interface TtsPipelineManifest permits CosyVoice3Manifest {
    PipelineHeader header();
}
