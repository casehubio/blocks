package io.casehub.blocks.speech.sherpa;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

public final class WhisperLibrary {

    static final int PARAMS_SIZE = 304;

    static final MemoryLayout PARAMS_LAYOUT;
    static {
        var fields = new MemoryLayout[PARAMS_SIZE / 4];
        for (int i = 0; i < fields.length; i++) {
            fields[i] = ValueLayout.JAVA_INT.withName("f" + i);
        }
        PARAMS_LAYOUT = MemoryLayout.structLayout(fields);
    }

    static final long OFFSET_STRATEGY = 0;
    static final long OFFSET_N_THREADS = 4;
    static final long OFFSET_PRINT_PROGRESS = 25;
    static final long OFFSET_PRINT_REALTIME = 26;
    static final long OFFSET_PRINT_TIMESTAMPS = 27;
    static final long OFFSET_INITIAL_PROMPT = 72;
    static final long OFFSET_LANGUAGE = 104;

    static final int WHISPER_SAMPLING_GREEDY = 0;
    static final int WHISPER_SAMPLING_BEAM_SEARCH = 1;

    private static volatile WhisperLibrary INSTANCE;

    static final int CTX_PARAMS_SIZE = 48;
    static final MemoryLayout CTX_PARAMS_LAYOUT;
    static {
        var ctxFields = new MemoryLayout[CTX_PARAMS_SIZE / 4];
        for (int i = 0; i < ctxFields.length; i++) {
            ctxFields[i] = ValueLayout.JAVA_INT.withName("c" + i);
        }
        CTX_PARAMS_LAYOUT = MemoryLayout.structLayout(ctxFields);
    }
    static final long CTX_OFFSET_USE_GPU = 0;

    final MethodHandle whisperInit;
    final MethodHandle whisperInitWithParams;
    final MethodHandle whisperCtxDefaultParams;
    final MethodHandle whisperCtxDefaultParamsByRef;
    final MethodHandle whisperFree;
    final MethodHandle whisperDefaultParams;
    final MethodHandle whisperDefaultParamsByRef;
    final MethodHandle whisperFull;
    final MethodHandle whisperFullNSegments;
    final MethodHandle whisperFullGetSegmentText;
    final MethodHandle whisperFullGetSegmentT0;
    final MethodHandle whisperFullGetSegmentT1;
    final MethodHandle shimTranscribe;
    final MethodHandle shimFreeText;

    private WhisperLibrary(SymbolLookup lookup) {
        Linker linker = Linker.nativeLinker();

        whisperInit = downcall(linker, lookup, "whisper_init_from_file",
                FunctionDescriptor.of(ADDRESS, ADDRESS));

        whisperInitWithParams = downcall(linker, lookup, "whisper_init_from_file_with_params",
                FunctionDescriptor.of(ADDRESS, ADDRESS, CTX_PARAMS_LAYOUT));

        whisperCtxDefaultParams = downcall(linker, lookup, "whisper_context_default_params",
                FunctionDescriptor.of(CTX_PARAMS_LAYOUT));

        whisperCtxDefaultParamsByRef = downcall(linker, lookup, "whisper_context_default_params_by_ref",
                FunctionDescriptor.ofVoid(ADDRESS));

        whisperFree = downcall(linker, lookup, "whisper_free",
                FunctionDescriptor.ofVoid(ADDRESS));

        whisperDefaultParams = downcall(linker, lookup, "whisper_full_default_params",
                FunctionDescriptor.of(PARAMS_LAYOUT, JAVA_INT));

        whisperDefaultParamsByRef = downcall(linker, lookup, "whisper_full_default_params_by_ref",
                FunctionDescriptor.ofVoid(JAVA_INT, ADDRESS));

        whisperFull = tryDowncall(linker, lookup, "shim_whisper_full",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT))
                .orElseGet(() -> downcall(linker, lookup, "whisper_full",
                        FunctionDescriptor.of(JAVA_INT, ADDRESS, PARAMS_LAYOUT, ADDRESS, JAVA_INT)));

        whisperFullNSegments = downcall(linker, lookup, "whisper_full_n_segments",
                FunctionDescriptor.of(JAVA_INT, ADDRESS));

