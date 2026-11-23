package me.jumpwatch.webserver.php.linux;

import me.jumpwatch.webserver.utils.DebugLogger;
import me.jumpwatch.webserver.utils.WPLogger;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Installs the Linux PHP/NGINX runtime without relying on wget or hardcoded /home/container paths. */
public final class PhpInstaller {
    private static final String DEFAULT_REPO = "https://github.com/hypersmc/WebPluginV2Repo";
    private static final String DEFAULT_PHP_ARCHIVE = "WebPlugin-linux-PHP8.4.8-MODULAR.tar.gz";
    private static final String DEFAULT_NGINX_ARCHIVE = "WebPlugin-linux-NGINX-1.27.1-PRECOMPILED.tar.gz";

    private PhpInstaller() {
    }

    /** Deprecated compatibility entrypoint. Use installphp(File, String, String, String, boolean). */
    public static void installphp() {
        WPLogger.warn("PhpInstaller.installphp() was called without a data folder. This legacy entrypoint cannot install safely on proxy platforms.");
    }

    public static boolean installphp(File dataFolder, String repo, String phpArchiveName, String nginxArchiveName, boolean debug) {
        boolean phpInstalled = installPhpRuntime(dataFolder, repo, phpArchiveName, debug);
        boolean nginxInstalled = installNginxRuntime(dataFolder, repo, nginxArchiveName, debug);
        return phpInstalled && nginxInstalled;
    }

    public static boolean installPhpRuntime(File dataFolder, String repo, String phpArchiveName, boolean debug) {
        Path phplinux = dataFolder.toPath().resolve("phplinux").normalize();
        Path phpRoot = phplinux.resolve("bin").resolve("php8").normalize();
        String archiveName = defaultIfBlank(phpArchiveName, DEFAULT_PHP_ARCHIVE);

        try {
            Files.createDirectories(phplinux);
            Path archive = download(resourceUrl(repo, archiveName), phplinux.resolve(archiveName));
            extractTarGz(archive, phplinux);
            ensurePhpRuntimeFiles(phpRoot);
            FilePermissions(dataFolder);
            WPLogger.info("PHP runtime installed at " + phpRoot);
            return true;
        } catch (Exception e) {
            if (debug) {
                DebugLogger.error(e.getMessage());
            }
            WPLogger.warn("Failed to install PHP runtime: " + e.getMessage());
            return false;
        }
    }

    public static boolean installNginxRuntime(File dataFolder, String repo, String nginxArchiveName, boolean debug) {
        Path nginxlinux = dataFolder.toPath().resolve("nginxlinux").normalize();
        Path nginxRoot = nginxlinux.resolve("bin").resolve("nginx").normalize();
        String archiveName = defaultIfBlank(nginxArchiveName, DEFAULT_NGINX_ARCHIVE);

        try {
            Files.createDirectories(nginxlinux);
            Path archive = download(resourceUrl(repo, archiveName), nginxlinux.resolve(archiveName));
            extractTarGz(archive, nginxlinux);
            ensureNginxRuntimeFiles(nginxRoot);
            FilePermissionsNGINX(dataFolder);
            WPLogger.info("NGINX runtime installed at " + nginxRoot);
            return true;
        } catch (Exception e) {
            if (debug) {
                DebugLogger.error(e.getMessage());
            }
            WPLogger.warn("Failed to install NGINX runtime: " + e.getMessage());
            return false;
        }
    }

    private static Path download(String url, Path destination) throws IOException, URISyntaxException {
        Files.createDirectories(destination.getParent());
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        WPLogger.info("Downloading " + url);
        try (InputStream input = new URI(url).toURL().openStream()) {
            Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        return destination;
    }

    private static String resourceUrl(String repo, String archiveNameOrUrl) {
        String archive = defaultIfBlank(archiveNameOrUrl, DEFAULT_PHP_ARCHIVE);
        if (archive.startsWith("https://") || archive.startsWith("http://")) {
            return archive;
        }

        String base = defaultIfBlank(repo, DEFAULT_REPO);
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }

        if (base.contains("raw.githubusercontent.com") || base.contains("/raw/")) {
            return base + "/" + archive;
        }
        if (base.contains("github.com")) {
            return base + "/raw/main/" + archive;
        }
        return base + "/" + archive;
    }

    private static void extractTarGz(Path archive, Path destination) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("tar");
        command.add("-xzf");
        command.add(archive.toString());
        command.add("-C");
        command.add(destination.toString());

