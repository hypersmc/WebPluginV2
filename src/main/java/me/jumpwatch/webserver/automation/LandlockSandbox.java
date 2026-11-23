package me.jumpwatch.webserver.automation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** WebPlugin-wide, opt-in Linux Landlock protection for NGINX, PHP-FPM and tasks. */
public final class LandlockSandbox {
    public enum Mode { PREFERRED, REQUIRED }
    public enum Role { NGINX, PHP_FPM, TASK }
    public enum Availability { AVAILABLE, NO_HELPER, UNSUPPORTED, BLOCKED, ERROR }

    private final boolean enabled;
    private final Mode mode;
    private final Path helper;
    private final Consumer<String> logger;
    private final Availability availability;
    private final String probeDetail;

    public LandlockSandbox(boolean enabled, Mode mode, Path helper, Consumer<String> logger) {
        this.enabled = enabled;
        this.mode = mode;
        this.helper = helper.toAbsolutePath().normalize();
        this.logger = logger;
        Probe probe = probe(this.helper);
        this.availability = probe.availability;
        this.probeDetail = probe.detail;
        logger.accept("Landlock: " + availability + " (" + probeDetail + "), enabled=" + enabled + ", mode=" + mode);
        if (!enabled) logger.accept("Landlock is recommended for WebPlugin. Enable Security.Landlock.Enabled after installing the helper.");
        else if (availability != Availability.AVAILABLE) logger.accept("WARNING: Landlock is enabled but unavailable; " +
                (mode == Mode.REQUIRED ? "affected web processes will not launch" : "preferred mode will use unsandboxed compatibility fallback"));
    }

    public Availability getAvailability() { return availability; }
    public boolean isEnabled() { return enabled; }
    public String getProbeDetail() { return probeDetail; }

    /**
     * The helper applies Landlock and no_new_privs before exec, never to the JVM.
     * The fallback is allowed only when availability was definitively absent at boot.
     * An unexpected helper/ruleset failure is never retried without sandboxing.
     */
    public ProcessBuilder protect(ProcessBuilder original, Role role,
                                  Path pluginData, Path websiteRoot, Path phpRuntime, Path nginxRuntime) throws IOException {
        if (!enabled) return original;
        if (availability != Availability.AVAILABLE) {
            if (mode == Mode.REQUIRED) throw new IOException("Landlock is required but unavailable: " + availability + " (" + probeDetail + ")");
            return original;
        }
        List<String> command = new ArrayList<>();
        command.add(helper.toString());
        Set<Path> rx = new LinkedHashSet<>();
        Set<Path> ro = new LinkedHashSet<>();
        Set<Path> rw = new LinkedHashSet<>();
        for (String path : new String[]{"/lib", "/lib64", "/usr", "/bin", "/sbin"}) addIfExists(rx, Path.of(path));
        for (String path : new String[]{"/etc/ssl", "/etc/hosts", "/etc/resolv.conf", "/etc/nsswitch.conf",
                "/etc/localtime", "/dev/urandom", "/dev/null", "/proc/self"}) addIfExists(ro, Path.of(path));
        addIfExists(rx, phpRuntime);
        addIfExists(rx, nginxRuntime);
        addIfExists(rw, pluginData);
        addIfExists(rw, websiteRoot);
        addIfExists(rw, Path.of(System.getProperty("java.io.tmpdir", "/tmp")));

        // Keep compatibility broad INSIDE the website, but not across the Minecraft filesystem.
        // For NGINX we can narrow the site root to read-only; log/temp paths stay writable.
        if (role == Role.NGINX && websiteRoot != null) {
            rw.remove(websiteRoot.toAbsolutePath().normalize());
            addIfExists(ro, websiteRoot);
        }
        for (Path p : rx) { command.add("--rx"); command.add(p.toString()); }
        for (Path p : ro) { command.add("--ro"); command.add(p.toString()); }
        for (Path p : rw) { command.add("--rw"); command.add(p.toString()); }
        command.add("--");
        command.addAll(original.command());
        ProcessBuilder result = new ProcessBuilder(command);
        result.directory(original.directory());
        result.environment().putAll(original.environment());
        result.redirectErrorStream(original.redirectErrorStream());
        return result;
    }

    private static void addIfExists(Set<Path> paths, Path path) {
        if (path == null) return;
        Path normalized = path.toAbsolutePath().normalize();
        if (Files.exists(normalized)) paths.add(normalized);
    }

    private static final class Probe {
        final Availability availability;
        final String detail;
        Probe(Availability availability, String detail) { this.availability = availability; this.detail = detail; }
    }

    private static Probe probe(Path helper) {
        if (!Files.isRegularFile(helper) || !Files.isExecutable(helper))
            return new Probe(Availability.NO_HELPER, "helper missing/not executable: " + helper);
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux"))
            return new Probe(Availability.UNSUPPORTED, "not Linux");
        try {
            Process process = new ProcessBuilder(helper.toString(), "--probe")
                    .redirectErrorStream(true).start();
            boolean exited = process.waitFor(4, TimeUnit.SECONDS);
            if (!exited) { process.destroyForcibly(); return new Probe(Availability.ERROR, "probe timed out"); }
            String response = new String(process.getInputStream().readNBytes(500), StandardCharsets.UTF_8).trim();
            if (process.exitValue() == 0 && response.startsWith("AVAILABLE "))
                return new Probe(Availability.AVAILABLE, response);
            if (process.exitValue() == 10 || process.exitValue() == 11) return new Probe(Availability.UNSUPPORTED, response);
            if (process.exitValue() == 12) return new Probe(Availability.BLOCKED, response);
            return new Probe(Availability.ERROR, "exit " + process.exitValue() + ": " + response);
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            return new Probe(Availability.ERROR, ex.toString());
        }
    }
}
