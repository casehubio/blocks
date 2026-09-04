package io.casehub.blocks.speech.sherpa;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class NativeJarExtractor {

    private static final System.Logger LOG = System.getLogger("casehub-speech");

    private NativeJarExtractor() {}

    static boolean extractIfAvailable(Path targetDir) {
        if (Files.isDirectory(targetDir)) {
            return false;
        }

        String version = SherpaLibrary.VERSION;
        String platform = SherpaLibrary.platformId();
        String prefix = "META-INF/native/sherpa-onnx/" + version + "/" + platform + "/";

        String sherpaLib = SherpaLibrary.sherpaLibName();
        String onnxLib = SherpaLibrary.onnxRuntimeLibName();

        URL sherpaUrl = Thread.currentThread().getContextClassLoader()
                .getResource(prefix + sherpaLib);
        URL onnxUrl = Thread.currentThread().getContextClassLoader()
                .getResource(prefix + onnxLib);

        if (sherpaUrl == null || onnxUrl == null) {
            return false;
        }

        try {
            Files.createDirectories(targetDir.getParent());
            Path tempDir = Files.createTempDirectory(targetDir.getParent(), ".native-extract-");
            try {
                extractResource(prefix + sherpaLib, tempDir.resolve(sherpaLib));
                extractResource(prefix + onnxLib, tempDir.resolve(onnxLib));
                Files.move(tempDir, targetDir, StandardCopyOption.ATOMIC_MOVE);
                LOG.log(System.Logger.Level.INFO,
                        "Extracted native libs from classpath to {0}", targetDir);
                return true;
            } catch (Exception e) {
                deleteRecursively(tempDir);
                throw e;
            }
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING,
                    "Failed to extract native libs from classpath: {0}", e.getMessage());
            return false;
        }
    }

    static boolean extractResource(String resourcePath, Path target) {
        URL url = Thread.currentThread().getContextClassLoader()
                .getResource(resourcePath);
        if (url == null) {
            return false;
        }

        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = url.openStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to extract " + resourcePath, e);
        }
    }

    private static void deleteRecursively(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        } catch (IOException ignored) {}
    }
}
