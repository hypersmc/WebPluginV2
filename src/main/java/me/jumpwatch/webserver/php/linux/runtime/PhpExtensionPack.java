package me.jumpwatch.webserver.php.linux.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Metadata for one optional PHP extension pack in bin/php8/extension-packs/<name>. */
public final class PhpExtensionPack {
    private final String name;
    private final String iniType;
    private final Path extensionFile;
    private final List<Path> libraryPaths;
    private final List<String> nativeLibraries;

    private PhpExtensionPack(String name, String iniType, Path extensionFile, List<Path> libraryPaths, List<String> nativeLibraries) {
        this.name = name;
        this.iniType = iniType;
        this.extensionFile = extensionFile;
        this.libraryPaths = Collections.unmodifiableList(new ArrayList<>(libraryPaths));
        this.nativeLibraries = Collections.unmodifiableList(new ArrayList<>(nativeLibraries));
    }

    public String getName() {
        return name;
    }

    public Path getExtensionFile() {
        return extensionFile;
    }

    public boolean isZendExtension() {
        return "zend_extension".equals(iniType);
    }

    public String getIniLine() {
        return iniType + "=" + extensionFile.normalize();
    }

    public List<Path> getLibraryPaths() {
        return libraryPaths;
    }

    public List<String> getNativeLibraries() {
        return nativeLibraries;
    }

    public static Map<String, PhpExtensionPack> discover(Path packsRoot) throws IOException {
        Map<String, PhpExtensionPack> packs = new LinkedHashMap<>();
        if (!Files.isDirectory(packsRoot)) {
            return packs;
        }
        try (java.util.stream.Stream<Path> children = Files.list(packsRoot)) {
            for (Path packDir : (Iterable<Path>) children::iterator) {
                if (!Files.isDirectory(packDir)) {
                    continue;
                }
                Path manifest = packDir.resolve("extension.properties");
                if (!Files.isRegularFile(manifest)) {
                    continue;
                }
                PhpExtensionPack pack = read(packDir, manifest);
                if (pack != null) {
                    packs.put(LinuxPhpRuntimeOptions.normalizeExtensionName(pack.getName()), pack);
                }
            }
        }
        return packs;
    }

    private static PhpExtensionPack read(Path packDir, Path manifest) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(manifest)) {
            properties.load(input);
        }

        String name = properties.getProperty("name", packDir.getFileName().toString()).trim();
        name = LinuxPhpRuntimeOptions.normalizeExtensionName(name);
        if (name.isEmpty()) {
            return null;
        }

        String iniType = properties.getProperty("ini_type", "extension").trim().toLowerCase();
        if (!"zend_extension".equals(iniType)) {
            iniType = "extension";
        }

        String extensionFileValue = properties.getProperty("extension_file", name + ".so").trim();
        Path extensionFile = packDir.resolve(extensionFileValue).normalize();
        if (!Files.isRegularFile(extensionFile)) {
            return null;
        }

        List<Path> libraryPaths = new ArrayList<>();
        String configuredLibraryPaths = properties.getProperty("library_paths", "libs");
        for (String rawPath : configuredLibraryPaths.split(",")) {
            String trimmed = rawPath.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Path libraryPath = packDir.resolve(trimmed).normalize();
            if (Files.isDirectory(libraryPath)) {
                libraryPaths.add(libraryPath);
            }
        }

        List<String> nativeLibraries = new ArrayList<>();
        String configuredNativeLibraries = properties.getProperty("native_libraries", "");
        for (String rawLibrary : configuredNativeLibraries.split(",")) {
            String trimmed = rawLibrary.trim();
            if (!trimmed.isEmpty()) {
                nativeLibraries.add(trimmed);
            }
        }

        return new PhpExtensionPack(name, iniType, extensionFile, libraryPaths, nativeLibraries);
    }
}
