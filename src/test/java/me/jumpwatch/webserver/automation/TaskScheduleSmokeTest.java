package me.jumpwatch.webserver.automation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure cron/interval tests plus an actual scheduled run using ProcessSupervisor. */
public final class TaskScheduleSmokeTest {
    public static void main(String[] args) throws Exception {
        TaskSchedule weekday = TaskSchedule.fromMap(Map.of(
                "Schedule", "*/5 8-18 * * 1-5",
                "TimeZone", "UTC",
                "OverlapPolicy", "skip"));
        if (!weekday.matches(Instant.parse("2026-11-25T12:05:00Z")) ||
                weekday.matches(Instant.parse("2026-11-25T12:07:00Z")) ||
                weekday.matches(Instant.parse("2026-11-29T12:05:00Z")))
            throw new AssertionError("Five-field cron weekdays/step failed");
        TaskSchedule weekends = TaskSchedule.fromMap(Map.of(
                "Schedule", "0 12 1 * 0", "TimeZone", "UTC"));
        if (!weekends.matches(Instant.parse("2026-11-29T12:00:00Z")) ||
                !weekends.matches(Instant.parse("2026-12-01T12:00:00Z")))
            throw new AssertionError("Cron DOM/DOW OR semantics failed");
        TaskSchedule hour = TaskSchedule.fromMap(Map.of(
                "Schedule", "0 13 * * *", "TimeZone", "Europe/Copenhagen"));
        if (!hour.matches(Instant.parse("2026-11-25T12:00:00Z")))
            throw new AssertionError("IANA time zone evaluation failed");
        TaskSchedule interval = TaskSchedule.fromMap(Map.of("Every", "6h"));
        if (!interval.interval.equals(java.time.Duration.ofHours(6)))
            throw new AssertionError("Interval duration parsing failed");

        for (Map<String, String> invalid : List.of(
                Map.of("Schedule", "* * * *"),
                Map.of("Every", "3s"),
                Map.of("Every", "1m", "Schedule", "* * * * *"),
                Map.of("Schedule", "* * * * *", "OverlapPolicy", "allow"),
                Map.of("Schedule", "* * * 13 *"))) {
            boolean rejected = false;
            try { TaskSchedule.fromMap(invalid); }
            catch (IllegalArgumentException expected) { rejected = true; }
            if (!rejected) throw new AssertionError("Invalid schedule accepted: " + invalid);
        }

        Path root = Files.createTempDirectory("webplugin-schedule-");
        Path site = Files.createDirectories(root.resolve("site"));
        Path phpRuntime = Files.createDirectories(root.resolve("php-runtime"));
        Path nginxRuntime = Files.createDirectories(root.resolve("nginx-runtime"));
        Path output = root.resolve("tick-output");
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("Id", "cron-smoke");
        task.put("Type", "scheduled");
        task.put("Runtime", "bundled-php");
        task.put("WorkingDirectory", site.toString());
        task.put("Arguments", List.of("dummy-php-file.php"));
        task.put("Every", "10s");
        task.put("AutoStart", true);
        task.put("TimeoutSeconds", 5);
        LandlockSandbox off = new LandlockSandbox(false, LandlockSandbox.Mode.PREFERRED,
                root.resolve("not-needed"), System.out::println);
        try (TaskEngine engine = new TaskEngine(root, site, phpRuntime, nginxRuntime,
                2, (cwd, phpArgs) -> new ProcessBuilder("/bin/sh", "-c",
                        "printf tick > \"$1\"", "cron-smoke", output.toString()).directory(cwd.toFile()),
                off, System.out::println)) {
            engine.load(List.of(task), root, site);
            engine.autoStart();
            if (engine.scheduledStatus().stream().noneMatch(s -> s.contains("cron-smoke: active")))
                throw new AssertionError("Auto-started schedule not active");
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
            while (!Files.isRegularFile(output) && System.nanoTime() < deadline)
                Thread.sleep(100);
            if (!Files.isRegularFile(output) || !Files.readString(output).equals("tick"))
                throw new AssertionError("Scheduled PHP job did not execute through process supervisor");
            engine.stop("cron-smoke");
            if (engine.scheduledStatus().stream().noneMatch(s -> s.contains("cron-smoke: paused")))
                throw new AssertionError("Stopped scheduled task did not pause");
            engine.start("cron-smoke");
            if (engine.scheduledStatus().stream().noneMatch(s -> s.contains("cron-smoke: active")))
                throw new AssertionError("Scheduled task resume did not work");
        }
        System.out.println("PASS: cron expressions, zone handling, bounds, recurring supervisor execution and pause/resume");
    }
}
