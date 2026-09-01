// Thin FFI shim for whisper.cpp — passes params by pointer instead of by value,
// and provides a single-call transcription function for Java Foreign Function API.
#include "whisper.h"
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>

int shim_whisper_full(struct whisper_context *ctx,
                      struct whisper_full_params *params,
                      const float *samples, int n_samples) {
    return whisper_full(ctx, *params, samples, n_samples);
}

char *shim_whisper_transcribe(struct whisper_context *ctx,
                              const float *samples, int n_samples,
                              const char *language, const char *initial_prompt,
                              int n_threads) {
    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads    = n_threads;
    params.print_progress   = false;
    params.print_realtime   = false;
    params.print_timestamps = false;
    if (language && language[0])       params.language       = language;
    if (initial_prompt && initial_prompt[0]) params.initial_prompt = initial_prompt;

    if (whisper_full(ctx, params, samples, n_samples) != 0) {
        return strdup("");
    }

    int n_segments = whisper_full_n_segments(ctx);
    size_t total = 0;
    for (int i = 0; i < n_segments; i++) {
        const char *text = whisper_full_get_segment_text(ctx, i);
        if (text) total += strlen(text);
    }

    char *result = (char *)malloc(total + 1);
    if (!result) return strdup("");
    result[0] = '\0';

    for (int i = 0; i < n_segments; i++) {
        const char *text = whisper_full_get_segment_text(ctx, i);
        if (text) strcat(result, text);
    }
    return result;
}

void shim_whisper_free_text(char *text) {
    free(text);
}
