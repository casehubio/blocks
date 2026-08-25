package io.casehub.blocks.speech.sherpa;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.Optional;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

final class SherpaLibrary {

    private static volatile SherpaLibrary INSTANCE;

    private final SymbolLookup lookup;

    // STT
    final MethodHandle createRecognizer;
    final MethodHandle destroyRecognizer;
    final MethodHandle createStream;
    final MethodHandle destroyStream;
    final MethodHandle acceptWaveform;
    final MethodHandle decodeStream;
    final MethodHandle getResult;
    final MethodHandle destroyResult;

    // TTS
    final MethodHandle createTts;
    final MethodHandle destroyTts;
    final MethodHandle ttsGenerate;
    final MethodHandle destroyGeneratedAudio;
    // Online STT (streaming)
    final MethodHandle createOnlineRecognizer;
    final MethodHandle destroyOnlineRecognizer;
    final MethodHandle createOnlineStream;
    final MethodHandle destroyOnlineStream;
    final MethodHandle onlineStreamAcceptWaveform;
    final MethodHandle isOnlineStreamReady;
    final MethodHandle decodeOnlineStream;
    final MethodHandle isEndpoint;
    final MethodHandle resetOnlineStream;
    final MethodHandle getOnlineStreamResult;
    final MethodHandle destroyOnlineRecognizerResult;
    // Online punctuation
    final MethodHandle createOnlinePunctuation;
    final MethodHandle destroyOnlinePunctuation;
    final MethodHandle onlinePunctuationAddPunct;
    final MethodHandle onlinePunctuationFreeText;


    private SherpaLibrary(SymbolLookup lookup) {
        this.lookup = lookup;
        Linker linker = Linker.nativeLinker();

        // STT handles
        createRecognizer = downcall(linker, "SherpaOnnxCreateOfflineRecognizer",
                FunctionDescriptor.of(ADDRESS, ADDRESS));
        destroyRecognizer = downcall(linker, "SherpaOnnxDestroyOfflineRecognizer",
                FunctionDescriptor.ofVoid(ADDRESS));
        createStream = downcall(linker, "SherpaOnnxCreateOfflineStream",
                FunctionDescriptor.of(ADDRESS, ADDRESS));
        destroyStream = downcall(linker, "SherpaOnnxDestroyOfflineStream",
                FunctionDescriptor.ofVoid(ADDRESS));
        acceptWaveform = downcall(linker, "SherpaOnnxAcceptWaveformOffline",
                FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));
        decodeStream = downcall(linker, "SherpaOnnxDecodeOfflineStream",
                FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
        getResult = downcall(linker, "SherpaOnnxGetOfflineStreamResult",
                FunctionDescriptor.of(ADDRESS, ADDRESS));
        destroyResult = downcall(linker, "SherpaOnnxDestroyOfflineRecognizerResult",
                FunctionDescriptor.ofVoid(ADDRESS));

        // TTS handles
        createTts = downcall(linker, "SherpaOnnxCreateOfflineTts",
                FunctionDescriptor.of(ADDRESS, ADDRESS));
        destroyTts = downcall(linker, "SherpaOnnxDestroyOfflineTts",
                FunctionDescriptor.ofVoid(ADDRESS));
        ttsGenerate = downcall(linker, "SherpaOnnxOfflineTtsGenerate",
                FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_INT, JAVA_FLOAT));
        destroyGeneratedAudio = downcall(linker, "SherpaOnnxDestroyOfflineTtsGeneratedAudio",
                FunctionDescriptor.ofVoid(ADDRESS));