        ProcessBuilder builder = new ProcessBuilder(command);
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
        if (exitCode != 0) {
            throw new IOException("tar failed with exit code " + exitCode + ":\n" + output);
        }
    }

    private static void ensurePhpRuntimeFiles(Path phpRoot) throws IOException {
        Path php = phpRoot.resolve("bin").resolve("php");
        Path phpFpm = phpRoot.resolve("sbin").resolve("php-fpm");
        if (!Files.isRegularFile(php)) {
            throw new IOException("PHP binary missing after extract: " + php);
        }
        if (!Files.isRegularFile(phpFpm)) {
            throw new IOException("php-fpm binary missing after extract: " + phpFpm);
        }

        copyIfMissing(phpRoot.resolve("etc").resolve("php-fpm.conf.default"), phpRoot.resolve("etc").resolve("php-fpm.conf"));
        copyIfMissing(phpRoot.resolve("etc").resolve("php-fpm.d").resolve("www.conf.default"), phpRoot.resolve("etc").resolve("php-fpm.d").resolve("www.conf"));
        Files.createDirectories(phpRoot.resolve("log"));
        touch(phpRoot.resolve("log").resolve("php-fpm.log"));
    }

    private static void ensureNginxRuntimeFiles(Path nginxRoot) throws IOException {
        Path nginx = nginxRoot.resolve("sbin").resolve("nginx");
        if (!Files.isRegularFile(nginx)) {
            throw new IOException("NGINX binary missing after extract: " + nginx);
        }
        Files.createDirectories(nginxRoot.resolve("logs"));
        touch(nginxRoot.resolve("logs").resolve("error.log"));
        touch(nginxRoot.resolve("logs").resolve("access.log"));
    }

    private static void copyIfMissing(Path source, Path target) throws IOException {
        if (Files.isRegularFile(source) && !Files.exists(target)) {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void touch(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        if (!Files.exists(path)) {
            Files.createFile(path);
        }
    }

    public static void FilePermissions() {
        FilePermissions(new File("plugins/webplugin"));
    }

    public static void FilePermissions(File dataFolder) {
        Path phpRoot = dataFolder.toPath().resolve("phplinux").resolve("bin").resolve("php8").normalize();
        markExecutable(phpRoot.resolve("bin").resolve("php"));
        markExecutable(phpRoot.resolve("sbin").resolve("php-fpm"));
        markExecutable(phpRoot.resolve("lib").resolve("glibc").resolve("lib").resolve("ld-linux-x86-64.so.2"));
        markExecutableRecursive(phpRoot.resolve("extension-packs"));
        markExecutableRecursive(phpRoot.resolve("lib"));
        FolderPermissions(dataFolder);
    }

    public static void FilePermissionsNGINX() {
        FilePermissionsNGINX(new File("plugins/webplugin"));
    }

    public static void FilePermissionsNGINX(File dataFolder) {
        Path nginxRoot = dataFolder.toPath().resolve("nginxlinux").resolve("bin").resolve("nginx").normalize();
        markExecutable(nginxRoot.resolve("sbin").resolve("nginx"));
    }

    public static void FilePermissionsGLIBC() {
        FilePermissions(new File("plugins/webplugin"));
    }

    public static void FilePermissionsLIBZ() {
        FilePermissions(new File("plugins/webplugin"));
    }

    public static void FilePermissionsLIBONIG() {
        FilePermissions(new File("plugins/webplugin"));
    }

    public static void FolderPermissions() {
        FolderPermissions(new File("plugins/webplugin"));
    }

    public static void FolderPermissions(File dataFolder) {
        File phpFolder = dataFolder.toPath().resolve("php").toFile();
        if (!phpFolder.exists()) {
            return;
        }
        phpFolder.setReadable(true, false);
        phpFolder.setWritable(true, true);
        phpFolder.setExecutable(true, false);
    }

    private static void markExecutableRecursive(Path root) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (java.util.stream.Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile).forEach(PhpInstaller::markExecutableIfNativeRuntimeFile);
        } catch (IOException e) {
            WPLogger.warn("Could not update permissions under " + root + ": " + e.getMessage());
        }
    }

    private static void markExecutableIfNativeRuntimeFile(Path path) {
        String filename = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (filename.endsWith(".so") || filename.contains(".so.") || filename.startsWith("ld-linux")) {
            markExecutable(path);
        }
    }

    private static void markExecutable(Path path) {
        File file = path.toFile();
        if (!file.exists()) {
            return;
        }
        file.setExecutable(true, false);
        file.setReadable(true, false);
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }
}
