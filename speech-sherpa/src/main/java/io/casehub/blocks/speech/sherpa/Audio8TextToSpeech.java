package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.SynthesisOptions;
import io.casehub.blocks.speech.SynthesisResult;
import io.casehub.blocks.speech.TextToSpeechService;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class Audio8TextToSpeech implements TextToSpeechService, AutoCloseable {

    @FunctionalInterface
    interface Generator {
        int[][] generate(String text, String referenceText, int[] voiceCodes,
                         Audio8Config config, Audio8Tokenizer tokenizer);
    }

    @FunctionalInterface
    interface Decoder {
        float[] decode(int[][] frames);
    }

    private final Audio8Tokenizer tokenizer;
    private final Audio8Config config;
    private final Generator generator;
    private final Decoder decoder;
    private final int sampleRate;
    private final VoiceRegistry voiceRegistry;
    private final int[]         defaultVoiceCodes;
    private final String        defaultReferenceText;


    Audio8TextToSpeech(Audio8Tokenizer tokenizer, Audio8Config config,
                       Generator generator, Decoder decoder,
                       int sampleRate, VoiceRegistry voiceRegistry) {this(tokenizer, config, generator, decoder, sampleRate, voiceRegistry, null, "");}

    Audio8TextToSpeech(Audio8Tokenizer tokenizer, Audio8Config config,
                       Generator generator, Decoder decoder,
                       int sampleRate, VoiceRegistry voiceRegistry,
                       int[] defaultVoiceCodes, String defaultReferenceText) {
        this.tokenizer            = tokenizer;
        this.config               = config;
        this.generator            = generator;
        this.decoder              = decoder;
        this.sampleRate           = sampleRate;
        this.voiceRegistry        = voiceRegistry;
        this.defaultVoiceCodes    = defaultVoiceCodes;
        this.defaultReferenceText = defaultReferenceText != null ? defaultReferenceText : "";
    }


    public static Audio8TextToSpeech withDefaults() {
        return withDefaults("0.1b");
    }

    public static Audio8TextToSpeech withDefaults(String variant) {
        Path modelDir = Provisioner.ensureAudio8Model(variant);
        return fromModelDir(modelDir, variant);
    }

    static Audio8TextToSpeech fromModelDir(Path modelDir, String variant) {
        try {
            var cfg      = Audio8Config.defaults(modelDir, variant);
            var manifest = RuntimeManifest.load(modelDir.resolve("runtime_manifest.json"));
            var tok      = Audio8Tokenizer.load(modelDir.resolve("tokenizer").resolve("tokenizer.json"));
            var ort      = OnnxRuntimeLibrary.load();

            var slowAr          = ort.createSession(modelDir.resolve(manifest.slowArFilename()), cfg.numThreads());
            var fastAr          = ort.createSession(modelDir.resolve(manifest.fastArFilename()), cfg.numThreads());
            var codecDecSession = ort.createSession(modelDir.resolve(manifest.codecDecoderFilename()), cfg.numThreads());

            var dualAr   = new DualARLoop(slowAr, fastAr, manifest);
            var codecDec = new CodecDecoder(codecDecSession, manifest.sampleRate());

            Generator gen = (text, refText, voiceCodes, config, tokenizer) ->
                                    dualAr.generate(text, refText, voiceCodes, config, tokenizer);
            Decoder dec = codecDec::decode;

            VoiceRegistry.VoiceEncoder voiceEncoder = audioData -> {
                Path encoderPath = modelDir.resolve("registration").resolve("codec_encoder_fp16.onnx");
                if (!Files.exists(encoderPath)) {
                    throw new SherpaException("Voice encoder not available — download registration package first");
                }
                var encoderSession = ort.createSession(encoderPath, cfg.numThreads());
                try {
                    return encodeVoice(encoderSession, audioData, manifest.numCodebooks());
                } finally {
                    encoderSession.close();
                }
            };

            int[] defaultCodes = null;
            if (manifest.referenceCodesFile() != null) {
                Path codesPath = modelDir.resolve(manifest.referenceCodesFile());
                if (Files.exists(codesPath)) {
                    defaultCodes = loadNpyCodes(codesPath);
                }
            }

            return new Audio8TextToSpeech(tok, cfg, gen, dec,
                                          manifest.sampleRate(), new VoiceRegistry(voiceEncoder),
                                          defaultCodes, manifest.referenceText());
        } catch (SherpaException e) {
            throw e;
        } catch (Exception e) {
            throw new SherpaException("Failed to initialize Audio8TextToSpeech from " + modelDir, e);
        }}

    @Override
    public SynthesisResult synthesise(String text, SynthesisOptions options) {
        int[]  voiceCodes = null;
        String refText    = "";
        if (options.voice() != null && !options.voice().isEmpty()) {
            voiceCodes = voiceRegistry.getVoiceCodes(options.voice());
        } else if (defaultVoiceCodes != null) {
            voiceCodes = defaultVoiceCodes;
            refText    = defaultReferenceText;
        }
        int[][] codecFrames = generator.generate(text, refText, voiceCodes, config, tokenizer);
        float[] samples     = decoder.decode(codecFrames);
        byte[]  wav         = WavWriter.encode(samples, sampleRate, 1);
        return new SynthesisResult(wav, "wav", List.of());}

    public String registerVoice(Path referenceAudio) {
        return voiceRegistry.register(referenceAudio);
    }

    public void releaseVoice(String voiceId) {
        voiceRegistry.release(voiceId);
    }

    public Set<String> registeredVoices() {
        return voiceRegistry.registeredVoices();
    }

    @Override
    public void close() {
        voiceRegistry.close();
    }

    private static int[] encodeVoice(OnnxRuntimeLibrary.Session encoder, byte[] audioData,
                                     int numCodebooks) {
        try {
            WavData wav     = WavReader.parse(audioData);
            float[] samples = wav.samples();

            if (wav.channels() > 1) {
                int     frames = samples.length / wav.channels();
                float[] mono   = new float[frames];
                for (int i = 0; i < frames; i++) {
                    float sum = 0;
                    for (int ch = 0; ch < wav.channels(); ch++) {
                        sum += samples[i * wav.channels() + ch];
                    }
                    mono[i] = sum / wav.channels();
                }
                samples = mono;
            }

            int padding = (2048 - (samples.length % 2048)) % 2048;
            if (padding > 0) {
                float[] padded = new float[samples.length + padding];
                System.arraycopy(samples, 0, padded, 0, samples.length);
                samples = padded;
            }

            try (var arena = Arena.ofConfined()) {
                MemorySegment audioSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, samples);
                MemorySegment audioTensor = encoder.createTensor(audioSeg,
                                                                 (long) samples.length * ValueLayout.JAVA_FLOAT.byteSize(),
                                                                 new long[]{1, 1, samples.length}, OnnxRuntimeLibrary.FLOAT, arena);

                String   inputName   = encoder.inputName(0);
                String[] outputNames = new String[encoder.outputCount()];
                for (int i = 0; i < outputNames.length; i++) {
                    outputNames[i] = encoder.outputName(i);
                }

                MemorySegment[] outputs = encoder.runRaw(
                        new String[]{inputName}, new MemorySegment[]{audioTensor},
                        outputNames, arena);
                try {
                    long count = encoder.tensorElementCount(outputs[0], arena);
                    MemorySegment codesPtr = encoder.getTensorData(outputs[0], arena)
                                                    .reinterpret(count * ValueLayout.JAVA_LONG.byteSize());
                    long[] rawCodes = codesPtr.toArray(ValueLayout.JAVA_LONG);
                    int[]  codes    = new int[rawCodes.length];
                    for (int i = 0; i < rawCodes.length; i++) {
                        codes[i] = (int) rawCodes[i];
                    }
                    return codes;
                } finally {
                    for (MemorySegment out : outputs) {encoder.releaseValue(out);}
                    encoder.releaseValue(audioTensor);
                }
            }
        } catch (IOException e) {
            throw new SherpaException("Failed to parse audio for voice encoding", e);
        }
    }

    static int[] loadNpyCodes(Path npyFile) throws IOException {
        byte[] data = Files.readAllBytes(npyFile);
        if (data.length < 10 || data[0] != (byte) 0x93 || data[1] != 'N' || data[2] != 'U'
            || data[3] != 'M' || data[4] != 'P' || data[5] != 'Y') {
            throw new IOException("Not a .npy file: invalid magic");
        }
        int major = data[6] & 0xFF;
        int headerLen;
        int headerStart;
        if (major <= 1) {
            headerLen   = (data[8] & 0xFF) | ((data[9] & 0xFF) << 8);
            headerStart = 10;
        } else {
            headerLen   = (data[8] & 0xFF) | ((data[9] & 0xFF) << 8)
                          | ((data[10] & 0xFF) << 16) | ((data[11] & 0xFF) << 24);
            headerStart = 12;
        }
        String header    = new String(data, headerStart, headerLen, java.nio.charset.StandardCharsets.US_ASCII);
        int    dataStart = headerStart + headerLen;

        boolean isUint16 = header.contains("'<u2'") || header.contains("'|u2'");
        boolean isInt64  = header.contains("'<i8'");
        boolean isInt32  = header.contains("'<i4'");

        int bytesPerElement;
        if (isUint16) {bytesPerElement = 2;} else if (isInt32) {bytesPerElement = 4;} else if (isInt64) {
            bytesPerElement = 8;
        } else {
            throw new IOException("Unsupported dtype in .npy header: " + header.trim());
        }

        int   totalElements = (data.length - dataStart) / bytesPerElement;
        int[] result        = new int[totalElements];
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(data, dataStart, data.length - dataStart)
                                                     .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < totalElements; i++) {
            if (isUint16) {result[i] = buf.getShort() & 0xFFFF;} else if (isInt32) {result[i] = buf.getInt();} else {
                result[i] = (int) buf.getLong();
            }
        }
        return result;
    }

}
