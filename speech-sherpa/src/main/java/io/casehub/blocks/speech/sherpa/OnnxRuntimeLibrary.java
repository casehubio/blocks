package io.casehub.blocks.speech.sherpa;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

final class OnnxRuntimeLibrary {

    private static volatile OnnxRuntimeLibrary INSTANCE;

    private final MemorySegment ortApi;
    private final MemorySegment env;
    private final Linker linker = Linker.nativeLinker();

    // Vtable indices (from onnxruntime v1.21.0 c-api.h)
    private static final int IDX_GET_ERROR_CODE = 1;
    private static final int IDX_GET_ERROR_MESSAGE = 2;
    private static final int IDX_CREATE_ENV = 3;
    private static final int IDX_CREATE_SESSION = 7;
    private static final int IDX_RUN = 9;
    private static final int IDX_CREATE_SESSION_OPTIONS = 10;
    private static final int IDX_SET_INTRA_OP_NUM_THREADS = 24;
    private static final int IDX_SESSION_GET_INPUT_COUNT = 30;
    private static final int IDX_SESSION_GET_OUTPUT_COUNT = 31;
    private static final int IDX_SESSION_GET_INPUT_NAME = 36;
    private static final int IDX_SESSION_GET_OUTPUT_NAME = 37;
    private static final int IDX_CREATE_TENSOR_WITH_DATA = 49;
    private static final int IDX_GET_TENSOR_MUTABLE_DATA = 51;
    private static final int IDX_CREATE_CPU_MEMORY_INFO = 69;
    private static final int IDX_ALLOCATOR_FREE = 76;
    private static final int IDX_GET_ALLOCATOR_WITH_DEFAULT_OPTIONS = 78;
    private static final int IDX_RELEASE_ENV = 92;
    private static final int IDX_RELEASE_STATUS = 93;
    private static final int IDX_RELEASE_MEMORY_INFO = 94;
    private static final int IDX_RELEASE_SESSION = 95;
    private static final int IDX_RELEASE_VALUE = 96;
    private static final int IDX_RELEASE_SESSION_OPTIONS = 100;

    private static final int ORT_API_VERSION = 18;
    private static final int ORT_LOGGING_LEVEL_WARNING = 2;
    private static final int OrtMemTypeCPU = 0;
    private static final int OrtDeviceAllocator = 0;
    private static final int ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT = 1;
    private static final int ONNX_TENSOR_ELEMENT_DATA_TYPE_INT64 = 7;

    private OnnxRuntimeLibrary(MemorySegment ortApi) {
        this.ortApi = ortApi;
        MemorySegment envOut = Arena.global().allocate(ADDRESS);
        MemorySegment logId = Arena.global().allocateFrom("casehub");
        checkStatus(callVtable(IDX_CREATE_ENV,
                FunctionDescriptor.of(ADDRESS, JAVA_INT, ADDRESS, ADDRESS),
                ORT_LOGGING_LEVEL_WARNING, logId, envOut));
        this.env = envOut.get(ADDRESS, 0);
    }

    static OnnxRuntimeLibrary load() {
        if (INSTANCE != null) { return INSTANCE; }
        synchronized (OnnxRuntimeLibrary.class) {
            if (INSTANCE != null) { return INSTANCE; }

            SymbolLookup lookup = findOnnxRuntime();
            MemorySegment apiBase = resolveApiBase(lookup);
            MemorySegment api = resolveApi(apiBase);

            INSTANCE = new OnnxRuntimeLibrary(api);
            return INSTANCE;
        }
    }

    Session createSession(Path modelPath, int numThreads) {
        try (Arena setup = Arena.ofConfined()) {
            MemorySegment optsOut = setup.allocate(ADDRESS);
            checkStatus(callVtable(IDX_CREATE_SESSION_OPTIONS,
                    FunctionDescriptor.of(ADDRESS, ADDRESS),
                    optsOut));
            MemorySegment opts = optsOut.get(ADDRESS, 0);

            checkStatus(callVtable(IDX_SET_INTRA_OP_NUM_THREADS,
                    FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT),
                    opts, numThreads));

            MemorySegment sessionOut = setup.allocate(ADDRESS);
            MemorySegment modelPathSeg = setup.allocateFrom(modelPath.toString());
            checkStatus(callVtable(IDX_CREATE_SESSION,
                    FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS),
                    env, modelPathSeg, opts, sessionOut));
            MemorySegment session = sessionOut.get(ADDRESS, 0);

