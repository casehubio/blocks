package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.RecognitionStream;
import io.casehub.blocks.speech.SynthesisOptions;
import io.casehub.blocks.speech.TranscriptionOptions;
import io.casehub.blocks.speech.TranscriptionResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SpeechCli {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            usage();
            return;
        }

        String command = args[0];
        switch (command) {
            case "transcribe" -> transcribe(args);
            case "synthesise", "synthesize" -> synthesise(args);
            case "stream" -> stream(args);
            case "listen" -> listen(args);
            default -> usage();
        }
    }

    private static void transcribe(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("Usage: transcribe <model-dir> <audio.wav>");
            System.exit(1);
        }
        Path modelDir = Path.of(args[1]);
        Path audioFile = Path.of(args[2]);
        String lang = args.length > 3 ? args[3] : null;

        var config = SherpaConfig.defaults(modelDir);
        var stt = new SherpaOnnxSpeechToText(config);
        var options = new TranscriptionOptions("wav", lang, "tiny");

        long start = System.currentTimeMillis();
        TranscriptionResult result = stt.transcribe(audioFile, options);
        long elapsed = System.currentTimeMillis() - start;

        System.out.println(result.text().trim());
        System.err.printf("[%dms]%n", elapsed);
    }

    private static void synthesise(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("Usage: synthesise <model-dir> <text> [output.wav]");
            System.exit(1);
        }
        Path modelDir = Path.of(args[1]);
        String text = args[2];
        Path output = args.length > 3 ? Path.of(args[3]) : Path.of("output.wav");

        var config = SherpaConfig.defaults(modelDir);
        var tts = new SherpaOnnxTextToSpeech(config);

        long start = System.currentTimeMillis();
        var result = tts.synthesise(text, SynthesisOptions.defaults());
        long elapsed = System.currentTimeMillis() - start;

        Files.write(output, result.audioData());
        System.out.println(output.toAbsolutePath());
        System.err.printf("[%dms, %d bytes]%n", elapsed, result.audioData().length);
    }

    private static void stream(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("Usage: stream <model-dir> <audio.wav>");
            System.exit(1);
        }
        Path modelDir = Path.of(args[1]);
        Path audioFile = Path.of(args[2]);

        float[] samples = readWavSamples(audioFile);
        int sampleRate = readWavSampleRate(audioFile);

        var config = SherpaConfig.defaults(modelDir);
        var stt = new SherpaOnnxStreamingSpeechToText(config);

        int chunkSize = sampleRate / 10; // 100ms
        String last = "";

        try (RecognitionStream rs = stt.startStream(TranscriptionOptions.defaults())) {
            for (int offset = 0; offset < samples.length; offset += chunkSize) {
                int len = Math.min(chunkSize, samples.length - offset);
                float[] chunk = new float[len];
                System.arraycopy(samples, offset, chunk, 0, len);
                rs.acceptSamples(chunk, sampleRate);

                String partial = rs.partialResult().trim();
                if (!partial.equals(last) && !partial.isEmpty()) {
                    double t = (offset + len) / (double) sampleRate;
                    System.out.printf("[%.1fs] %s%n", t, partial);
                    last = partial;
                }

                if (rs.isEndpointDetected()) {
                    System.out.println("--- endpoint ---");
                }
            }
            System.out.println();
            System.out.println(rs.finalResult().text().trim());
        }
        stt.close();
    }

    private static void listen(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: listen <model-dir>");
            System.exit(1);
        }
        Path modelDir = Path.of(args[1]);

        var config = SherpaConfig.defaults(modelDir);
        var stt    = new SherpaOnnxStreamingSpeechToText(config);

        System.err.println("Listening... (press Enter to stop)");

        try (RecognitionStream rs = stt.startStream(TranscriptionOptions.defaults());
             MicrophoneCapture mic = MicrophoneCapture.openDefault(rs)) {

            mic.start();
            String last = "";

            while (System.in.available() == 0) {
                String partial = rs.partialResult().trim();
                if (!partial.equals(last) && !partial.isEmpty()) {
                    System.out.printf("\r> %s", partial);
                    System.out.flush();
                    last = partial;
                }
                if (rs.isEndpointDetected()) {
                    if (!last.isEmpty()) {
                        System.out.println();
                        last = "";
                    }
                }
                Thread.sleep(100);
            }
            System.in.read();

            mic.stop();
            String finalText = rs.finalResult().text().trim();
            if (!finalText.isEmpty()) {
                System.out.println();
                System.out.println(finalText);
            }
        }
        stt.close();
    }


    private static float[] readWavSamples(Path path) throws IOException {
        byte[] data = Files.readAllBytes(path);
        var buf = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.position(40);
        int dataSize = buf.getInt();
        int count = dataSize / 2;
        float[] samples = new float[count];
        for (int i = 0; i < count; i++) samples[i] = buf.getShort() / 32768f;
        return samples;
    }

    private static int readWavSampleRate(Path path) throws IOException {
        byte[] data = Files.readAllBytes(path);
        var buf = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.position(24);
        return buf.getInt();
    }

    private static void usage() {
        System.err.println("CaseHub Speech CLI");
        System.err.println();
        System.err.println("Commands:");
        System.err.println("  transcribe <model-dir> <audio.wav> [lang]   Offline STT (Whisper)");
        System.err.println("  synthesise <model-dir> <text> [output.wav]  TTS (VITS/Piper)");
        System.err.println("  stream     <model-dir> <audio.wav>          Streaming STT (Zipformer)");
        System.err.println("  listen     <model-dir>                      Live microphone STT (Zipformer)");
        System.err.println();
        System.err.println("Example:");
        System.err.println("  java --enable-native-access=ALL-UNNAMED -cp speech-sherpa.jar \\");
        System.err.println("    io.casehub.blocks.speech.sherpa.SpeechCli transcribe ./whisper-tiny test.wav");
        System.exit(1);
    }
}
