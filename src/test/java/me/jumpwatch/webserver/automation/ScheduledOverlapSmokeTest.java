package me.jumpwatch.webserver.automation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Proves a long-running scheduled process is not launched twice at the next tick. */
public final class ScheduledOverlapSmokeTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("webplugin-overlap-");
        Path app = Files.createDirectories(root.resolve("app"));
        Path php = Files.createDirectories(root.resolve("php"));
        Path nginx = Files.createDirectories(root.resolve("nginx"));
        Path count = root.resolve("launches.txt");
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("Id", "slow-cron");
        cfg.put("Type", "scheduled");
        cfg.put("Runtime", "bundled-php");
        cfg.put("WorkingDirectory", app.toString());
        cfg.put("Arguments", List.of("mock-artisan"));
        cfg.put("Every", "10s");
        cfg.put("TimeoutSeconds", 40);
        cfg.put("AutoStart", true);

        LandlockSandbox disabled = new LandlockSandbox(false, LandlockSandbox.Mode.PREFERRED,
                root.resolve("missing-helper"), System.out::println);
        try (TaskEngine engine = new TaskEngine(root, app, php, nginx, 2,
                (working, phpArgs) -> new ProcessBuilder("/bin/sh", "-c",
                        "printf x >> \"$1\"; sleep 16", "webplugin-overlap", count.toString())
                        .directory(working.toFile()), disabled, System.out::println)) {
            engine.load(List.of(cfg), root, app);
            engine.autoStart();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(23);
            while (System.nanoTime() < deadline) {
                Thread.sleep(150);
            }
            if (!Files.isRegularFile(count) || Files.readString(count).length() != 1)
                throw new AssertionError("Scheduled task overlapped; expected exactly one process launch in 23 seconds");
            if (engine.snapshots().stream().filter(s -> s.id.equals("slow-cron")).count() != 1)
                throw new AssertionError("A scheduled task spawned extra supervisor slots");
            engine.stop("slow-cron");
            if (engine.scheduledStatus().stream().noneMatch(s -> s.contains("paused")))
                throw new AssertionError("Stop did not pause future occurrences");
        }
        System.out.println("PASS: slow scheduled job skipped its overlapping tick; stop paused recurrence");
    }
}
