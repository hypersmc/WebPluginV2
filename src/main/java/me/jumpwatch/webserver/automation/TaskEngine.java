package me.jumpwatch.webserver.automation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Platform-neutral deployment tasks and Laravel worker manager. */
public final class TaskEngine implements AutoCloseable {
    @FunctionalInterface
    public interface PhpCliBuilder {
        ProcessBuilder build(Path workingDirectory, List<String> phpArgs) throws Exception;
    }

    private final ProcessSupervisor supervisor;
    private final TaskLedger ledger;
    private final PhpCliBuilder phpCliBuilder;
    private final LandlockSandbox sandbox;
    private final Path pluginData;
    private final Path websiteRoot;
    private final Path phpRuntime;
    private final Path nginxRuntime;
    private final Consumer<String> log;
    private final Map<String, TaskDefinition> definitions = new HashMap<>();
    private final ScheduledExecutorService reconciliation = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "webplugin-task-ledger");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, Integer> recordedExits = new HashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    public TaskEngine(Path pluginData, Path websiteRoot, Path phpRuntime, Path nginxRuntime,
                      int maxProcesses, PhpCliBuilder phpCliBuilder, LandlockSandbox sandbox,
                      Consumer<String> log) throws IOException {
        this.pluginData = pluginData;
        this.websiteRoot = websiteRoot;
        this.phpRuntime = phpRuntime;
        this.nginxRuntime = nginxRuntime;
        this.ledger = new TaskLedger(pluginData.resolve("tasks").resolve("state.properties"));
        this.supervisor = new ProcessSupervisor(maxProcesses, log);
        this.phpCliBuilder = phpCliBuilder;
        this.sandbox = sandbox;
        this.log = log;
        reconciliation.scheduleWithFixedDelay(this::reconcile, 400, 400, TimeUnit.MILLISECONDS);
    }

    public synchronized void load(List<? extends Map<?, ?>> configurations, Path serverRoot, Path defaultWebRoot) {
        if (!definitions.isEmpty()) throw new IllegalStateException("Task definitions cannot be reloaded while engine is running");
        for (Map<?, ?> entry : configurations) {
            TaskDefinition definition = TaskDefinition.fromMap(entry, serverRoot, defaultWebRoot);
            if (definitions.putIfAbsent(definition.id, definition) != null)
                throw new IllegalArgumentException("Duplicate task Id: " + definition.id);
        }
    }

    public synchronized void autoStart() {
        for (TaskDefinition task : definitions.values()) {
            if (task.kind == ProcessSupervisor.Kind.SERVICE && task.autoStart) start(task.id);
            else if (task.kind == ProcessSupervisor.Kind.ONESHOT && ledger.shouldRun(task)) start(task.id);
        }
    }

    /** For use by an authenticated console command; not a website HTTP endpoint. */
    public synchronized void start(String id) {
        if (closed.get()) throw new IllegalStateException("Task engine is closed");
        TaskDefinition task = definitions.get(id);
        if (task == null) throw new IllegalArgumentException("Unknown task Id: " + id);
        ProcessSupervisor.Spec spec = new ProcessSupervisor.Spec(task.id, task.kind, task.restart,
                task.instances, task.timeoutSeconds, task.successCodes, () -> {
                    ProcessBuilder original = phpCliBuilder.build(task.workingDirectory, task.arguments);
                    // Even if the PHP runtime factory has prepared its own environment, protect
                    // the resulting process (and descendants) before the first exec.
                    return sandbox.protect(original, LandlockSandbox.Role.TASK,
                            pluginData, task.workingDirectory, phpRuntime, nginxRuntime);
                });
        supervisor.start(spec);
    }

    public synchronized void stop(String id) {
        TaskDefinition task = definitions.get(id);
        if (task == null) throw new IllegalArgumentException("Unknown task Id: " + id);
        if (task.instances == 1) supervisor.stop(id);
        else for (int i = 1; i <= task.instances; i++) supervisor.stop(id + "#" + i);
    }

    public Collection<TaskDefinition> definitions() { return List.copyOf(definitions.values()); }
    public List<ProcessSupervisor.Snapshot> snapshots() { return supervisor.snapshots(); }

    private synchronized void reconcile() {
        if (closed.get()) return;
        for (TaskDefinition task : definitions.values()) {
            if (task.kind != ProcessSupervisor.Kind.ONESHOT) continue;
            ProcessSupervisor.Snapshot snapshot = supervisor.snapshot(task.id);
            if (snapshot == null || snapshot.state != ProcessSupervisor.State.COMPLETED || snapshot.lastExitCode == null)
                continue;
            Integer recorded = recordedExits.get(task.id);
            if (recorded != null && recorded.equals(snapshot.lastExitCode)) continue;
            try {
                ledger.completed(task, snapshot.lastExitCode);
                recordedExits.put(task.id, snapshot.lastExitCode);
            } catch (IOException ex) {
                log.accept("Failed to persist task completion for " + task.id + ": " + ex.getMessage());
            }
        }
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        reconciliation.shutdownNow();
        // Completion is normally persisted within 400 ms; one last synchronous pass
        // is intentionally omitted here because in-progress tasks must not be marked done.
        supervisor.close();
    }
}
