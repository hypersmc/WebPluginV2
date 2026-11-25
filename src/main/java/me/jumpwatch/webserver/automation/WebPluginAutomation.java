package me.jumpwatch.webserver.automation;

import me.jumpwatch.webserver.php.linux.runtime.LinuxPhpProcessRunner;
import me.jumpwatch.webserver.php.linux.runtime.LinuxPhpRuntimeOptions;
import me.jumpwatch.webserver.php.linux.runtime.LinuxPhpRuntimePaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Shared 4.2B integration point for Bukkit, BungeeCord and Velocity.
 * The Landlock probe runs once on startup, without restricting the Minecraft JVM.
 * Each native web process and configured PHP task is restricted separately.
 */
public final class WebPluginAutomation implements AutoCloseable {
    private final LandlockSandbox sandbox;
    private final LinuxPhpProcessRunner runtime;
    private final TaskEngine tasks;
    private final SandboxFilesystemSelfTest filesystemSelfTest;
    private final Path websiteRoot;
    private final Consumer<String> log;
    private volatile boolean tasksStarted;

    public WebPluginAutomation(LinuxPhpRuntimeOptions options, String indexLocation,
                               boolean landlockEnabled, String landlockMode, String helperLocation,
                               boolean tasksEnabled, int maxProcesses, List<? extends Map<?, ?>> taskConfigs,
                               Consumer<String> log) throws IOException {
        this.log = Objects.requireNonNull(log);
        LinuxPhpRuntimePaths paths = new LinuxPhpRuntimePaths(options);
        Path dataRoot = paths.dataRoot();
        String suffix = indexLocation == null ? "" : indexLocation;
        // Match WebPlugin 4.1R's configured NGINX webroot convention exactly.
        this.websiteRoot = Path.of(dataRoot.toString() + "/php" + suffix).toAbsolutePath().normalize();
        Path helper = LandlockHelperInstaller.prepare(
                dataRoot, paths.serverRoot(), helperLocation, log);
        LandlockSandbox.Mode mode;
        try {
            mode = LandlockSandbox.Mode.valueOf(
                    (landlockMode == null || landlockMode.isBlank() ? "preferred" : landlockMode)
                    .trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            // An invalid security option must not downgrade an explicitly enabled sandbox.
            mode = LandlockSandbox.Mode.REQUIRED;
            log.accept("Invalid Security.Landlock.Mode; using required rather than disabling protection.");
        }
        this.sandbox = new LandlockSandbox(landlockEnabled, mode, helper, log);
        this.runtime = new LinuxPhpProcessRunner(options, sandbox, websiteRoot);
        this.filesystemSelfTest = new SandboxFilesystemSelfTest(sandbox, runtime, websiteRoot, log);

        TaskEngine engine = null;
        if (tasksEnabled) {
            try {
                engine = new TaskEngine(dataRoot, websiteRoot, paths.phpRoot(), paths.nginxRoot(),
                        maxProcesses, runtime::buildPhpCliCommand, sandbox, log);
                engine.load(taskConfigs == null ? List.of() : taskConfigs, paths.serverRoot(), websiteRoot);
                log.accept("Configured " + engine.definitions().size() + " PHP deployment task(s).");
            } catch (Exception ex) {
                if (engine != null) engine.close();
                engine = null;
                log.accept("ERROR: Deployment tasks disabled (invalid configuration): " + ex.getMessage());
            }
        }
        this.tasks = engine;
    }

    public LandlockSandbox sandbox() { return sandbox; }
    public LinuxPhpProcessRunner runtime() { return runtime; }
    public Path websiteRoot() { return websiteRoot; }
    public TaskEngine tasks() { return tasks; }

    /**
     * Call after bundled PHP has been installed; never blocks on the worker process.
     * PHP must not be silently replaced with system php if the configured binary is missing.
     */
    public synchronized void startTasksIfReady() {
        if (tasks == null || tasksStarted) return;
        if (!Files.isRegularFile(runtime.paths().phpCliExecutable())) {
            log.accept("Deployment tasks not started: bundled PHP CLI is not installed.");
            return;
        }
        tasksStarted = true;
        try {
            tasks.autoStart();
        } catch (RuntimeException ex) {
            log.accept("Could not automatically start all deployment tasks: " + ex.getMessage());
        }
    }

    /** Administrative command responses, suitable for console and permission-gated platform adapters. */
    public List<String> command(List<String> args) {
        List<String> lines = new ArrayList<>();
        if (args == null || args.isEmpty()) {
            return List.of("Usage: tasks list|status [id]|logs <id>|start <id>|stop <id>");
        }
        String action = args.get(0).toLowerCase(Locale.ROOT);
        if (action.equals("sandbox")) {
            if (args.size() >= 2 && args.get(1).equalsIgnoreCase("test"))
                return List.of(filesystemSelfTest.start());
            if (args.size() >= 2 && args.get(1).equalsIgnoreCase("result"))
                return List.of("Filesystem self-test: " + filesystemSelfTest.result());
            return List.of("Landlock: " + (sandbox.isEnabled() ? "enabled" : "disabled")
                            + ", availability=" + sandbox.getAvailability() + ", " + sandbox.getProbeDetail(),
                    "Filesystem self-test: " + filesystemSelfTest.result(),
                    "Run sandbox test to check PHP-FPM and task filesystem isolation.");
        }
        if (tasks == null) return List.of("Deployment tasks are disabled or misconfigured.");
        try {
            switch (action) {
                case "list":
                    if (tasks.definitions().isEmpty()) return List.of("No deployment tasks configured.");
                    for (TaskDefinition definition : tasks.definitions())
                        lines.add(definition.id + ": " + definition.kind + ", run=" + definition.run
                                + ", instances=" + definition.instances);
                    break;
                case "status":
                    for (ProcessSupervisor.Snapshot snapshot : tasks.snapshots()) {
                        if (args.size() < 2 || snapshot.id.equals(args.get(1))
                                || snapshot.id.startsWith(args.get(1) + "#"))
                            lines.add(snapshot.id + ": " + snapshot.state + " pid=" + snapshot.pid
                                    + " exit=" + snapshot.lastExitCode + " restarts=" + snapshot.restarts);
                    }
                    if (lines.isEmpty()) lines.add("No matching task processes.");
                    break;
                case "logs":
                    if (args.size() < 2) return List.of("Usage: tasks logs <id>");
                    for (ProcessSupervisor.Snapshot snapshot : tasks.snapshots())
                        if (snapshot.id.equals(args.get(1))) {
                            List<String> tail = snapshot.logTail;
                            return tail.subList(Math.max(0, tail.size() - 20), tail.size());
                        }
                    return List.of("No logs for " + args.get(1));
                case "start":
                    if (args.size() < 2) return List.of("Usage: tasks start <id>");
                    tasks.start(args.get(1));
                    lines.add("Task " + args.get(1) + " submitted.");
                    break;
                case "stop":
                    if (args.size() < 2) return List.of("Usage: tasks stop <id>");
                    tasks.stop(args.get(1));
                    lines.add("Stop requested for " + args.get(1) + ".");
                    break;
                default:
                    lines.add("Usage: tasks list|status [id]|logs <id>|start <id>|stop <id>");
            }
        } catch (RuntimeException ex) {
            lines.add("Task command failed: " + ex.getMessage());
        }
        return lines;
    }

    @Override public void close() {
        filesystemSelfTest.close();
        if (tasks != null) tasks.close();
    }
}
