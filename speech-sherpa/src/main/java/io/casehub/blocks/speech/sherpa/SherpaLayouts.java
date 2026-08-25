package io.casehub.blocks.speech.sherpa;

// Byte offsets into sherpa-onnx v1.13.6 C structs (64-bit platforms).
// Derived from c-api.h. The config structs are large (~800+ bytes for STT,
// ~600+ bytes for TTS) because they embed sub-configs for every supported
// model type. We allocate 4096 bytes zero-filled — sherpa-onnx treats zero/NULL
// as "not configured", matching C's memset(&config, 0, sizeof(config)) pattern.
final class SherpaLayouts {

    private SherpaLayouts() {}

    static final int CONFIG_ALLOC_SIZE = 4096;

    // === SherpaOnnxOfflineRecognizerConfig offsets ===
    // feat_config at offset 0
    static final long FEAT_SAMPLE_RATE  = 0;
    static final long FEAT_FEATURE_DIM  = 4;
    // model_config at offset 8
    //   transducer: 24 bytes (8-31), paraformer: 8 (32-39), nemo_ctc: 8 (40-47)
    //   whisper at offset 48: encoder(8), decoder(8), language(8), task(8),
    //     tail_paddings(4), enable_token_timestamps(4), enable_segment_timestamps(4), pad(4) = 48 bytes
    static final long WHISPER_ENCODER   = 48;
    static final long WHISPER_DECODER   = 56;
    static final long WHISPER_LANGUAGE  = 64;
    static final long WHISPER_TASK      = 72;
    //   tdnn at 96 (8 bytes, ends at 103)
    //   tokens at model_config offset 96 = absolute 104
    static final long MODEL_TOKENS      = 104;
    static final long MODEL_NUM_THREADS = 112;
    // debug at 116
    static final long MODEL_PROVIDER    = 120;
    // === SherpaOnnxOnlineRecognizerConfig offsets ===
    // feat_config at offset 0 (8 bytes)
    // model_config at offset 8:
    //   transducer (24), paraformer (16), zipformer2_ctc (8) = sub-structs end at 48
    //   tokens(8), num_threads(4), pad(4), provider(8), debug(4), pad(4),
    //   model_type(8), modeling_unit(8), bpe_vocab(8), tokens_buf(8),
    //   tokens_buf_size(4), pad(4), nemo_ctc(8), t_one_ctc(8) = model_config total ~136
    static final long ONLINE_TRANSDUCER_ENCODER = 8;
    static final long ONLINE_TRANSDUCER_DECODER = 16;
    static final long ONLINE_TRANSDUCER_JOINER = 24;
    static final long ONLINE_MODEL_TOKENS = 56;
    static final long ONLINE_MODEL_NUM_THREADS = 64;
    static final long ONLINE_MODEL_PROVIDER = 72;
    // After model_config (8 + 136 = 144):
    // decoding_method(8), max_active_paths(4), enable_endpoint(4),
    // rule1(4), rule2(4), rule3(4)
    static final long ONLINE_ENABLE_ENDPOINT = 156;
    static final long ONLINE_RULE1_MIN_TRAILING_SILENCE = 160;
    static final long ONLINE_RULE2_MIN_TRAILING_SILENCE = 164;
    static final long ONLINE_RULE3_MIN_UTTERANCE_LENGTH = 168;


    // === SherpaOnnxOfflineTtsConfig offsets ===
    // model.vits at offset 0
    static final long VITS_MODEL_PATH    = 0;
    static final long VITS_LEXICON       = 8;
    static final long VITS_TOKENS        = 16;
    static final long VITS_DATA_DIR      = 24;
    static final long VITS_NOISE_SCALE   = 32;
    static final long VITS_NOISE_SCALE_W = 36;
    static final long VITS_LENGTH_SCALE  = 40;
    // dict_dir at 48
    // vits total: 56 bytes
    static final long TTS_NUM_THREADS    = 56;
    // debug at 60
    static final long TTS_PROVIDER       = 64;

    // === SherpaOnnxGeneratedAudio (small, stable — keep as MemoryLayout) ===
    static final java.lang.foreign.GroupLayout GENERATED_AUDIO = java.lang.foreign.MemoryLayout.structLayout(
            java.lang.foreign.ValueLayout.ADDRESS.withName("samples"),
            java.lang.foreign.ValueLayout.JAVA_INT.withName("n"),
            java.lang.foreign.ValueLayout.JAVA_INT.withName("sample_rate")
                                                                                                            );

    static final java.lang.invoke.VarHandle AUDIO_SAMPLES     = GENERATED_AUDIO.varHandle(
            java.lang.foreign.MemoryLayout.PathElement.groupElement("samples"));
    static final java.lang.invoke.VarHandle AUDIO_N           = GENERATED_AUDIO.varHandle(
            java.lang.foreign.MemoryLayout.PathElement.groupElement("n"));
    static final java.lang.invoke.VarHandle AUDIO_SAMPLE_RATE = GENERATED_AUDIO.varHandle(
            java.lang.foreign.MemoryLayout.PathElement.groupElement("sample_rate"));
}
