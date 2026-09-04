package io.casehub.blocks.speech.sherpa;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;

public final class NativePackager {

    private static final Set<String> VALID_PLATFORMS = Set.of(
            "osx-arm64", "osx-x64", "linux-x64", "linux-arm64", "win-x64");

    private NativePackager() {}

    public static void main(String[] args) {
        Path outputDir = Path.of(System.getProperty("project.build.outputDirectory",
                "target/classes"));
        packageNative(args, outputDir);
    }

    static void packageNative(String[] args, Path outputDir) {
        if (args.length < 1) {
            throw new IllegalArgumentException(
                    "Usage: NativePackager <platform ID>. "
                    + "Valid platforms: " + VALID_PLATFORMS);
        }

        String platformId = args[0];
        if (!VALID_PLATFORMS.contains(platformId)) {
            throw new IllegalArgumentException(
                    "Unknown platform: " + platformId
                    + ". Valid platforms: " + VALID_PLATFORMS);
        }

        Path nativeDir = Provisioner.nativeCacheDir(platformId);
        if (!Files.isDirectory(nativeDir)) {
            if (Provisioner.isAutoDownloadEnabled()) {
                Provisioner.ensureNativeLibrary();
                nativeDir = Provisioner.nativeCacheDir(platformId);
            }
            if (!Files.isDirectory(nativeDir)) {
                throw new IllegalStateException(
                        "Native libs not found at " + nativeDir
                        + ". Run with -Dcasehub.speech.auto-download=true or "
                        + "provision manually.");
            }
        }

        Path targetDir = outputDir.resolve("META-INF/native/sherpa-onnx")
                .resolve(SherpaLibrary.VERSION).resolve(platformId);

        try {
            Files.createDirectories(targetDir);
            copyPlatformLibs(nativeDir, targetDir, platformId);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to package native libs", e);
        }
    }

    private static void copyPlatformLibs(Path sourceDir, Path targetDir,
                                          String platformId) throws IOException {
        String sherpaLib = sherpaLibNameFor(platformId);
        String onnxLib = onnxRuntimeLibNameFor(platformId);

        Path sherpaSrc = sourceDir.resolve(sherpaLib);
        Path onnxSrc = sourceDir.resolve(onnxLib);

        if (!Files.exists(sherpaSrc)) {
            throw new IllegalStateException("Missing " + sherpaLib + " in " + sourceDir);
        }
        if (!Files.exists(onnxSrc)) {
            throw new IllegalStateException("Missing " + onnxLib + " in " + sourceDir);
        }

        Files.copy(sherpaSrc, targetDir.resolve(sherpaLib), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(onnxSrc, targetDir.resolve(onnxLib), StandardCopyOption.REPLACE_EXISTING);
    }

    static String sherpaLibNameFor(String platformId) {
        if (platformId.startsWith("osx")) return "libsherpa-onnx-c-api.dylib";
        if (platformId.startsWith("win")) return "sherpa-onnx-c-api.dll";
        return "libsherpa-onnx-c-api.so";
    }

    static String onnxRuntimeLibNameFor(String platformId) {
        if (platformId.startsWith("osx")) return "libonnxruntime.dylib";
        if (platformId.startsWith("win")) return "onnxruntime.dll";
        return "libonnxruntime.so";
    }
}
