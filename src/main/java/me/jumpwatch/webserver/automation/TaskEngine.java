package me.jumpwatch.webserver.automation;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
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
    private final Map<String, ScheduleState> scheduled = new HashMap<>();
    private boolean schedulesStarted;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "webplugin-cron-scheduler");
        thread.setDaemon(true);
        return thread;
    });
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
            if (definition.schedule != null)
                scheduled.put(definition.id, new ScheduleState(definition));
        }
    }

    public synchronized void autoStart() {
        if (!schedulesStarted) {
            schedulesStarted = true;
            for (ScheduleState state : scheduled.values()) {
                state.paused = !state.task.autoStart;
                state.reset();
            }
            // One lightweight timer; all executions go into the existing bounded supervisor.
            scheduler.scheduleWithFixedDelay(this::tick, 1, 1, TimeUnit.SECONDS);
        }
        for (TaskDefinition task : definitions.values()) {
            if (task.schedule != null) continue;
            try {
                if (task.kind == ProcessSupervisor.Kind.SERVICE && task.autoStart) start(task.id);
                else if (task.kind == ProcessSupervisor.Kind.ONESHOT && ledger.shouldRun(task)) start(task.id);
            } catch (RuntimeException ex) {
                log.accept("Task " + task.id + " did not auto-start: " + ex.getMessage());
            }
        }
    }

    private synchronized void tick() {
        if (closed.get()) return;
        Instant now = Instant.now();
        for (ScheduleState state : scheduled.values()) {
            if (state.paused || !state.due(now)) continue;
            ProcessSupervisor.Snapshot snapshot = supervisor.snapshot(state.task.id);
            if (snapshot != null && active(snapshot.state)) {
                log.accept("Scheduled PHP task " + state.task.id + " skipped: previous run still active");
                continue;
            }
            try {
                launch(state.task);
            } catch (RuntimeException ex) {
                // A missed tick is skipped; no unbounded replay or backlog.
                log.accept("Scheduled PHP task " + state.task.id + " skipped: "
                        + ex.getClass().getSimpleName());
            }
        }
    }

    private static boolean active(ProcessSupervisor.State state) {
        return state == ProcessSupervisor.State.QUEUED || state == ProcessSupervisor.State.STARTING ||
                state == ProcessSupervisor.State.RUNNING || state == ProcessSupervisor.State.RESTART_WAIT;
    }

    /** For administrators: scheduled start resumes ticks; other tasks start immediately. */
    public synchronized void start(String id) {
        TaskDefinition task = get(id);
        if (task.schedule != null) {
            ScheduleState state = scheduled.get(id);
            state.paused = false;
            state.reset(); // Resume with next interval / next cron minute; no catch-up.
            return;
        }
        launch(task);
    }

    /** Explicit one-time invocation, including scheduled tasks, without modifying the schedule. */
    public synchronized void run(String id) {
        launch(get(id));
    }

    private TaskDefinition get(String id) {
        if (closed.get()) throw new IllegalStateException("Task engine is closed");
        TaskDefinition task = definitions.get(id);
        if (task == null) throw new IllegalArgumentException("Unknown task Id: " + id);
        return task;
    }

    private void launch(TaskDefinition task) {
        recordedExits.remove(task.id);
        ProcessSupervisor.Spec spec = new ProcessSupervisor.Spec(task.id, task.kind, task.restart,
                task.instances, task.timeoutSeconds, task.successCodes, () -> {
                    ProcessBuilder original = phpCliBuilder.build(task.workingDirectory, task.arguments);
                    return sandbox.protect(original, LandlockSandbox.Role.TASK,
                            pluginData, task.workingDirectory, phpRuntime, nginxRuntime);
                });
        supervisor.start(spec);
    }

    public synchronized void stop(String id) {
        TaskDefinition task = get(id);
        if (task.schedule != null) scheduled.get(id).paused = true;
        if (task.instances == 1) supervisor.stop(id);
        else for (int i = 1; i <= task.instances; i++) supervisor.stop(id + "#" + i);
    }

    public synchronized List<String> scheduledStatus() {
        List<String> lines = new ArrayList<>();
        for (ScheduleState state : scheduled.values())
            lines.add(state.task.id + ": " + (state.paused ? "paused" : "active")
                    + ", schedule=" + state.task.schedule.expression
                    + (state.task.schedule.cron ? ", timezone=" + state.task.schedule.timeZone : "")
                    + ", overlap=skip");
        return lines;
    }

    public Collection<TaskDefinition> definitions() { return List.copyOf(definitions.values()); }
    public List<ProcessSupervisor.Snapshot> snapshots() { return supervisor.snapshots(); }

    private synchronized void reconcile() {
        if (closed.get()) return;
        for (TaskDefinition task : definitions.values()) {
            if (task.kind != ProcessSupervisor.Kind.ONESHOT || task.schedule != null) continue;
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

    @Override public synchronized void close() {
        if (closed.get()) return;
        // Persist successes that finished just before shutdown. In-progress work is not complete.
        reconcile();
        closed.set(true);
        reconciliation.shutdownNow();
        scheduler.shutdownNow();
        supervisor.close();
    }

    private static final class ScheduleState {
        final TaskDefinition task;
        boolean paused;
        long lastCronMinute;
        long nextIntervalNanos;

        ScheduleState(TaskDefinition task) {
            this.task = task;
            this.paused = true; // Nothing starts until PHP is available and autoStart() is called.
            reset();
        }

        void reset() {
            lastCronMinute = Instant.now().getEpochSecond() / 60L;
            if (!task.schedule.cron) nextIntervalNanos = System.nanoTime() + task.schedule.interval.toNanos();
        }

        boolean due(Instant now) {
            if (task.schedule.cron) {
                long minute = now.getEpochSecond() / 60L;
                if (minute == lastCronMinute) return false;
                lastCronMinute = minute;
                return task.schedule.matches(now);
            }
            long time = System.nanoTime();
            if (time - nextIntervalNanos < 0) return false;
            // Drift-free enough for a hosting scheduler; never replay missed ticks.
            nextIntervalNanos = time + task.schedule.interval.toNanos();
            return true;
        }
    }
}
