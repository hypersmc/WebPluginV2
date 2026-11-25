package me.jumpwatch.webserver.automation;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** A bounded, non-blocking supervisor for independent subprocesses. */
public final class ProcessSupervisor implements AutoCloseable {
    public enum Kind { ONESHOT, SERVICE }
    public enum Restart { NEVER, ON_FAILURE, ALWAYS }
    public enum State { QUEUED, STARTING, RUNNING, COMPLETED, FAILED, STOPPED, RESTART_WAIT }

    @FunctionalInterface
    public interface BuilderFactory { ProcessBuilder build() throws Exception; }

    public static final class Spec {
        public final String id;
        public final Kind kind;
        public final Restart restart;
        public final int instances;
        public final long timeoutSeconds;
        public final List<Integer> successExitCodes;
        public final BuilderFactory builder;

        public Spec(String id, Kind kind, Restart restart, int instances, long timeoutSeconds,
                    List<Integer> successExitCodes, BuilderFactory builder) {
            if (id == null || !id.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}")) {
                throw new IllegalArgumentException("Invalid task id");
            }
            if (instances < 1 || instances > 32 || timeoutSeconds < 0 || timeoutSeconds > 86400) {
                throw new IllegalArgumentException("Invalid instances or timeout");
            }
            if (kind == Kind.ONESHOT && (instances != 1 || restart != Restart.NEVER)) {
                throw new IllegalArgumentException("One-shot tasks cannot be replicated or automatically restarted");
            }
            this.id = id;
            this.kind = Objects.requireNonNull(kind);
            this.restart = Objects.requireNonNull(restart);
            this.instances = instances;
            this.timeoutSeconds = timeoutSeconds;
            this.successExitCodes = Collections.unmodifiableList(new ArrayList<>(successExitCodes));
            if (this.successExitCodes.isEmpty()) throw new IllegalArgumentException("Missing success exit codes");
            this.builder = Objects.requireNonNull(builder);
        }
    }

    public static final class Snapshot {
        public final String id;
        public final State state;
        public final long pid;
        public final Integer lastExitCode;
        public final int restarts;
        public final Instant updatedAt;
        public final List<String> logTail;

        private Snapshot(Slot slot) {
            this.id = slot.name;
            this.state = slot.state;
            this.pid = slot.process == null ? -1 : slot.process.pid();
            this.lastExitCode = slot.lastExitCode;
            this.restarts = slot.restarts;
            this.updatedAt = slot.updatedAt;
            synchronized (slot.tail) { this.logTail = List.copyOf(slot.tail); }
        }
    }

    private static final int MAX_LOG_LINES = 160;
    private static final int MAX_LINE_CHARS = 768;
    private final Map<String, Slot> slots = new ConcurrentHashMap<>();
    private final ScheduledExecutorService control = Executors.newScheduledThreadPool(2, r -> daemon("webplugin-supervisor", r));
    private final ExecutorService readers = Executors.newCachedThreadPool(r -> daemon("webplugin-task-output", r));
    private final AtomicInteger capacity = new AtomicInteger();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final int maxProcesses;
    private final Consumer<String> log;

    public ProcessSupervisor(int maxProcesses, Consumer<String> log) {
        if (maxProcesses < 1 || maxProcesses > 64) throw new IllegalArgumentException("maxProcesses must be 1..64");
        this.maxProcesses = maxProcesses;
        this.log = Objects.requireNonNull(log);
    }

    private static Thread daemon(String name, Runnable runnable) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    /** Returns immediately. Each instance runs independently of every other instance. */
    public synchronized void start(Spec spec) {
        if (closed.get()) throw new IllegalStateException("Supervisor is closed");
        for (int i = 1; i <= spec.instances; i++) {
            String name = spec.instances == 1 ? spec.id : spec.id + "#" + i;
            Slot previous = slots.get(name);
            if (previous != null && !previous.terminated()) {
                throw new IllegalStateException("Task already active: " + name);
            }
        }
        if (capacity.get() + spec.instances > maxProcesses) {
            throw new IllegalStateException("Process limit reached (" + maxProcesses + ")");
        }
        capacity.addAndGet(spec.instances);
        for (int i = 1; i <= spec.instances; i++) {
            String name = spec.instances == 1 ? spec.id : spec.id + "#" + i;
            Slot slot = new Slot(name, spec);
            slots.put(name, slot);
            control.execute(() -> launch(slot));
        }
    }

    private void launch(Slot slot) {
        if (closed.get() || slot.cancelled.get()) { terminal(slot, State.STOPPED); return; }
        slot.setState(State.STARTING);
        try {
            ProcessBuilder builder = slot.spec.builder.build();
            builder.redirectErrorStream(true);
            builder.redirectInput(ProcessBuilder.Redirect.PIPE);
            if (closed.get() || slot.cancelled.get()) { terminal(slot, State.STOPPED); return; }
            Process process = builder.start();
            slot.process = process;
            slot.startedAtNanos = System.nanoTime();
            try { process.getOutputStream().close(); } catch (IOException ignored) { }
            slot.setState(State.RUNNING);
            log.accept("Task " + slot.name + " started (PID " + process.pid() + ")");
            readers.execute(() -> drain(slot, process));
            if (slot.spec.timeoutSeconds > 0) {
                slot.timeout = control.schedule(() -> {
                    if (process.isAlive() && !slot.cancelled.get()) {
                        slot.timedOut = true;
                        log.accept("Task " + slot.name + " timed out");
                        terminateTree(process);
                        control.schedule(() -> {
                            if (process.isAlive()) forceTerminateTree(process);
                        }, 5, TimeUnit.SECONDS);
                    }
                }, slot.spec.timeoutSeconds, TimeUnit.SECONDS);
            }
            process.onExit().whenCompleteAsync((exited, error) -> exited(slot, process, error), control);
            if (slot.cancelled.get()) terminateTree(process);
        } catch (Exception ex) {
            addLog(slot, "Launch failed: " + ex.getMessage());
            log.accept("Task " + slot.name + " launch failed: " + ex.getMessage());
            onFailure(slot);
        }
    }

    /** Never allow a task that omits newlines to accumulate an unbounded readLine() buffer. */
    private void drain(Slot slot, Process process) {
        try (InputStreamReader reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
            char[] chunk = new char[2048];
            StringBuilder line = new StringBuilder(MAX_LINE_CHARS);
            boolean truncated = false;
            int read;
            while ((read = reader.read(chunk)) != -1) {
                for (int i = 0; i < read; i++) {
                    char ch = chunk[i];
                    if (ch == '\n') {
                        addLog(slot, line.toString() + (truncated ? " [truncated]" : ""));
                        line.setLength(0);
                        truncated = false;
                    } else if (ch != '\r') {
                        if (line.length() < MAX_LINE_CHARS - 16) line.append(ch);
                        else truncated = true;
                    }
                }
            }
            if (line.length() > 0 || truncated)
                addLog(slot, line.toString() + (truncated ? " [truncated]" : ""));
        } catch (IOException ex) {
            addLog(slot, "Output unavailable: " + ex.getMessage());
        }
    }

    private static void addLog(Slot slot, String line) {
        String shortLine = line.length() > MAX_LINE_CHARS ? line.substring(0, MAX_LINE_CHARS) + " [truncated]" : line;
        synchronized (slot.tail) {
            if (slot.tail.size() == MAX_LOG_LINES) slot.tail.removeFirst();
            slot.tail.addLast(shortLine);
        }
    }

    private void exited(Slot slot, Process process, Throwable error) {
        if (slot.process != process) return;
        if (slot.timeout != null) slot.timeout.cancel(false);
        if (error != null) addLog(slot, "Process-exit monitor failed: " + error.getMessage());
        try { slot.lastExitCode = process.exitValue(); } catch (IllegalThreadStateException ignored) { }
        slot.process = null;
        // A healthy service that runs for two minutes gets a fresh restart budget.
        if (slot.spec.kind == Kind.SERVICE && slot.startedAtNanos != 0L
                && System.nanoTime() - slot.startedAtNanos > TimeUnit.MINUTES.toNanos(2)) {
            slot.restarts = 0;
        }
        if (slot.cancelled.get() || closed.get()) { terminal(slot, State.STOPPED); return; }
        boolean success = !slot.timedOut && error == null && slot.lastExitCode != null
                && slot.spec.successExitCodes.contains(slot.lastExitCode);
        if (slot.spec.kind == Kind.ONESHOT) { terminal(slot, success ? State.COMPLETED : State.FAILED); return; }
        if (slot.spec.restart == Restart.ALWAYS || (slot.spec.restart == Restart.ON_FAILURE && !success)) {
            restart(slot);
        } else {
            terminal(slot, success ? State.COMPLETED : State.FAILED);
        }
    }

    private void onFailure(Slot slot) {
        if (slot.cancelled.get() || closed.get()) { terminal(slot, State.STOPPED); return; }
        if (slot.spec.kind == Kind.SERVICE && slot.spec.restart != Restart.NEVER) restart(slot);
        else terminal(slot, State.FAILED);
    }

    private void restart(Slot slot) {
        slot.restarts++;
        if (slot.restarts > 10) {
            log.accept("Task " + slot.name + " stopped after 10 consecutive restart failures");
            terminal(slot, State.FAILED);
            return;
        }
        slot.timedOut = false;
        long delay = Math.min(60, 1L << Math.min(6, slot.restarts - 1));
        slot.setState(State.RESTART_WAIT);
        log.accept("Task " + slot.name + " restarting in " + delay + " seconds (attempt " + slot.restarts + ")");
        slot.pending = control.schedule(() -> launch(slot), delay, TimeUnit.SECONDS);
    }

    private void terminal(Slot slot, State state) {
        slot.setState(state);
        if (slot.released.compareAndSet(false, true)) capacity.decrementAndGet();
    }

    public synchronized void stop(String name) {
        Slot slot = slots.get(name);
        if (slot == null) return;
        slot.cancelled.set(true);
        if (slot.pending != null) slot.pending.cancel(false);
        Process process = slot.process;
        if (process != null && process.isAlive()) {
            terminateTree(process);
            if (!closed.get()) control.schedule(() -> {
                if (process.isAlive()) forceTerminateTree(process);
            }, 5, TimeUnit.SECONDS);
        } else terminal(slot, State.STOPPED);
    }

    private static void terminateTree(Process process) {
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
    }

    private static void forceTerminateTree(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    public List<Snapshot> snapshots() {
        List<Snapshot> snapshots = new ArrayList<>();
        for (Slot slot : slots.values()) snapshots.add(new Snapshot(slot));
        snapshots.sort((a, b) -> a.id.compareTo(b.id));
        return snapshots;
    }

    public Snapshot snapshot(String id) {
        Slot slot = slots.get(id);
        return slot == null ? null : new Snapshot(slot);
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        for (String id : new ArrayList<>(slots.keySet())) stop(id);
        control.shutdown();
        readers.shutdown();
        // Daemon reaper avoids blocking the Minecraft server shutdown thread.
        daemon("webplugin-process-reaper", () -> {
            try { Thread.sleep(5000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            for (Slot slot : slots.values()) {
                Process process = slot.process;
                if (process != null && process.isAlive()) forceTerminateTree(process);
            }
        }).start();
    }

    private static final class Slot {
        final String name;
        final Spec spec;
        final AtomicBoolean cancelled = new AtomicBoolean();
        final AtomicBoolean released = new AtomicBoolean();
        final Deque<String> tail = new ArrayDeque<>();
        volatile State state = State.QUEUED;
        volatile Instant updatedAt = Instant.now();
        volatile Process process;
        volatile ScheduledFuture<?> timeout;
        volatile ScheduledFuture<?> pending;
        volatile Integer lastExitCode;
        volatile boolean timedOut;
        volatile int restarts;
        volatile long startedAtNanos;

        Slot(String name, Spec spec) { this.name = name; this.spec = spec; }
        void setState(State state) { this.state = state; updatedAt = Instant.now(); }
        boolean terminated() {
            return released.get() && (state == State.COMPLETED || state == State.FAILED || state == State.STOPPED);
        }
    }
}