        whisperFullGetSegmentText = downcall(linker, lookup, "whisper_full_get_segment_text",
                FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));

        whisperFullGetSegmentT0 = downcall(linker, lookup, "whisper_full_get_segment_t0",
                FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT));

        whisperFullGetSegmentT1 = downcall(linker, lookup, "whisper_full_get_segment_t1",
                FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT));

        shimTranscribe = downcall(linker, lookup, "shim_whisper_transcribe",
                FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
        shimFreeText = downcall(linker, lookup, "shim_whisper_free_text",
                FunctionDescriptor.ofVoid(ADDRESS));
    }

    public static WhisperLibrary load() {
        if (INSTANCE != null) return INSTANCE;
        synchronized (WhisperLibrary.class) {
            if (INSTANCE != null) return INSTANCE;

            String os = System.getProperty("os.name", "").toLowerCase();
            String libName = os.contains("mac") ? "libwhisper.dylib"
                    : os.contains("win") ? "whisper.dll" : "libwhisper.so";

            String override = System.getProperty("whisper.native.dir");
            if (override != null) {
                Path overrideDir = Path.of(override);
                Path lib = overrideDir.resolve(libName);
                if (Files.exists(lib)) {
                    INSTANCE = new WhisperLibrary(combinedLookup(lib, overrideDir));
                    return INSTANCE;
                }
            }

            try {
                SymbolLookup lookup = SymbolLookup.loaderLookup();
                lookup.find("whisper_init_from_file").orElseThrow();
                INSTANCE = new WhisperLibrary(lookup);
                return INSTANCE;
            } catch (Exception ignored) {}

            Path brewLib = Path.of("/opt/homebrew/lib").resolve(libName);
            if (Files.exists(brewLib)) {
                INSTANCE = new WhisperLibrary(SymbolLookup.libraryLookup(brewLib, Arena.global()));
                return INSTANCE;
            }

            Path cacheDir = Provisioner.whisperNativeCacheDir();
            Path cachedLib = cacheDir.resolve(libName);
            if (Files.exists(cachedLib)) {
                INSTANCE = new WhisperLibrary(SymbolLookup.libraryLookup(cachedLib, Arena.global()));
                return INSTANCE;
            }

            if (Provisioner.isAutoDownloadEnabled()) {
                Path downloadedDir = Provisioner.ensureWhisperNativeLib();
                Path downloadedLib = downloadedDir.resolve(libName);
                if (Files.exists(downloadedLib)) {
                    INSTANCE = new WhisperLibrary(SymbolLookup.libraryLookup(downloadedLib, Arena.global()));
                    return INSTANCE;
                }
            }

            throw new UnsatisfiedLinkError(
                    "whisper native library not found. Install whisper.cpp or place "
                    + libName + " in " + cacheDir);
        }
    }

    public static WhisperLibrary load(Path libraryPath) {
        SymbolLookup lookup = SymbolLookup.libraryLookup(libraryPath, Arena.global());
        return new WhisperLibrary(lookup);
    }

    public static boolean isAvailable() {
        try {
            load();
            return true;
        } catch (UnsatisfiedLinkError | Exception e) {
            System.getLogger("casehub-speech").log(System.Logger.Level.DEBUG,
                                                   "Whisper not available: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        }}

    MemorySegment defaultParams(Arena arena, int strategy) {
        try {
            MemorySegment params = arena.allocate(PARAMS_LAYOUT);
            whisperDefaultParamsByRef.invokeExact(strategy, params);
            return params;
        } catch (Throwable t) {
            throw new SherpaException("Failed to get whisper default params", t);
        }
    }

    void setParamsThreads(MemorySegment params, int threads) {
        params.set(ValueLayout.JAVA_INT, OFFSET_N_THREADS, threads);
    }

    void setParamsSilent(MemorySegment params) {
        params.set(ValueLayout.JAVA_BYTE, OFFSET_PRINT_PROGRESS, (byte) 0);
        params.set(ValueLayout.JAVA_BYTE, OFFSET_PRINT_REALTIME, (byte) 0);
        params.set(ValueLayout.JAVA_BYTE, OFFSET_PRINT_TIMESTAMPS, (byte) 0);
    }

    void setParamsInitialPrompt(MemorySegment params, MemorySegment promptStr) {
        params.set(ValueLayout.ADDRESS, OFFSET_INITIAL_PROMPT, promptStr);
    }

    void setParamsLanguage(MemorySegment params, MemorySegment langStr) {
        params.set(ValueLayout.ADDRESS, OFFSET_LANGUAGE, langStr);
    }

    private static MethodHandle downcall(Linker linker, SymbolLookup lookup,
                                          String name, FunctionDescriptor descriptor) {
        Optional<MemorySegment> symbol = lookup.find(name);
        if (symbol.isEmpty()) {
            throw new UnsatisfiedLinkError("Symbol not found in whisper: " + name);
        }
        return linker.downcallHandle(symbol.get(), descriptor);
    }

    private static Optional<MethodHandle> tryDowncall(Linker linker, SymbolLookup lookup,
                                                       String name, FunctionDescriptor descriptor) {
        return lookup.find(name)
                .map(seg -> linker.downcallHandle(seg, descriptor));
    }

    private static SymbolLookup combinedLookup(Path whisperLib, Path dir) {
        SymbolLookup primary = SymbolLookup.libraryLookup(whisperLib, Arena.global());
        String shimName = System.getProperty("os.name", "").toLowerCase().contains("mac")
                ? "libwhisper_shim.dylib" : "libwhisper_shim.so";
        Path shimLib = dir.resolve(shimName);
        if (Files.exists(shimLib)) {
            SymbolLookup shim = SymbolLookup.libraryLookup(shimLib, Arena.global());
            return name -> primary.find(name).or(() -> shim.find(name));
        }
        return primary;
    }
}
