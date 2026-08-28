package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.lang.foreign.Arena;

import static org.assertj.core.api.Assertions.assertThat;

class WhisperLibraryTest {

    static boolean isWhisperAvailable() {
        return WhisperLibrary.isAvailable();
    }

    @Test
    void isAvailableDoesNotThrow() {
        boolean result = WhisperLibrary.isAvailable();
        assertThat(result).isIn(true, false);
    }

    @Test
    void isAvailableDoesNotThrowWithAutoDownloadEnabled() {
        String prev = System.getProperty("casehub.speech.auto-download");
        try {
            System.setProperty("casehub.speech.auto-download", "true");
            // Force re-check by clearing singleton
            // isAvailable() must never throw — even if provisioner fails with SherpaException
            boolean result = WhisperLibrary.isAvailable();
            assertThat(result).isIn(true, false);
        } finally {
            if (prev != null) {
                System.setProperty("casehub.speech.auto-download", prev);
            } else {System.clearProperty("casehub.speech.auto-download");}
        }
    }


    @Test
    @EnabledIf("isWhisperAvailable")
    void loadsLibraryAndResolvesSymbols() {
        var lib = WhisperLibrary.load();
        assertThat(lib).isNotNull();
        assertThat(lib.whisperInit).isNotNull();
        assertThat(lib.whisperFull).isNotNull();
        assertThat(lib.whisperFree).isNotNull();
        assertThat(lib.whisperDefaultParams).isNotNull();
        assertThat(lib.whisperFullNSegments).isNotNull();
        assertThat(lib.whisperFullGetSegmentText).isNotNull();
    }

    @Test
    @EnabledIf("isWhisperAvailable")
    void defaultParamsReturnsNonNullSegment() {
        var lib = WhisperLibrary.load();
        try (Arena arena = Arena.ofConfined()) {
            var params = lib.defaultParams(arena, WhisperLibrary.WHISPER_SAMPLING_BEAM_SEARCH);
            assertThat(params).isNotNull();
            assertThat(params.byteSize()).isEqualTo(WhisperLibrary.PARAMS_SIZE);
        }
    }

    @Test
    @EnabledIf("isWhisperAvailable")
    void canSetParamsFields() {
        var lib = WhisperLibrary.load();
        try (Arena arena = Arena.ofConfined()) {
            var params = lib.defaultParams(arena, WhisperLibrary.WHISPER_SAMPLING_BEAM_SEARCH);
            lib.setParamsThreads(params, 4);
            lib.setParamsSilent(params);
            lib.setParamsLanguage(params, arena.allocateFrom("en"));
            lib.setParamsInitialPrompt(params, arena.allocateFrom("hello world"));
            // no crash = fields are writable at the expected offsets
        }
    }
}
