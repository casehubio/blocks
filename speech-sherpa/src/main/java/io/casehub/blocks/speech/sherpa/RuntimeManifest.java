package io.casehub.blocks.speech.sherpa;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

record RuntimeManifest(
        int sampleRate,
        int numCodebooks,
        int codebookSize,
        int semanticBeginId,
        int semanticEndId,
        int imEndId,
        String slowLogitsLayout,
        int maxSeqLen,
        int numLayers,
        int nLocalHeads,
        int headDim,
        int numFastLayers,
        int fastNLocalHeads,
        int fastHeadDim,
        String defaultPrecision,
        String defaultCodecPrecision,
        int mambaChunkSize,
        int mambaDConv,
        int mambaDState,
        int mambaDHead,
        int mambaNHeads,
        int mambaDSsm,
        String referenceCodesFile,
        String referenceText
) {
    static RuntimeManifest load(Path path) throws IOException {
        var obj = new Gson().fromJson(Files.readString(path), JsonObject.class);
        return new RuntimeManifest(
                obj.get("sample_rate").getAsInt(),
                obj.get("num_codebooks").getAsInt(),
                obj.get("codebook_size").getAsInt(),
                obj.get("semantic_begin_id").getAsInt(),
                obj.get("semantic_end_id").getAsInt(),
                obj.get("im_end_id").getAsInt(),
                obj.has("slow_logits_layout") ? obj.get("slow_logits_layout").getAsString() : "full_vocab",
                obj.get("max_seq_len").getAsInt(),
                obj.get("num_layers").getAsInt(),
                obj.get("n_local_heads").getAsInt(),
                obj.get("head_dim").getAsInt(),
                obj.get("num_fast_layers").getAsInt(),
                obj.get("fast_n_local_heads").getAsInt(),
                obj.get("fast_head_dim").getAsInt(),
                obj.has("default_precision") ? obj.get("default_precision").getAsString() : "int8",
                obj.has("default_codec_precision") ? obj.get("default_codec_precision").getAsString() : "fp16",
                obj.has("mamba_chunk_size") ? obj.get("mamba_chunk_size").getAsInt() : 0,
                obj.has("mamba_d_conv") ? obj.get("mamba_d_conv").getAsInt() : 0,
                obj.has("mamba_d_state") ? obj.get("mamba_d_state").getAsInt() : 0,
                obj.has("mamba_d_head") ? obj.get("mamba_d_head").getAsInt() : 0,
                obj.has("mamba_n_heads") ? obj.get("mamba_n_heads").getAsInt() : 0,
                obj.has("mamba_d_ssm") ? obj.get("mamba_d_ssm").getAsInt() : 0,
                obj.has("reference_codes") ? obj.get("reference_codes").getAsString() : null,
                obj.has("reference_text") ? obj.get("reference_text").getAsString() : null
        );
    }

    boolean hasMambaState() {
        return mambaChunkSize > 0;
    }

    int convStateDim() {
        return mambaDSsm + mambaChunkSize;
    }

    String slowArFilename() {
        return "slow_ar_" + defaultPrecision + ".onnx";
    }

    String fastArFilename() {
        return "fast_ar_" + defaultPrecision + ".onnx";
    }

    String codecDecoderFilename() {
        return "codec_decoder_" + defaultCodecPrecision + ".onnx";
    }

    boolean isCompactLogitsLayout() {
        return slowLogitsLayout.endsWith("semantic_then_eos");
    }
}
