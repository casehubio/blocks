package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIf("espeakAvailable")
class EspeakLibraryTest {

    @Test
    void textToPhonemes_producesIpa() {
        var lib = loadEspeak();
        String ipa = lib.textToPhonemes("hello", "en-us");
        assertThat(ipa).isNotEmpty();
        assertThat(ipa).contains("h");
        assertThat(ipa).contains("l");
    }

    @Test
    void textToPhonemes_multipleWords() {
        var lib = loadEspeak();
        String ipa = lib.textToPhonemes("hello world", "en-us");
        assertThat(ipa).contains(" ");
    }

    @Test
    void textToPhonemes_consistentOutput() {
        var lib = loadEspeak();
        String first = lib.textToPhonemes("test", "en-us");
        String second = lib.textToPhonemes("test", "en-us");
        assertThat(first).isEqualTo(second);
    }

    @Test
    void textToPhonemes_threadSafe() {
        var lib = loadEspeak();
        var futures = IntStream.range(0, 10)
                .mapToObj(i -> CompletableFuture.supplyAsync(
                        () -> lib.textToPhonemes("test " + i, "en-us")))
                .toList();
        var results = futures.stream()
                .map(CompletableFuture::join)
                .toList();
        assertThat(results).hasSize(10);
        assertThat(results).allMatch(s -> !s.isEmpty());
    }

    @Test
    void textToPhonemes_emptyString() {
        var lib = loadEspeak();
        String ipa = lib.textToPhonemes("", "en-us");
        assertThat(ipa).isEmpty();
    }

    static EspeakLibrary loadEspeak() {
        return EspeakLibrary.load(espeakLibPath(), espeakDataPath());
    }

    static boolean espeakAvailable() {
        Path lib = espeakLibPath();
        Path data = espeakDataPath();
        return Files.exists(lib) && Files.isDirectory(data);
    }

    static Path espeakLibPath() {
        return Path.of("/opt/homebrew/lib/libespeak-ng.dylib");
    }

    static Path espeakDataPath() {
        return Path.of("/opt/homebrew/Cellar/espeak-ng/1.52.0/share/espeak-ng-data");
    }
}
