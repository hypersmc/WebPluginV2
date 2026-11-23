package me.jumpwatch.webserver.php.linux.runtime;

import me.jumpwatch.webserver.utils.WPLogger;
import me.jumpwatch.webserver.automation.LandlockSandbox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Shared Linux PHP/NGINX launcher used by Bukkit, Bungee and Velocity.
 *
 * The modular runtime contract is:
 * - bin/php8/runtime-manifest.properties defines base native library paths
 * - bin/php8/extension-packs/<name>/extension.properties defines optional modules
 * - config.yml decides which extension packs are enabled
 * - this class generates php.ini and LD_LIBRARY_PATH before php-fpm starts
 */
public final class LinuxPhpProcessRunner {
    private final LinuxPhpRuntimeOptions options;
    private final LinuxPhpRuntimePaths paths;
    private final LandlockSandbox sandbox;
    private final Path websiteRoot;
    private static final Object PHP_INI_LOCK = new Object();

    public LinuxPhpProcessRunner(LinuxPhpRuntimeOptions options) {
        this(options, null, null);
    }

    public LinuxPhpProcessRunner(LinuxPhpRuntimeOptions options, LandlockSandbox sandbox, Path websiteRoot) {
        this.options = options;
        this.paths = new LinuxPhpRuntimePaths(options);
        this.sandbox = sandbox;
        this.websiteRoot = websiteRoot;
    }

