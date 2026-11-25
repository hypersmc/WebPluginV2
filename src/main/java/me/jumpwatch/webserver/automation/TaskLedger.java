package me.jumpwatch.webserver.automation;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Persistent completion ledger separate from server-owner task configuration. */
public final class TaskLedger {
    private final Path file;
    private final Properties properties = new Properties();

    public TaskLedger(Path file) throws IOException {
        this.file = file.toAbsolutePath().normalize();
        Files.createDirectories(this.file.getParent());
        if (Files.isRegularFile(this.file)) {
            try (InputStream in = Files.newInputStream(this.file)) { properties.load(in); }
        }
    }

    public synchronized boolean shouldRun(TaskDefinition task) {
        switch (task.run) {
            case ONCE:
                return !"true".equals(properties.getProperty("task." + task.id + ".success"));
            case ONCE_PER_DEPLOYMENT:
                return !task.deploymentId.equals(properties.getProperty("task." + task.id + ".completedDeployment"));
            case EVERY_START:
                return true;
            case MANUAL:
            default:
                return false;
        }
    }

    public synchronized void completed(TaskDefinition task, int exitCode) throws IOException {
        properties.setProperty("task." + task.id + ".success", "true");
        properties.setProperty("task." + task.id + ".lastExitCode", Integer.toString(exitCode));
        properties.setProperty("task." + task.id + ".lastSuccessTime", java.time.Instant.now().toString());
        if (task.run == TaskDefinition.Run.ONCE_PER_DEPLOYMENT) {
            properties.setProperty("task." + task.id + ".completedDeployment", task.deploymentId);
        }
        Path temporary = Files.createTempFile(file.getParent(), "task-state-", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temporary)) {
                properties.store(out, "WebPlugin deployment task completion state");
            }
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
