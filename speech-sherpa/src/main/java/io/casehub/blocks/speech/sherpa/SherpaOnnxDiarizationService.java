package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.DiarizedSegment;
import io.casehub.blocks.speech.DiarizationOptions;
import io.casehub.blocks.speech.SpeakerDiarizationService;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SherpaOnnxDiarizationService implements SpeakerDiarizationService, AutoCloseable {

    private final SherpaLibrary lib;
    private final MemorySegment handle;
    private final int expectedSampleRate;
    private final Arena globalArena = Arena.ofShared();

    public SherpaOnnxDiarizationService(SherpaLibrary lib, Path segmentationModel, Path embeddingModel) {
        this.lib = lib;
        this.handle = createHandle(segmentationModel, embeddingModel);
        try {
            this.expectedSampleRate = (int) lib.diarizationGetSampleRate.invokeExact(handle);
        } catch (Throwable e) {
            throw new SherpaException("Failed to get diarization sample rate", e);
        }
    }

    @Override
    public List<DiarizedSegment> diarize(Path audioFile, DiarizationOptions options) {
        WavData wav;
        try {
            wav = WavReader.read(audioFile);
        } catch (IOException e) {
            throw new SherpaException("Failed to read audio file: " + audioFile, e);
        }
        float[] audio = wav.sampleRate() != expectedSampleRate
                ? AudioResampler.resample(wav.samples(), wav.sampleRate(), expectedSampleRate)
                : wav.samples();

        updateClusteringConfig(options);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment samples = arena.allocateFrom(ValueLayout.JAVA_FLOAT, audio);
            MemorySegment result = (MemorySegment) lib.diarizationProcess
                    .invokeExact(handle, samples, audio.length);
            try {
                int numSegments = (int) lib.diarizationResultGetNumSegments.invokeExact(result);
                if (numSegments == 0) return List.of();

                MemorySegment sorted = (MemorySegment) lib.diarizationResultSortByStartTime
                        .invokeExact(result);
                sorted = sorted.reinterpret((long) numSegments * 12);

                List<DiarizedSegment> segments = new ArrayList<>(numSegments);
                for (int i = 0; i < numSegments; i++) {
                    long offset = (long) i * 12;
                    float start = sorted.get(ValueLayout.JAVA_FLOAT, offset);
                    float end = sorted.get(ValueLayout.JAVA_FLOAT, offset + 4);
                    int speaker = sorted.get(ValueLayout.JAVA_INT, offset + 8);
                    int startIdx = (int) (start * expectedSampleRate);
                    int endIdx = Math.min((int) (end * expectedSampleRate), audio.length);
                    if (endIdx <= startIdx) continue;
                    float[] segSamples = Arrays.copyOfRange(audio, startIdx, endIdx);
                    segments.add(new DiarizedSegment(
                            (long) (start * 1000), (long) (end * 1000),
                            "speaker_" + speaker, segSamples, expectedSampleRate));
                }
                return segments;
            } finally {
                lib.diarizationDestroyResult.invokeExact(result);
            }
        } catch (SherpaException e) {
            throw e;
        } catch (Throwable e) {
            throw new SherpaException("Diarization failed", e);
        }
    }

    private MemorySegment createHandle(Path segModel, Path embModel) {
        try {
            MemorySegment config = globalArena.allocate(SherpaLayouts.CONFIG_ALLOC_SIZE);
            config.fill((byte) 0);
            config.set(ValueLayout.ADDRESS, SherpaLayouts.DIARIZATION_SEGMENTATION_PYANNOTE,
                    globalArena.allocateFrom(segModel.toString()));
            config.set(ValueLayout.ADDRESS, SherpaLayouts.DIARIZATION_EMBEDDING_MODEL,
                    globalArena.allocateFrom(embModel.toString()));
            config.set(ValueLayout.JAVA_INT, SherpaLayouts.DIARIZATION_SEGMENTATION_NUM_THREADS, 2);
            config.set(ValueLayout.ADDRESS, SherpaLayouts.DIARIZATION_SEGMENTATION_PROVIDER,
                    globalArena.allocateFrom("cpu"));
            config.set(ValueLayout.JAVA_INT, SherpaLayouts.DIARIZATION_EMBEDDING_NUM_THREADS, 2);
            config.set(ValueLayout.ADDRESS, SherpaLayouts.DIARIZATION_EMBEDDING_PROVIDER,
                    globalArena.allocateFrom("cpu"));
            config.set(ValueLayout.JAVA_INT, SherpaLayouts.DIARIZATION_CLUSTERING_NUM_CLUSTERS, -1);
            config.set(ValueLayout.JAVA_FLOAT, SherpaLayouts.DIARIZATION_CLUSTERING_THRESHOLD, 0.5f);
            return (MemorySegment) lib.createDiarization.invokeExact(config);
        } catch (Throwable e) {
            throw new SherpaException("Failed to create diarization handle", e);
        }
    }

    private void updateClusteringConfig(DiarizationOptions options) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment config = arena.allocate(SherpaLayouts.CONFIG_ALLOC_SIZE);
            config.fill((byte) 0);
            config.set(ValueLayout.JAVA_INT, SherpaLayouts.DIARIZATION_CLUSTERING_NUM_CLUSTERS,
                    options.numSpeakersHint());
            if (options.clusterThreshold() > 0) {
                config.set(ValueLayout.JAVA_FLOAT, SherpaLayouts.DIARIZATION_CLUSTERING_THRESHOLD,
                        options.clusterThreshold());
            }
            lib.diarizationSetConfig.invokeExact(handle, config);
        } catch (Throwable e) {
            throw new SherpaException("Failed to update clustering config", e);
        }
    }

    @Override
    public void close() {
        try {
            lib.destroyDiarization.invokeExact(handle);
        } catch (Throwable e) {
            throw new SherpaException("Failed to destroy diarization handle", e);
        }
        globalArena.close();
    }
}