    /** Build a PHP CLI invocation using exactly the same extensions and native loader as PHP-FPM. */
    public ProcessBuilder buildPhpCliCommand(Path workingDirectory, List<String> arguments) throws IOException {
        if (!Files.isRegularFile(paths.phpCliExecutable()))
            throw new IOException("Bundled PHP CLI not found: " + paths.phpCliExecutable());
        PhpRuntimeManifest manifest = PhpRuntimeManifest.load(paths.phpRoot());
        Map<String, PhpExtensionPack> packs = PhpExtensionPack.discover(manifest.extensionPacksRoot(paths.phpRoot()));
        List<String> extensions = resolveEnabledExtensions();
        Path ini;
        synchronized (PHP_INI_LOCK) { ini = writeGeneratedPhpIni(extensions, packs); }
        List<String> libraries = buildLibraryPathEntries(extensions, packs, manifest);
        List<String> command = bundledRuntimeCommand(paths.phpCliExecutable(), libraries);
        command.add("-c");
        command.add(ini.toString());
        command.addAll(arguments);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDirectory.toFile());
        putLibraryPath(builder, libraries);
        return builder;
    }

    /** Landlock is restricted to subprocesses, not to the Minecraft JVM. */
    private ProcessBuilder protect(ProcessBuilder builder, LandlockSandbox.Role role) throws IOException {
        return sandbox == null ? builder : sandbox.protect(builder, role,
                paths.dataRoot(), websiteRoot, paths.phpRoot(), paths.nginxRoot());
    }

    public int startPhpFpm() throws IOException, InterruptedException {
        PhpRuntimeManifest manifest = PhpRuntimeManifest.load(paths.phpRoot());
        Map<String, PhpExtensionPack> extensionPacks = PhpExtensionPack.discover(manifest.extensionPacksRoot(paths.phpRoot()));
        List<String> enabledExtensions = resolveEnabledExtensions();
        Path phpIni;
        synchronized (PHP_INI_LOCK) { phpIni = writeGeneratedPhpIni(enabledExtensions, extensionPacks); }
        List<String> libraryPaths = buildLibraryPathEntries(enabledExtensions, extensionPacks, manifest);

        logRuntimeSummary(manifest, enabledExtensions, extensionPacks);
        if (options.isPreflightCheck()) {
            verifyPhpCli(phpIni, libraryPaths);
        }

        List<String> command = bundledRuntimeCommand(paths.phpFpmExecutable(), libraryPaths);
        command.add("-p");
        command.add(paths.phpRoot().toString());
        command.add("-c");
        command.add(phpIni.toString());
        command.add("-y");
        command.add(paths.phpFpmConfig().toString());

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(paths.phpFpmExecutable().getParent().toFile());
        putLibraryPath(builder, libraryPaths);
        return runAndLog("php-fpm", protect(builder, LandlockSandbox.Role.PHP_FPM));
    }

    public int startNginx() throws IOException, InterruptedException {
        PhpRuntimeManifest manifest = PhpRuntimeManifest.load(paths.phpRoot());
        List<String> libraryPaths = buildNginxLibraryPathEntries(manifest);
        List<String> command = bundledRuntimeCommand(paths.nginxExecutable(), libraryPaths);
        command.add("-c");
        command.add(paths.nginxConfig().toString());
        command.add("-p");
        command.add(paths.nginxRoot().toString());

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(paths.nginxExecutable().getParent().toFile());
        putLibraryPath(builder, libraryPaths);
        return runAndLog("nginx", protect(builder, LandlockSandbox.Role.NGINX));
    }

    public LinuxPhpRuntimePaths paths() {
        return paths;
    }

    private List<String> bundledRuntimeCommand(Path executable, List<String> libraryPaths) {
        List<String> command = new ArrayList<>();
        Path loader = paths.bundledGlibcLoader();
        if (Files.isRegularFile(loader)) {
            command.add(loader.toString());
            command.add("--library-path");
            command.add(joinPathList(libraryPaths));
            command.add(executable.toString());
        } else {
            command.add(executable.toString());
        }
        return command;
    }

    private List<String> buildLibraryPathEntries(List<String> extensions, Map<String, PhpExtensionPack> extensionPacks, PhpRuntimeManifest manifest) {
        Set<String> entries = new LinkedHashSet<>();

        for (Path basePath : manifest.baseLibraryPaths(paths.phpRoot())) {
            addIfDirectory(entries, basePath);
        }

        // Compatibility fallback for older WebPlugin runtime archives.
        addIfDirectory(entries, paths.phpRoot().resolve("lib"));
        addIfDirectory(entries, paths.phpRoot().resolve("lib").resolve("base"));
        addIfDirectory(entries, paths.phpRoot().resolve("lib").resolve("glibc").resolve("lib"));

        for (String configuredPath : options.getExtraLibraryPaths()) {
            addIfDirectory(entries, paths.resolveRuntimeRelativePath(configuredPath));
        }

        Map<String, List<String>> extensionPathMap = new LinkedHashMap<>();
        extensionPathMap.putAll(defaultExtensionLibraryPaths());
        extensionPathMap.putAll(options.getExtensionLibraryPaths());

        for (String extension : extensions) {
            String normalized = LinuxPhpRuntimeOptions.normalizeExtensionName(extension);
            PhpExtensionPack pack = extensionPacks.get(normalized);
            if (pack != null) {
                for (Path libraryPath : pack.getLibraryPaths()) {
                    addIfDirectory(entries, libraryPath);
                }
            }

            List<String> configuredPaths = extensionPathMap.get(normalized);
            if (configuredPaths != null) {
                for (String configuredPath : configuredPaths) {
                    addIfDirectory(entries, paths.resolveRuntimeRelativePath(configuredPath));
                }
            }
        }

        return new ArrayList<>(entries);
    }

    private List<String> buildNginxLibraryPathEntries(PhpRuntimeManifest manifest) {
        Set<String> entries = new LinkedHashSet<>();

        // NGINX is built separately, but it still needs the bundled runtime libs in
        // stripped Minecraft containers. Use the same manifest base libraries as PHP,
        // but do not add PHP extension-pack libs because NGINX never loads those.
        for (Path basePath : manifest.baseLibraryPaths(paths.phpRoot())) {
            addIfDirectory(entries, basePath);
        }

        // Allow future NGINX archives to carry their own libs without another code change.
        addIfDirectory(entries, paths.nginxRoot().resolve("lib"));
        addIfDirectory(entries, paths.nginxRoot().resolve("lib").resolve("base"));
        addIfDirectory(entries, paths.nginxRoot().resolve("lib").resolve("glibc").resolve("lib"));

        // Compatibility fallback for older WebPlugin runtime archives.
        addIfDirectory(entries, paths.phpRoot().resolve("lib"));
        addIfDirectory(entries, paths.phpRoot().resolve("lib").resolve("base"));
        addIfDirectory(entries, paths.phpRoot().resolve("lib").resolve("glibc").resolve("lib"));

        for (String configuredPath : options.getExtraLibraryPaths()) {
            addIfDirectory(entries, paths.resolveRuntimeRelativePath(configuredPath));
        }

        return new ArrayList<>(entries);
    }

    private List<String> resolveEnabledExtensions() throws IOException {
        List<String> extensions = new ArrayList<>(options.getEnabledExtensions());
        if (extensions.isEmpty() && options.isDetectExtensionsFromPhpIni()) {
            extensions.addAll(PhpIniExtensionResolver.readEnabledExtensions(paths.phpIni()));
        }
        List<String> normalized = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String extension : extensions) {
            String value = LinuxPhpRuntimeOptions.normalizeExtensionName(extension);
            if (!value.isEmpty() && seen.add(value)) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private Path writeGeneratedPhpIni(List<String> enabledExtensions, Map<String, PhpExtensionPack> extensionPacks) throws IOException {
        Path generated = paths.generatedPhpIni();
        Files.createDirectories(generated.getParent());

        List<String> lines = new ArrayList<>();
        Path basePhpIni = Files.isRegularFile(paths.phpIni()) ? paths.phpIni() : paths.phpRoot().resolve("etc").resolve("php.ini");
        if (Files.isRegularFile(basePhpIni)) {
            for (String rawLine : Files.readAllLines(basePhpIni, StandardCharsets.UTF_8)) {
                String trimmed = rawLine.trim().toLowerCase();
                if (trimmed.startsWith("extension=")
                        || trimmed.startsWith("zend_extension=")
                        || trimmed.startsWith("extension_dir")) {
                    continue;
                }
                lines.add(rawLine);
            }
        }

        lines.add("");
        lines.add("; ---- WebPlugin generated extension section ----");
        lines.add("; Override PHP's compiled extension_dir so phpinfo()/ini_get() does not leak");
        lines.add("; the build-machine install prefix from the precompiled runtime archive.");
        lines.add("extension_dir=\"" + paths.extensionPacksRoot().normalize() + "\"");
        for (String extension : enabledExtensions) {
            PhpExtensionPack pack = extensionPacks.get(extension);
            if (pack != null) {
                lines.add(pack.getIniLine());
            } else {
                lines.add("extension=" + extension + ".so");
                WPLogger.warn("No extension pack manifest found for PHP extension " + extension + "; falling back to extension=" + extension + ".so");
            }
        }
        lines.add("; ---- End WebPlugin generated extension section ----");
        Files.write(generated, lines, StandardCharsets.UTF_8);
        return generated;
    }

    private void verifyPhpCli(Path phpIni, List<String> libraryPaths) throws IOException, InterruptedException {
        if (!Files.isRegularFile(paths.phpCliExecutable())) {
            throw new IOException("PHP executable not found: " + paths.phpCliExecutable());
        }

        List<String> command = bundledRuntimeCommand(paths.phpCliExecutable(), libraryPaths);
        command.add("-c");
        command.add(phpIni.toString());
        command.add("-m");

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(paths.phpCliExecutable().getParent().toFile());
        putLibraryPath(builder, libraryPaths);
        CommandResult result = runAndCapture(protect(builder, LandlockSandbox.Role.TASK));
        if (result.exitCode != 0) {
            throw new IOException("PHP runtime preflight failed with exit code " + result.exitCode + ":\n" + result.output);
        }
        if (options.isDebug()) {
            WPLogger.info("PHP runtime preflight succeeded. Loaded modules:\n" + result.output);
        }
    }

    private void logRuntimeSummary(PhpRuntimeManifest manifest, List<String> enabledExtensions, Map<String, PhpExtensionPack> packs) {
        if (manifest.isPresent()) {
            WPLogger.info("Using WebPlugin PHP runtime " + manifest.getPhpVersion() + " (" + manifest.getLayout() + ", " + manifest.getRuntimeId() + ", profile " + manifest.getExtensionProfile() + ")");
        } else {
            WPLogger.warn("No runtime-manifest.properties found. Falling back to legacy PHP runtime behavior.");
        }
        WPLogger.info("Enabled PHP extensions: " + (enabledExtensions.isEmpty() ? "none" : enabledExtensions));
        for (String extension : enabledExtensions) {
            if (!packs.containsKey(extension)) {
                WPLogger.warn("Configured PHP extension has no extension pack: " + extension);
            }
        }
    }

    /** Compatibility defaults for older WebPlugin runtime archives. */
    private static Map<String, List<String>> defaultExtensionLibraryPaths() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put("mbstring", list("phplinux/bin/php8/lib/libonig/lib"));
        result.put("mbregex", list("phplinux/bin/php8/lib/libonig/lib"));
        result.put("zlib", list("phplinux/bin/php8/lib/libz/lib"));
        result.put("zip", list("phplinux/bin/php8/lib/libz/lib"));
        result.put("curl", list("phplinux/bin/php8/lib/libz/lib"));
        result.put("pdo_sqlite", list("phplinux/bin/php8/lib/libz/lib"));
        result.put("sqlite3", list("phplinux/bin/php8/lib/libz/lib"));
        result.put("opcache", list("phplinux/bin/php8/lib/libz/lib"));
        return result;
    }

    private static List<String> list(String value) {
        List<String> result = new ArrayList<>();
        result.add(value);
        return result;
    }

    private static void addIfDirectory(Set<String> entries, Path path) {
        if (Files.isDirectory(path)) {
            entries.add(path.normalize().toString());
        }
    }

    private static void putLibraryPath(ProcessBuilder builder, List<String> libraryPaths) {
        String newPath = joinPathList(libraryPaths);
        String existing = builder.environment().get("LD_LIBRARY_PATH");
        if (existing != null && !existing.trim().isEmpty()) {
            newPath = newPath + ":" + existing;
        }
        builder.environment().put("LD_LIBRARY_PATH", newPath);
    }

    private static String joinPathList(List<String> libraryPaths) {
        StringJoiner joiner = new StringJoiner(":");
        for (String libraryPath : libraryPaths) {
            joiner.add(libraryPath);
        }
        return joiner.toString();
    }

    private int runAndLog(String name, ProcessBuilder builder) throws IOException, InterruptedException {
        builder.redirectErrorStream(true);
        Process process = builder.start();
        StringBuilder output = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }

        int exitCode = process.waitFor();
        WPLogger.info(name + " exit code: " + exitCode);
        if (output.length() > 0) {
            WPLogger.info(name + " output: " + output);
        }
        process.destroy();
        return exitCode;
    }

    private CommandResult runAndCapture(ProcessBuilder builder) throws IOException, InterruptedException {
        builder.redirectErrorStream(true);
        Process process = builder.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        int exitCode = process.waitFor();
        process.destroy();
        return new CommandResult(exitCode, output.toString());
    }

    private static final class CommandResult {
        private final int exitCode;
        private final String output;

        private CommandResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }
}
