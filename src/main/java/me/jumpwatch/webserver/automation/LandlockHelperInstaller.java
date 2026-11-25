package me.jumpwatch.webserver.automation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Deploys the CI-built Linux helper embedded inside the WebPlugin JAR.
 * Only the default plugin-managed location is modified; custom paths stay untouched.
 * No native build tools or external network access are required on the server.
 */
public final class LandlockHelperInstaller {
    private static final String RESOURCE = "/native/linux-amd64/webplugin-landlock";
    private static final String FILE_NAME = "webplugin-landlock";
    private static final Set<PosixFilePermission> EXECUTABLE = EnumSet.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE);

    private LandlockHelperInstaller() {}

    /**
     * Resolves the sandbox helper, deploying the embedded Linux amd64 helper on demand.
     * This is intentionally independent of Security.Landlock.Enabled: support is
     * detected on every startup before the administrator chooses to enforce it.
     */
    public static Path prepare(Path dataRoot, Path serverRoot, String configuredPath,
                               Consumer<String> log) {
        if (configuredPath != null && !configuredPath.isBlank()) {
            Path custom = Path.of(configuredPath.trim());
            return (custom.isAbsolute() ? custom : serverRoot.resolve(custom))
                    .toAbsolutePath().normalize();
        }
        Path managed = dataRoot.resolve("bin").resolve(FILE_NAME).toAbsolutePath().normalize();
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")) {
            return managed;
        }
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (!arch.equals("amd64") && !arch.equals("x86_64")) {
            log.accept("No bundled Landlock helper for architecture " + arch
                    + "; Linux x86-64 is currently supported.");
            return managed;
        }
        try {
            Files.createDirectories(managed.getParent());
            if (Files.isSymbolicLink(managed)) {
                log.accept("Landlock helper path is a symbolic link; refusing to install over it: " + managed);
                return managed;
            }

            try (InputStream source = LandlockHelperInstaller.class.getResourceAsStream(RESOURCE)) {
                if (source == null) {
                    // Local source builds may not carry a precompiled helper. Preserve
                    // administrator-provided helpers and attempt to fix upload permissions.
                    if (Files.isRegularFile(managed, LinkOption.NOFOLLOW_LINKS))
                        makeExecutable(managed);
                    else
                        log.accept("This JAR has no bundled Linux helper; use the CI artifact or set HelperPath.");
                    return managed;
                }

                Path temporary = Files.createTempFile(managed.getParent(), ".landlock-", ".tmp");
                boolean installed = false;
                try {
                    Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
                    makeExecutable(temporary);
                    if (!Files.isExecutable(temporary))
                        throw new IOException("Cannot make bundled Landlock helper executable at " + managed.getParent());

                    if (Files.isRegularFile(managed, LinkOption.NOFOLLOW_LINKS) && sameContent(temporary, managed)) {
                        makeExecutable(managed);
                    } else {
                        try {
                            Files.move(temporary, managed, StandardCopyOption.ATOMIC_MOVE,
                                    StandardCopyOption.REPLACE_EXISTING);
                        } catch (AtomicMoveNotSupportedException ex) {
                            Files.move(temporary, managed, StandardCopyOption.REPLACE_EXISTING);
                        }
                        installed = true;
                        log.accept("Installed bundled Linux Landlock helper: " + managed);
                    }
                } finally {
                    Files.deleteIfExists(temporary);
                }
                if (!Files.isExecutable(managed))
                    log.accept("Landlock helper exists but is not executable: " + managed
                            + " (check Pterodactyl filesystem permissions).");
                else if (!installed)
                    log.accept("Bundled Landlock helper verified and executable: " + managed);
            }
        } catch (IOException | SecurityException e) {
            // The Landlock probe and the configured preferred/required mode determine
            // whether a native subprocess may start; never silently claim enforcement.
            log.accept("Could not prepare bundled Landlock helper: " + e.getMessage());
        }
        return managed;
    }

    private static boolean sameContent(Path a, Path b) throws IOException {
        // Files.mismatch is available on Java 12+ (WebPlugin targets Java 15).
        return Files.mismatch(a, b) == -1L;
    }

    private static void makeExecutable(Path file) throws IOException {
        try {
            Files.setPosixFilePermissions(file, EXECUTABLE);
        } catch (UnsupportedOperationException ex) {
            if (!file.toFile().setExecutable(true, false))
                throw new IOException("Filesystem does not support executable permissions for " + file, ex);
        }
    }
}
