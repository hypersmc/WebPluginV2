package me.jumpwatch.webserver.automation;

import java.util.ArrayList;
import java.util.List;

/** Run with plain javac/java; no Bukkit or Maven dependencies. */
public final class ProcessSupervisorSmokeTest {
    public static void main(String[] args) throws Exception {
        List<String> log = new ArrayList<>();
        try (ProcessSupervisor supervisor = new ProcessSupervisor(4, text -> { synchronized (log) { log.add(text); } })) {
            ProcessSupervisor.Spec first = new ProcessSupervisor.Spec(
                    "first", ProcessSupervisor.Kind.ONESHOT, ProcessSupervisor.Restart.NEVER, 1, 5,
                    List.of(0), () -> new ProcessBuilder("sh", "-c", "sleep 1; echo first-done"));
            ProcessSupervisor.Spec second = new ProcessSupervisor.Spec(
                    "second", ProcessSupervisor.Kind.ONESHOT, ProcessSupervisor.Restart.NEVER, 1, 5,
                    List.of(0), () -> new ProcessBuilder("sh", "-c", "sleep 1; echo second-done"));
            long started = System.nanoTime();
            supervisor.start(first);
            supervisor.start(second);
            while (true) {
                ProcessSupervisor.Snapshot a = supervisor.snapshot("first");
                ProcessSupervisor.Snapshot b = supervisor.snapshot("second");
                if (a.state == ProcessSupervisor.State.COMPLETED && b.state == ProcessSupervisor.State.COMPLETED) {
                    if (a.logTail.stream().noneMatch(x -> x.contains("first-done")) ||
                            b.logTail.stream().noneMatch(x -> x.contains("second-done"))) {
                        throw new AssertionError("Output logs missing");
                    }
                    break;
                }
                if (a.state == ProcessSupervisor.State.FAILED || b.state == ProcessSupervisor.State.FAILED) {
                    throw new AssertionError("Unexpected process failure");
                }
                if ((System.nanoTime() - started) > 4_000_000_000L) {
                    throw new AssertionError("Processes ran sequentially or never finished");
                }
                Thread.sleep(30);
            }
            ProcessSupervisor.Spec service = new ProcessSupervisor.Spec(
                    "worker", ProcessSupervisor.Kind.SERVICE, ProcessSupervisor.Restart.ON_FAILURE, 2, 0,
                    List.of(0), () -> new ProcessBuilder("sh", "-c", "echo alive; sleep 20"));
            supervisor.start(service);
            Thread.sleep(400);
            if (supervisor.snapshot("worker#1").state != ProcessSupervisor.State.RUNNING ||
                    supervisor.snapshot("worker#2").state != ProcessSupervisor.State.RUNNING) {
                throw new AssertionError("Service instances did not run independently");
            }
            supervisor.stop("worker#1");
            supervisor.stop("worker#2");
            System.out.println("PASS: parallel one-shots, output capture, concurrent long-running workers, stop");
        }
    }
}
