package me.jumpwatch.webserver.automation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One server-owner-defined PHP CLI job; never a shell command string. */
public final class TaskDefinition {
    public enum Run { ONCE, ONCE_PER_DEPLOYMENT, EVERY_START, MANUAL }
    public final String id;
    public final ProcessSupervisor.Kind kind;
    public final Run run;
    public final ProcessSupervisor.Restart restart;
    public final boolean autoStart;
    public final int instances;
    public final long timeoutSeconds;
    public final String deploymentId;
    public final Path workingDirectory;
    public final List<String> arguments;
    public final List<Integer> successCodes;

    private TaskDefinition(String id, ProcessSupervisor.Kind kind, Run run, ProcessSupervisor.Restart restart,
                           boolean autoStart, int instances, long timeoutSeconds, String deploymentId,
                           Path workingDirectory, List<String> arguments, List<Integer> successCodes) {
        this.id = id;
        this.kind = kind;
        this.run = run;
        this.restart = restart;
        this.autoStart = autoStart;
        this.instances = instances;
        this.timeoutSeconds = timeoutSeconds;
        this.deploymentId = deploymentId;
        this.workingDirectory = workingDirectory;
        this.arguments = Collections.unmodifiableList(arguments);
        this.successCodes = Collections.unmodifiableList(successCodes);
    }

    public static TaskDefinition fromMap(Map<?, ?> config, Path serverRoot, Path defaultWebRoot) {
        String id = string(config, "Id", "");
        if (!id.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}")) throw new IllegalArgumentException("Invalid task Id: " + id);
        String runtime = string(config, "Runtime", "bundled-php");
        if (!runtime.equalsIgnoreCase("bundled-php")) throw new IllegalArgumentException("Only Runtime: bundled-php is supported in 4.2R");
        ProcessSupervisor.Kind kind = ProcessSupervisor.Kind.valueOf(string(config, "Type", "oneshot").toUpperCase(Locale.ROOT));
        Run run = Run.valueOf(string(config, "Run", kind == ProcessSupervisor.Kind.SERVICE ? "manual" : "once")
                .replace('-', '_').toUpperCase(Locale.ROOT));
        ProcessSupervisor.Restart restart = ProcessSupervisor.Restart.valueOf(string(config, "Restart", "never")
                .replace('-', '_').toUpperCase(Locale.ROOT));
        int instances = number(config, "Instances", 1);
        int timeout = number(config, "TimeoutSeconds", kind == ProcessSupervisor.Kind.ONESHOT ? 180 : 0);
        String deploymentId = string(config, "DeploymentId", "");
        if (run == Run.ONCE_PER_DEPLOYMENT && deploymentId.isBlank()) throw new IllegalArgumentException("DeploymentId is required: " + id);
        String dir = string(config, "WorkingDirectory", "");
        Path cwd = dir.isBlank() ? defaultWebRoot : Path.of(dir);
        if (!cwd.isAbsolute()) cwd = serverRoot.resolve(cwd);
        cwd = cwd.toAbsolutePath().normalize();
        if (!Files.isDirectory(cwd)) throw new IllegalArgumentException("WorkingDirectory does not exist: " + cwd);
        try { cwd = cwd.toRealPath(); } catch (Exception ex) { throw new IllegalArgumentException("WorkingDirectory unavailable: " + cwd, ex); }
        Object rawArgs = config.get("Arguments");
        if (!(rawArgs instanceof List)) throw new IllegalArgumentException("Arguments must be a YAML list: " + id);
        List<String> args = new ArrayList<>();
        for (Object raw : (List<?>) rawArgs) {
            if (!(raw instanceof String) || ((String) raw).isBlank() || ((String) raw).indexOf('\0') >= 0)
                throw new IllegalArgumentException("Invalid argument in " + id);
            args.add((String) raw);
        }
        if (args.isEmpty()) throw new IllegalArgumentException("Missing Arguments: " + id);
        List<Integer> codes = new ArrayList<>();
        Object codesRaw = config.getOrDefault("SuccessCodes", null); // see Map<?, ?> wildcard handling below
        if (codesRaw == null) codes.add(0);
        else if (codesRaw instanceof List) {
            for (Object x : (List<?>) codesRaw) {
                if (!(x instanceof Number)) throw new IllegalArgumentException("SuccessCodes must be integers: " + id);
                codes.add(((Number) x).intValue());
            }
        } else throw new IllegalArgumentException("SuccessCodes must be a list: " + id);
        if (codes.isEmpty()) throw new IllegalArgumentException("SuccessCodes is empty: " + id);
        if (kind == ProcessSupervisor.Kind.ONESHOT && (instances != 1 || restart != ProcessSupervisor.Restart.NEVER))
            throw new IllegalArgumentException("One-shot tasks cannot have instances >1 or Restart != never");
        return new TaskDefinition(id, kind, run, restart, bool(config, "AutoStart", kind == ProcessSupervisor.Kind.SERVICE),
                instances, timeout, deploymentId, cwd, args, codes);
    }

    private static String string(Map<?, ?> m, String key, String fallback) {
        Object value = m.get(key); return value == null ? fallback : value.toString().trim();
    }
    private static int number(Map<?, ?> m, String key, int fallback) {
        Object value = m.get(key); return value == null ? fallback : ((Number) value).intValue();
    }
    private static boolean bool(Map<?, ?> m, String key, boolean fallback) {
        Object value = m.get(key); return value == null ? fallback : Boolean.parseBoolean(value.toString());
    }
}
