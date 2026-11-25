package me.jumpwatch.webserver.automation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class TaskEngineSmokeTest {
    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("wp-test-");
        Path web = Files.createDirectories(temp.resolve("app"));
        LandlockSandbox sandbox = new LandlockSandbox(false, LandlockSandbox.Mode.PREFERRED,
                temp.resolve("missing-helper"), System.out::println);
        try (TaskEngine engine = new TaskEngine(temp, web, temp, temp, 3,
                (cwd, phpArgs) -> new ProcessBuilder("sh", "-c", "sleep 1; echo migrated").directory(cwd.toFile()),
                sandbox, System.out::println)) {
            engine.load(List.of(Map.of("Id", "migration", "Type", "oneshot", "Runtime", "bundled-php",
                    "WorkingDirectory", web.toString(), "Run", "once-per-deployment", "DeploymentId", "v1",
                    "Arguments", List.of("artisan", "migrate", "--force"), "SuccessCodes", List.of(0))), temp, web);
            engine.autoStart();
            long start = System.currentTimeMillis();
            while (true) {
                ProcessSupervisor.Snapshot snapshot = engine.snapshots().get(0);
                if (snapshot.state == ProcessSupervisor.State.COMPLETED) break;
                if (System.currentTimeMillis() - start > 6000) throw new AssertionError("Migration timeout");
                Thread.sleep(50);
            }
            Thread.sleep(600);
        }
        TaskLedger ledger = new TaskLedger(temp.resolve("tasks").resolve("state.properties"));
        TaskDefinition task = TaskDefinition.fromMap(Map.of("Id", "migration", "Type", "oneshot",
                        "WorkingDirectory", web.toString(), "Run", "once-per-deployment", "DeploymentId", "v1",
                        "Arguments", List.of("artisan", "migrate")), temp, web);
        if (ledger.shouldRun(task)) throw new AssertionError("Completion ledger lost state");
        TaskDefinition next = TaskDefinition.fromMap(Map.of("Id", "migration", "Type", "oneshot",
                        "WorkingDirectory", web.toString(), "Run", "once-per-deployment", "DeploymentId", "v2",
                        "Arguments", List.of("artisan", "migrate")), temp, web);
        if (!ledger.shouldRun(next)) throw new AssertionError("Deployment change was not detected");
        System.out.println("PASS: once-per-deployment task and durable ledger");
    }
}
