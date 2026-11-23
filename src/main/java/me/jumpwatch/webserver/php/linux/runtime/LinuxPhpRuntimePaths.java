package me.jumpwatch.webserver.php.linux.runtime;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Resolves runtime paths without assuming /home/container or ~/plugins/webplugin. */
public final class LinuxPhpRuntimePaths {
    private final Path serverRoot;
    private final Path dataRoot;
    private final Path phpRoot;
    private final Path nginxRoot;

    public LinuxPhpRuntimePaths(LinuxPhpRuntimeOptions options) {
        this.serverRoot = resolveServerRoot(options.getServerLocation());
        this.dataRoot = resolveDataRoot(options.getDataFolder(), serverRoot);
        this.phpRoot = dataRoot.resolve("phplinux").resolve("bin").resolve("php8").normalize();
        this.nginxRoot = dataRoot.resolve("nginxlinux").resolve("bin").resolve("nginx").normalize();
    }

    public Path serverRoot() {
        return serverRoot;
    }

    public Path dataRoot() {
        return dataRoot;
    }

    public Path phpRoot() {
        return phpRoot;
    }

    public Path nginxRoot() {
        return nginxRoot;
    }

    public Path phpFpmExecutable() {
        return phpRoot.resolve("sbin").resolve("php-fpm");
    }

    public Path phpCliExecutable() {
        return phpRoot.resolve("bin").resolve("php");
    }

    public Path phpIni() {
        return phpRoot.resolve("bin").resolve("php.ini");
    }

    public Path generatedPhpIni() {
        return phpRoot.resolve("etc").resolve("webplugin.generated.php.ini");
    }

    public Path extensionPacksRoot() {
        return phpRoot.resolve("extension-packs");
    }

    public Path phpFpmConfig() {
        return phpRoot.resolve("etc").resolve("php-fpm.conf");
    }

    public Path phpFpmPoolConfig() {
        return phpRoot.resolve("etc").resolve("php-fpm.d").resolve("www.conf");
    }

    public Path nginxExecutable() {
        return nginxRoot.resolve("sbin").resolve("nginx");
    }

    public Path nginxConfig() {
        return nginxRoot.resolve("conf").resolve("nginx.conf");
    }

    public Path bundledGlibcLoader() {
        return phpRoot.resolve("lib").resolve("glibc").resolve("lib").resolve("ld-linux-x86-64.so.2");
    }

    public Path resolveRuntimeRelativePath(String configuredPath) {
        if (configuredPath == null || configuredPath.trim().isEmpty()) {
            return phpRoot;
        }
        Path path = Paths.get(configuredPath.trim());
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return dataRoot.resolve(path).normalize();
    }

    private static Path resolveServerRoot(String configuredServerLocation) {
        if (configuredServerLocation == null || configuredServerLocation.trim().isEmpty()) {
            return Paths.get("").toAbsolutePath().normalize();
        }
        return Paths.get(configuredServerLocation.trim()).toAbsolutePath().normalize();
    }

    private static Path resolveDataRoot(File dataFolder, Path serverRoot) {
        Path dataPath = dataFolder.toPath();
        if (dataPath.isAbsolute()) {
            return dataPath.normalize();
        }
        return serverRoot.resolve(dataPath).normalize();
    }
}
