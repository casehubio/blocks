package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.TextFilter;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.Objects;

public final class PunctuationFilter implements TextFilter, AutoCloseable {

    private final SherpaLibrary lib;
    private final MemorySegment punct;
    private final Arena punctArena;


    public static PunctuationFilter withDefaults() {
        Provisioner.ensureNativeLibrary();
        Path modelDir = Provisioner.ensurePunctuationModel();
        return new PunctuationFilter(modelDir);
    }

    public PunctuationFilter(Path modelDir) {
        this(modelDir, SherpaLibrary.load());
    }

    PunctuationFilter(Path modelDir, SherpaLibrary lib) {
        this.lib = Objects.requireNonNull(lib);
        this.punctArena = Arena.ofShared();

        MemorySegment config = punctArena.allocate(SherpaLayouts.CONFIG_ALLOC_SIZE);
        config.fill((byte) 0);

        config.set(ValueLayout.ADDRESS, 0,
                punctArena.allocateFrom(modelDir.resolve("model.onnx").toString()));
        config.set(ValueLayout.ADDRESS, 8,
                punctArena.allocateFrom(modelDir.resolve("bpe.vocab").toString()));
        config.set(ValueLayout.JAVA_INT, 16, 2);
        config.set(ValueLayout.ADDRESS, 24, punctArena.allocateFrom("cpu"));

        try {
            this.punct = (MemorySegment) lib.createOnlinePunctuation.invokeExact(config);
        } catch (Throwable t) {
            punctArena.close();
            throw new SherpaException("Failed to create punctuation model", t);
        }
        if (punct.equals(MemorySegment.NULL)) {
            punctArena.close();
            throw new SherpaException("sherpa-onnx returned null punctuation — check model paths in " + modelDir);
        }
    }

    @Override
    public String apply(String text) {
        if (text == null || text.isBlank()) return text;

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment textSeg = arena.allocateFrom(text);
            MemorySegment resultPtr;
            try {
                resultPtr = (MemorySegment) lib.onlinePunctuationAddPunct.invokeExact(punct, textSeg);
            } catch (Throwable t) {
                throw new SherpaException("Punctuation failed", t);
            }
            try {
                return resultPtr.reinterpret(Long.MAX_VALUE).getString(0);
            } finally {
                try { lib.onlinePunctuationFreeText.invokeExact(resultPtr); } catch (Throwable t) { /* cleanup */ }
            }
        }
    }

    @Override
    public String name() {
        return "punctuation";
    }

    @Override
    public int destructiveness() {
        return 2;
    }

    @Override
    public void close() {
        try { lib.destroyOnlinePunctuation.invokeExact(punct); } catch (Throwable t) { /* cleanup */ }
        punctArena.close();
    }
}
