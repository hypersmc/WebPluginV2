package me.jumpwatch.webserver.automation;

import me.jumpwatch.webserver.php.linux.runtime.LinuxPhpProcessRunner;
import me.jumpwatch.webserver.php.linux.runtime.LinuxPhpRuntimePaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Opt-in, administrator-triggered filesystem self-test.
 *
 * Uses the bundled PHP CLI with PHP-FPM and task Landlock profiles. No PHP
 * source is uploaded into the website and no existing Minecraft file is read:
 * the JVM creates a throwaway sentinel in the server root instead.
 *
 * Runs asynchronously so a slow native process never blocks a Minecraft tick.
 */
public final class SandboxFilesystemSelfTest implements AutoCloseable {
    private static final int TIMEOUT_SECONDS = 12;
    private static final String PROBE =
            "$target=$argv[1];$outside=$argv[2];$expected='webplugin-sandbox-test';"
            + "if (@file_put_contents($target,$expected)!==strlen($expected)) {echo 'WRITE_FAILED',PHP_EOL;exit(21);}"
            + "if (@file_get_contents($target)!==$expected) {echo 'WRITE_VERIFY_FAILED',PHP_EOL;exit(22);}"
            + "echo 'WRITE_OK',PHP_EOL;"
            + "if (@file_get_contents($outside)!==false) {echo 'OUTSIDE_READ_ALLOWED',PHP_EOL;exit(23);}"
            + "$error=error_get_last();"
            + "if (!$error || stripos((string)$error['message'],'Permission denied')===false) {"
            + "echo 'OUTSIDE_DENIAL_UNCONFIRMED',PHP_EOL;exit(24);}"
            + "echo 'OUTSIDE_BLOCKED',PHP_EOL;exit(0);";