            MemorySegment memInfoOut = setup.allocate(ADDRESS);
            checkStatus(callVtable(IDX_CREATE_CPU_MEMORY_INFO,
                    FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT, ADDRESS),
                    OrtDeviceAllocator, OrtMemTypeCPU, memInfoOut));
            MemorySegment memInfo = memInfoOut.get(ADDRESS, 0);

            MemorySegment allocOut = setup.allocate(ADDRESS);
            checkStatus(callVtable(IDX_GET_ALLOCATOR_WITH_DEFAULT_OPTIONS,
                    FunctionDescriptor.of(ADDRESS, ADDRESS),
                    allocOut));
            MemorySegment allocator = allocOut.get(ADDRESS, 0);

            releaseQuietly(IDX_RELEASE_SESSION_OPTIONS, opts);

            return new Session(this, session, memInfo, allocator);
        }
    }

    private MemorySegment callVtable(int index, FunctionDescriptor desc, Object... args) {
        try {
            MemorySegment fnPtr = ortApi.get(ADDRESS, (long) index * ADDRESS.byteSize());
            MethodHandle mh = linker.downcallHandle(fnPtr, desc);
            return (MemorySegment) mh.invokeWithArguments(args);
        } catch (Throwable t) {
            throw new SherpaException("ORT vtable call failed at index " + index, t);
        }
    }

    private void callVtableVoid(int index, FunctionDescriptor desc, Object... args) {
        try {
            MemorySegment fnPtr = ortApi.get(ADDRESS, (long) index * ADDRESS.byteSize());
            MethodHandle mh = linker.downcallHandle(fnPtr, desc);
            mh.invokeWithArguments(args);
        } catch (Throwable t) {
            throw new SherpaException("ORT vtable call failed at index " + index, t);
        }
    }

    private void checkStatus(MemorySegment status) {
        if (status == null || status.equals(MemorySegment.NULL)) {
            return;
        }
        try {
            MemorySegment msgPtr = callVtable(IDX_GET_ERROR_MESSAGE,
                    FunctionDescriptor.of(ADDRESS, ADDRESS), status);
            String msg = msgPtr.reinterpret(1024).getString(0);
            callVtableVoid(IDX_RELEASE_STATUS,
                    FunctionDescriptor.ofVoid(ADDRESS), status);
            throw new SherpaException("ORT error: " + msg);
        } catch (SherpaException e) {
            throw e;
        } catch (Throwable t) {
            throw new SherpaException("ORT error (could not read message)", t);
        }
    }

    private void releaseQuietly(int releaseIndex, MemorySegment ptr) {
        if (ptr != null && !ptr.equals(MemorySegment.NULL)) {
            try {
                callVtableVoid(releaseIndex, FunctionDescriptor.ofVoid(ADDRESS), ptr);
            } catch (Throwable ignored) {}
        }
    }

    private static SymbolLookup findOnnxRuntime() {
        // Tier 1: system library path
        try {
            return SymbolLookup.libraryLookup("onnxruntime", Arena.global());
        } catch (IllegalArgumentException | UnsatisfiedLinkError ignored) {}

        // Tier 2: sherpa-onnx provisioned directory
        Path cacheDir = SherpaLibrary.defaultCacheDir();
        if (cacheDir != null) {
            Path onnxLib = cacheDir.resolve(onnxRuntimeLibName());
            if (Files.exists(onnxLib)) {
                return SymbolLookup.libraryLookup(onnxLib, Arena.global());
            }
        }

        throw new UnsatisfiedLinkError(
                "onnxruntime not found. Install it system-wide or provision sherpa-onnx first.");
    }

    private static MemorySegment resolveApiBase(SymbolLookup lookup) {
        var symbol = lookup.find("OrtGetApiBase")
                .orElseThrow(() -> new UnsatisfiedLinkError("OrtGetApiBase not found in onnxruntime"));

        try {
            MethodHandle getApiBase = Linker.nativeLinker().downcallHandle(
                    symbol, FunctionDescriptor.of(ADDRESS));
            MemorySegment apiBase = (MemorySegment) getApiBase.invokeExact();
            return apiBase.reinterpret(2 * ADDRESS.byteSize());
        } catch (Throwable t) {
            throw new SherpaException("Failed to call OrtGetApiBase", t);
        }
    }

    private static MemorySegment resolveApi(MemorySegment apiBase) {
        try {
            MemorySegment getApiFnPtr = apiBase.get(ADDRESS, 0);
            MethodHandle getApi = Linker.nativeLinker().downcallHandle(
                    getApiFnPtr, FunctionDescriptor.of(ADDRESS, JAVA_INT));
            MemorySegment api = (MemorySegment) getApi.invokeExact(ORT_API_VERSION);
            if (api.equals(MemorySegment.NULL)) {
                throw new SherpaException(
                        "ORT API version " + ORT_API_VERSION + " not supported by loaded onnxruntime");
            }
            return api.reinterpret(200L * ADDRESS.byteSize());
        } catch (SherpaException e) {
            throw e;
        } catch (Throwable t) {
            throw new SherpaException("Failed to resolve ORT API", t);
        }
    }

    private static String onnxRuntimeLibName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) { return "libonnxruntime.dylib"; }
        if (os.contains("win")) { return "onnxruntime.dll"; }
        return "libonnxruntime.so";
    }

    static final class Session implements AutoCloseable {
        private final OnnxRuntimeLibrary lib;
        private final MemorySegment session;
        private final MemorySegment memInfo;
        private final MemorySegment allocator;
        private final int inputCount;
        private final int outputCount;
        private volatile boolean closed;


        Session(OnnxRuntimeLibrary lib, MemorySegment session,
                MemorySegment memInfo, MemorySegment allocator) {
            this.lib = lib;
            this.session = session;
            this.memInfo = memInfo;
            this.allocator = allocator;
            this.inputCount = queryCount(IDX_SESSION_GET_INPUT_COUNT);
            this.outputCount = queryCount(IDX_SESSION_GET_OUTPUT_COUNT);
        }

        int inputCount() { return inputCount; }
        int outputCount() { return outputCount; }

        String inputName(int index) { return queryName(IDX_SESSION_GET_INPUT_NAME, index); }
        String outputName(int index) { return queryName(IDX_SESSION_GET_OUTPUT_NAME, index); }

        float[] runFloat(String[] inputNames, MemorySegment[] inputData, long[][] inputShapes,
                         String[] outputNames, Arena arena) {
            int numInputs = inputNames.length;
            int numOutputs = outputNames.length;

            MemorySegment inputNamePtrs = arena.allocate(ADDRESS, numInputs);
            MemorySegment inputValues = arena.allocate(ADDRESS, numInputs);

            for (int i = 0; i < numInputs; i++) {
                inputNamePtrs.setAtIndex(ADDRESS, i, arena.allocateFrom(inputNames[i]));

                MemorySegment shapeSeg = arena.allocateFrom(JAVA_LONG, inputShapes[i]);
                long totalBytes = 1;
                for (long dim : inputShapes[i]) { totalBytes *= dim; }
                totalBytes *= ValueLayout.JAVA_FLOAT.byteSize();

                MemorySegment valueOut = arena.allocate(ADDRESS);
                lib.checkStatus(lib.callVtable(IDX_CREATE_TENSOR_WITH_DATA,
                        FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS, JAVA_LONG, JAVA_INT, ADDRESS),
                        memInfo, inputData[i], totalBytes, shapeSeg, (long) inputShapes[i].length,
                        ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT, valueOut));
                inputValues.setAtIndex(ADDRESS, i, valueOut.get(ADDRESS, 0));
            }

            MemorySegment outputNamePtrs = arena.allocate(ADDRESS, numOutputs);
            for (int i = 0; i < numOutputs; i++) {
                outputNamePtrs.setAtIndex(ADDRESS, i, arena.allocateFrom(outputNames[i]));
            }

            MemorySegment outputValues = arena.allocate(ADDRESS, numOutputs);
            outputValues.fill((byte) 0);

            lib.checkStatus(lib.callVtable(IDX_RUN,
                    FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS, JAVA_LONG, ADDRESS),
                    session, MemorySegment.NULL, inputNamePtrs, inputValues, (long) numInputs,
                    outputNamePtrs, (long) numOutputs, outputValues));

            MemorySegment firstOutput = outputValues.get(ADDRESS, 0);
            MemorySegment dataOut = arena.allocate(ADDRESS);
            lib.checkStatus(lib.callVtable(IDX_GET_TENSOR_MUTABLE_DATA,
                    FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS),
                    firstOutput, dataOut));

            MemorySegment dataPtr = dataOut.get(ADDRESS, 0);
            MemorySegment tensorInfo = getTensorTypeAndShape(firstOutput, arena);
            long elementCount = getElementCount(tensorInfo, arena);
            lib.releaseQuietly(99, tensorInfo); // ReleaseTensorTypeAndShapeInfo = 99

            float[] result = dataPtr.reinterpret(elementCount * ValueLayout.JAVA_FLOAT.byteSize())
                    .toArray(ValueLayout.JAVA_FLOAT);

            for (int i = 0; i < numInputs; i++) {
                lib.releaseQuietly(IDX_RELEASE_VALUE, inputValues.getAtIndex(ADDRESS, i));
            }
            for (int i = 0; i < numOutputs; i++) {
                lib.releaseQuietly(IDX_RELEASE_VALUE, outputValues.getAtIndex(ADDRESS, i));
            }

            return result;
        }

        MemorySegment[] runRaw(String[] inputNames, MemorySegment[] inputValues,
                               String[] outputNames, Arena arena) {
            int numInputs = inputNames.length;
            int numOutputs = outputNames.length;

            MemorySegment inputNamePtrs = arena.allocate(ADDRESS, numInputs);
            MemorySegment inputValuePtrs = arena.allocate(ADDRESS, numInputs);
            for (int i = 0; i < numInputs; i++) {
                inputNamePtrs.setAtIndex(ADDRESS, i, arena.allocateFrom(inputNames[i]));
                inputValuePtrs.setAtIndex(ADDRESS, i, inputValues[i]);
            }

            MemorySegment outputNamePtrs = arena.allocate(ADDRESS, numOutputs);
            for (int i = 0; i < numOutputs; i++) {
                outputNamePtrs.setAtIndex(ADDRESS, i, arena.allocateFrom(outputNames[i]));
            }

            MemorySegment outputValuePtrs = arena.allocate(ADDRESS, numOutputs);
            outputValuePtrs.fill((byte) 0);

            lib.checkStatus(lib.callVtable(IDX_RUN,
                    FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS, JAVA_LONG, ADDRESS),
                    session, MemorySegment.NULL, inputNamePtrs, inputValuePtrs, (long) numInputs,
                    outputNamePtrs, (long) numOutputs, outputValuePtrs));

            MemorySegment[] results = new MemorySegment[numOutputs];
            for (int i = 0; i < numOutputs; i++) {
                results[i] = outputValuePtrs.getAtIndex(ADDRESS, i);
            }
            return results;
        }

        MemorySegment createTensor(MemorySegment data, long dataBytes, long[] shape, int dataType, Arena arena) {
            MemorySegment shapeSeg = arena.allocateFrom(JAVA_LONG, shape);
            MemorySegment valueOut = arena.allocate(ADDRESS);
            lib.checkStatus(lib.callVtable(IDX_CREATE_TENSOR_WITH_DATA,
                    FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS, JAVA_LONG, JAVA_INT, ADDRESS),
                    memInfo, data, dataBytes, shapeSeg, (long) shape.length, dataType, valueOut));
            return valueOut.get(ADDRESS, 0);
        }

        MemorySegment getTensorData(MemorySegment value, Arena arena) {
            MemorySegment dataOut = arena.allocate(ADDRESS);
            lib.checkStatus(lib.callVtable(IDX_GET_TENSOR_MUTABLE_DATA,
                    FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS),
                    value, dataOut));
            return dataOut.get(ADDRESS, 0);
        }

        void releaseValue(MemorySegment value) {
            lib.releaseQuietly(IDX_RELEASE_VALUE, value);
        }

        long tensorElementCount(MemorySegment value, Arena arena) {
            MemorySegment tensorInfo = getTensorTypeAndShape(value, arena);
            long          count      = getElementCount(tensorInfo, arena);
            lib.releaseQuietly(99, tensorInfo);
            return count;
        }


        private int queryCount(int vtableIndex) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment countOut = arena.allocate(JAVA_LONG);
                lib.checkStatus(lib.callVtable(vtableIndex,
                        FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS),
                        session, countOut));
                return (int) countOut.get(JAVA_LONG, 0);
            }
        }

        private String queryName(int vtableIndex, int index) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment nameOut = arena.allocate(ADDRESS);
                lib.checkStatus(lib.callVtable(vtableIndex,
                        FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG, ADDRESS, ADDRESS),
                        session, (long) index, allocator, nameOut));
                MemorySegment namePtr = nameOut.get(ADDRESS, 0);
                String name = namePtr.reinterpret(256).getString(0);
                lib.callVtableVoid(IDX_ALLOCATOR_FREE,
                        FunctionDescriptor.ofVoid(ADDRESS, ADDRESS),
                        allocator, namePtr);
                return name;
            }
        }

        private MemorySegment getTensorTypeAndShape(MemorySegment value, Arena arena) {
            MemorySegment out = arena.allocate(ADDRESS);
            lib.checkStatus(lib.callVtable(65, // GetTensorTypeAndShape
                    FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS),
                    value, out));
            return out.get(ADDRESS, 0);
        }

        private long getElementCount(MemorySegment tensorTypeAndShape, Arena arena) {
            MemorySegment out = arena.allocate(JAVA_LONG);
            lib.checkStatus(lib.callVtable(64, // GetTensorShapeElementCount
                    FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS),
                    tensorTypeAndShape, out));
            return out.get(JAVA_LONG, 0);
        }

        @Override
        public void close() {
            if (closed) {return;}
            closed = true;
            lib.releaseQuietly(IDX_RELEASE_SESSION, session);
            lib.releaseQuietly(IDX_RELEASE_MEMORY_INFO, memInfo);}
    }

    static final int FLOAT = ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT;
    static final int INT64 = ONNX_TENSOR_ELEMENT_DATA_TYPE_INT64;
    static final int BOOL  = 9;

}
