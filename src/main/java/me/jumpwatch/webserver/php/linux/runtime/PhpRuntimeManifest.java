package me.jumpwatch.webserver.php.linux.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/** Reads bin/php8/runtime-manifest.properties from the modular WebPlugin PHP runtime. */
public final class PhpRuntimeManifest {
    private final boolean present;
    private final String runtimeId;
    private final String phpVersion;
    private final String layout;
    private final String extensionProfile;
    private final String extensionPacksPath;
    private final List<String> baseLibraryPaths;
    private final boolean bundledGlibc;

    private PhpRuntimeManifest(
            boolean present,
            String runtimeId,
            String phpVersion,
            String layout,
            String extensionProfile,
            String extensionPacksPath,
            List<String> baseLibraryPaths,
            boolean bundledGlibc
    ) {
        this.present = present;
        this.runtimeId = runtimeId;
        this.phpVersion = phpVersion;
        this.layout = layout;
        this.extensionProfile = extensionProfile;
        this.extensionPacksPath = extensionPacksPath;
        this.baseLibraryPaths = Collections.unmodifiableList(new ArrayList<>(baseLibraryPaths));
        this.bundledGlibc = bundledGlibc;
    }

    public static PhpRuntimeManifest load(Path phpRoot) throws IOException {
        Path manifest = phpRoot.resolve("runtime-manifest.properties");
        if (!Files.isRegularFile(manifest)) {
            return legacy();
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(manifest)) {
            properties.load(input);
        }

        return new PhpRuntimeManifest(
                true,
                properties.getProperty("runtime_id", "unknown").trim(),
                properties.getProperty("php_version", "unknown").trim(),
                properties.getProperty("layout", "unknown").trim(),
                properties.getProperty("extension_profile", "unknown").trim(),
                properties.getProperty("extension_packs", "extension-packs").trim(),
                splitPathList(properties.getProperty("base_library_path", "lib/glibc/lib:lib/base")),
                Boolean.parseBoolean(properties.getProperty("glibc_bundled", "false").trim())
        );
    }

    private static PhpRuntimeManifest legacy() {
        List<String> basePaths = new ArrayList<>();
        basePaths.add("lib");
        basePaths.add("lib/glibc/lib");
        basePaths.add("lib/base");
        return new PhpRuntimeManifest(false, "legacy", "unknown", "legacy", "legacy", "extension-packs", basePaths, false);
    }

    private static List<String> splitPathList(String value) {
        List<String> result = new ArrayList<>();
        if (value == null || value.trim().isEmpty()) {
            return result;
        }
        for (String entry : value.split("[:,]")) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    public boolean isPresent() {
        return present;
    }

    public String getRuntimeId() {
        return runtimeId;
    }

    public String getPhpVersion() {
        return phpVersion;
    }

    public String getLayout() {
        return layout;
    }

    public String getExtensionProfile() {
        return extensionProfile;
    }

    public boolean isBundledGlibc() {
        return bundledGlibc;
    }

    public Path extensionPacksRoot(Path phpRoot) {
        return resolveRuntimePath(phpRoot, extensionPacksPath);
    }

    public List<Path> baseLibraryPaths(Path phpRoot) {
        List<Path> result = new ArrayList<>();
        for (String baseLibraryPath : baseLibraryPaths) {
            result.add(resolveRuntimePath(phpRoot, baseLibraryPath));
        }
        return result;
    }

    private static Path resolveRuntimePath(Path phpRoot, String configuredPath) {
        Path path = phpRoot.getFileSystem().getPath(configuredPath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return phpRoot.resolve(path).normalize();
    }
}