    private final LandlockSandbox sandbox;
    private final LinuxPhpProcessRunner runtime;
    private final LinuxPhpRuntimePaths paths;
    private final Path websiteRoot;
    private final Consumer<String> logger;
    private final AtomicBoolean running = new AtomicBoolean();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "webplugin-sandbox-selftest");
        thread.setDaemon(true);
        return thread;
    });
    private volatile String result = "Not run; use sandbox test.";
    private volatile boolean closed;

    public SandboxFilesystemSelfTest(LandlockSandbox sandbox,
                                     LinuxPhpProcessRunner runtime,
                                     Path websiteRoot, Consumer<String> logger) {
        this.sandbox = sandbox;
        this.runtime = runtime;
        this.paths = runtime.paths();
        this.websiteRoot = websiteRoot;
        this.logger = logger;
    }

    public String result() {
        return result;
    }

    public synchronized String start() {
        if (closed) return "Sandbox self-test unavailable: WebPlugin is shutting down.";
        if (!sandbox.isEnabled())
            return "Sandbox self-test skipped: enable Security.Landlock.Enabled and restart first.";
        if (sandbox.getAvailability() != LandlockSandbox.Availability.AVAILABLE)
            return "Sandbox self-test skipped: Landlock is " + sandbox.getAvailability()
                    + " (" + sandbox.getProbeDetail() + ").";
        if (!Files.isRegularFile(paths.phpCliExecutable()))
            return "Sandbox self-test unavailable: bundled PHP CLI is not installed.";
        if (!running.compareAndSet(false, true))
            return "Sandbox self-test already running. Use sandbox result to check progress.";
        result = "RUNNING (two PHP subprocesses; no changes to existing Minecraft files)";
        try {
            executor.execute(() -> {
                try {
                    result = runChecks();
                } catch (Exception ex) {
                    result = "FAILED: could not run sandbox checks (" + ex.getClass().getSimpleName()
                            + ": " + safe(ex.getMessage()) + ")";
                } finally {
                    running.set(false);
                    logger.accept("Sandbox filesystem self-test: " + result);
                }
            });
        } catch (RejectedExecutionException ex) {
            running.set(false);
            result = "FAILED: sandbox test executor is stopped.";
            return result;
        }
        return "Sandbox self-test started asynchronously. Use sandbox result to see the results.";
    }

    private String runChecks() throws Exception {
        Path server = paths.serverRoot().toRealPath();
        Path data = paths.dataRoot().toRealPath();
        Path website = websiteRoot.toRealPath();
        if (!data.startsWith(server) || data.equals(server)
                || !website.startsWith(data) || website.equals(server)) {
            return "INCONCLUSIVE: server root and WebPlugin website paths are not safely separated.";
        }

        // Our compatibility profile permits the entire configured OS temp directory.
        // A Minecraft server rooted there cannot prove separation using this profile.
        Path systemTmp = Path.of(System.getProperty("java.io.tmpdir", "/tmp"))
                .toAbsolutePath().normalize().toRealPath();
        if (server.startsWith(systemTmp))
            return "INCONCLUSIVE: server root lies under the sandbox's allowed temp directory.";

        Path sentinel = null;
        Path workDir = null;
        Path output = null;
        try {
            // Temporary, harmless control file, created by the JVM in Minecraft's
            // root. The JVM must be able to read it; PHP under Landlock must not.
            sentinel = Files.createTempFile(server, ".webplugin-sandbox-sentinel-", ".txt");
            String marker = "temporary-webplugin-sentinel";
            Files.writeString(sentinel, marker, StandardCharsets.UTF_8);
            if (!marker.equals(Files.readString(sentinel, StandardCharsets.UTF_8)))
                return "INCONCLUSIVE: could not read the temporary Minecraft-root sentinel.";

            // The only PHP write target is a short-lived, random directory under
            // the configured website root; no PHP file or endpoint is published.
            workDir = Files.createTempDirectory(website, ".webplugin-sandbox-selftest-");
            output = Files.createTempFile(data, ".webplugin-sandbox-output-", ".tmp");
            StringBuilder summary = new StringBuilder();
            for (LandlockSandbox.Role role : List.of(
                    LandlockSandbox.Role.PHP_FPM, LandlockSandbox.Role.TASK)) {
                Path allowed = workDir.resolve("write-" + role.name().toLowerCase(Locale.ROOT) + ".txt");
                ProcessBuilder processBuilder = runtime.buildPhpCliCommand(
                        website, List.of("-r", PROBE, "--", allowed.toString(), sentinel.toString()));
                processBuilder = sandbox.protect(processBuilder, role, data,
                        website, paths.phpRoot(), paths.nginxRoot());
                processBuilder.redirectErrorStream(true);
                processBuilder.redirectOutput(output.toFile());

                Process process = processBuilder.start();
                try {
                    process.getOutputStream().close();
                } catch (IOException ignored) { }
                if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    process.descendants().forEach(ProcessHandle::destroyForcibly);
                    process.destroyForcibly();
                    return "FAILED: " + role + " PHP subprocess timed out.";
                }
                String text;
                try (InputStream stream = Files.newInputStream(output)) {
                    text = new String(stream.readNBytes(2048), StandardCharsets.UTF_8);
                }
                int code = process.exitValue();
                if (code != 0 || !text.contains("WRITE_OK") || !text.contains("OUTSIDE_BLOCKED")) {
                    return "FAILED: " + role + " filesystem probe (exit " + code
                            + ", output " + safe(text) + ").";
                }
                if (!Files.isRegularFile(allowed)
                        || !Files.readString(allowed, StandardCharsets.UTF_8)
                                 .equals("webplugin-sandbox-test"))
                    return "FAILED: " + role + " did not write the expected site test file.";
                if (summary.length() != 0) summary.append("; ");
                summary.append(role).append(" site write PASS, Minecraft-root read BLOCKED");
            }
            return "PASS: " + summary
                    + ". No existing Minecraft files were opened by PHP.";
        } finally {
            if (workDir != null) {
                for (String role : List.of("php_fpm", "task"))
                    Files.deleteIfExists(workDir.resolve("write-" + role + ".txt"));
                Files.deleteIfExists(workDir);
            }
            if (output != null) Files.deleteIfExists(output);
            if (sentinel != null) Files.deleteIfExists(sentinel);
        }
    }

    private static String safe(String value) {
        if (value == null || value.isEmpty()) return "(no output)";
        return value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ')
                .substring(0, Math.min(160, value.length()));
    }

    @Override public void close() {
        closed = true;
        executor.shutdownNow();
    }
}