        // Online STT handles (streaming)
        createOnlineRecognizer = downcall(linker, "SherpaOnnxCreateOnlineRecognizer",
                FunctionDescriptor.of(ADDRESS, ADDRESS));
        destroyOnlineRecognizer = downcall(linker, "SherpaOnnxDestroyOnlineRecognizer",
                FunctionDescriptor.ofVoid(ADDRESS));
        createOnlineStream = downcall(linker, "SherpaOnnxCreateOnlineStream",
                FunctionDescriptor.of(ADDRESS, ADDRESS));
        destroyOnlineStream = downcall(linker, "SherpaOnnxDestroyOnlineStream",
                FunctionDescriptor.ofVoid(ADDRESS));
        onlineStreamAcceptWaveform = downcall(linker, "SherpaOnnxOnlineStreamAcceptWaveform",
                FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));
        isOnlineStreamReady = downcall(linker, "SherpaOnnxIsOnlineStreamReady",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
        decodeOnlineStream = downcall(linker, "SherpaOnnxDecodeOnlineStream",
                FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
        isEndpoint = downcall(linker, "SherpaOnnxOnlineStreamIsEndpoint",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
        resetOnlineStream = downcall(linker, "SherpaOnnxOnlineStreamReset",
                FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
        getOnlineStreamResult = downcall(linker, "SherpaOnnxGetOnlineStreamResult",
                FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
        destroyOnlineRecognizerResult = downcall(linker, "SherpaOnnxDestroyOnlineRecognizerResult",
                FunctionDescriptor.ofVoid(ADDRESS));

        // Online punctuation handles
        createOnlinePunctuation = downcall(linker, "SherpaOnnxCreateOnlinePunctuation",
                FunctionDescriptor.of(ADDRESS, ADDRESS));
        destroyOnlinePunctuation = downcall(linker, "SherpaOnnxDestroyOnlinePunctuation",
                FunctionDescriptor.ofVoid(ADDRESS));
        onlinePunctuationAddPunct = downcall(linker, "SherpaOnnxOnlinePunctuationAddPunct",
                FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
        onlinePunctuationFreeText = downcall(linker, "SherpaOnnxOnlinePunctuationFreeText",
                FunctionDescriptor.ofVoid(ADDRESS));
    }

    static SherpaLibrary load() {
        if (INSTANCE != null) {return INSTANCE;}
        synchronized (SherpaLibrary.class) {
            if (INSTANCE != null) {return INSTANCE;}

            // Tier 1: system library path
            try {
                SymbolLookup lookup = SymbolLookup.libraryLookup("sherpa-onnx-c-api", Arena.global());
                INSTANCE = new SherpaLibrary(lookup);
                return INSTANCE;
            } catch (IllegalArgumentException | UnsatisfiedLinkError ignored) {
            }

            // Tier 2: local cache
            Path cacheDir = resolveNativeDir();
            if (cacheDir != null) {
                Path onnxRuntime = cacheDir.resolve(onnxRuntimeLibName());
                Path sherpaLib   = cacheDir.resolve(sherpaLibName());
                if (java.nio.file.Files.exists(sherpaLib) && java.nio.file.Files.exists(onnxRuntime)) {
                    SymbolLookup.libraryLookup(onnxRuntime, Arena.global());
                    SymbolLookup lookup = SymbolLookup.libraryLookup(sherpaLib, Arena.global());
                    INSTANCE = new SherpaLibrary(lookup);
                    return INSTANCE;
                }
            }

            throw new UnsatisfiedLinkError(
                    "sherpa-onnx native library not found. Install it system-wide or place "
                    + sherpaLibName() + " + " + onnxRuntimeLibName()
                    + " in " + defaultCacheDir());
        }
    }

    static SherpaLibrary load(Path libraryPath) {
        SymbolLookup lookup = SymbolLookup.libraryLookup(libraryPath, Arena.global());
        return new SherpaLibrary(lookup);
    }

    static boolean isAvailable() {
        try {
            load();
            return true;
        } catch (UnsatisfiedLinkError | IllegalArgumentException e) {
            return false;
        }
    }

    private MethodHandle downcall(Linker linker, String name, FunctionDescriptor descriptor) {
        Optional<MemorySegment> symbol = lookup.find(name);
        if (symbol.isEmpty()) {
            throw new UnsatisfiedLinkError("Symbol not found in sherpa-onnx: " + name);
        }
        return linker.downcallHandle(symbol.get(), descriptor);
    }

    private static Path resolveNativeDir() {
        String override = System.getProperty("sherpa.native.dir");
        if (override != null) {return Path.of(override);}

        Path cacheDir = defaultCacheDir();
        if (java.nio.file.Files.isDirectory(cacheDir)) {return cacheDir;}

        // Search versioned subdirectories (latest first)
        Path parent = cacheDir.getParent();
        if (parent != null && java.nio.file.Files.isDirectory(parent)) {
            try (var dirs = java.nio.file.Files.list(parent)) {
                return dirs.filter(java.nio.file.Files::isDirectory)
                           .max(java.util.Comparator.comparing(p -> p.getFileName().toString()))
                           .map(v -> v.resolve(platformId()))
                           .filter(java.nio.file.Files::isDirectory)
                           .orElse(null);
            } catch (java.io.IOException e) {
                return null;
            }
        }
        return null;
    }

    static Path defaultCacheDir() {
        return Path.of(System.getProperty("user.home"), ".casehub", "native", "sherpa-onnx", VERSION, platformId());
    }

    static String platformId() {
        String os      = System.getProperty("os.name", "").toLowerCase();
        String arch    = System.getProperty("os.arch", "");
        String osKey   = os.contains("mac") ? "osx" : os.contains("linux") ? "linux" : "win";
        String archKey = arch.equals("aarch64") || arch.equals("arm64") ? "arm64" : "x64";
        return osKey + "-" + archKey;
    }

    private static String sherpaLibName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {return "libsherpa-onnx-c-api.dylib";}
        if (os.contains("win")) {return "sherpa-onnx-c-api.dll";}
        return "libsherpa-onnx-c-api.so";
    }

    private static String onnxRuntimeLibName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {return "libonnxruntime.dylib";}
        if (os.contains("win")) {return "onnxruntime.dll";}
        return "libonnxruntime.so";
    }

    static final String VERSION = "1.13.6";
}
