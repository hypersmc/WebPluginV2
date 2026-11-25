package me.jumpwatch.webserver.automation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Covers explicit environment injection, secret handling and PHP-FPM pool idempotence. */
public final class PhpEnvironmentSmokeTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("webplugin-env-");
        Path privateDir = Files.createDirectories(root.resolve("private"));
        Path secrets = privateDir.resolve("db.env");
        Files.writeString(secrets, "# private file\nDB_PASSWORD=very-secret-test-value\n", StandardCharsets.UTF_8);

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("Enabled", true);
        settings.put("InheritContainerEnvironment", false);
        settings.put("Variables", Map.of("APP_ENV", "production", "APP_DEBUG", "false"));
        settings.put("FromContainer", List.of());
        settings.put("SecretsFile", "db.env");
        PhpEnvironment bridge = PhpEnvironment.fromMap(settings, root);
        if (!bridge.enabled() || bridge.size() != 3) throw new AssertionError("Invalid bridge config");

        ProcessBuilder builder = new ProcessBuilder("true");
        builder.environment().put("MC_SECRET_TOKEN", "sensitive-minecraft-value");
        builder.environment().put("LD_LIBRARY_PATH", "/some/bundled/native/libs");
        bridge.apply(builder);
        if (builder.environment().containsKey("MC_SECRET_TOKEN") ||
                !builder.environment().get("LD_LIBRARY_PATH").equals("/some/bundled/native/libs") ||
                !builder.environment().get("DB_PASSWORD").equals("very-secret-test-value") ||
                !builder.environment().get("APP_DEBUG").equals("false"))
            throw new AssertionError("Environment not explicit, or bundled native library path lost");

        Path pool = root.resolve("www.conf");
        String original = "[www]\nlisten = 9000\nclear_env = no\n";
        Files.writeString(pool, original);
        bridge.updateFpmPool(pool);
        String generated = Files.readString(pool);
        if (!generated.contains("clear_env = yes") ||
                !generated.contains("env[DB_PASSWORD] = $DB_PASSWORD") ||
                generated.contains("very-secret-test-value") ||
                generated.contains("sensitive-minecraft-value"))
            throw new AssertionError("Pool config must reference only keys, never secret values");
        bridge.updateFpmPool(pool);
        if (!generated.equals(Files.readString(pool)))
            throw new AssertionError("Repeated PHP-FPM pool generation must be idempotent");

        PhpEnvironment.disabled().updateFpmPool(pool);
        if (!Files.readString(pool).equals(original))
            throw new AssertionError("Disabling bridge must remove managed directives");

        for (String key : List.of("LD_PRELOAD", "PHP_INI_SCAN_DIR", "PATH", "HOME", "BASH_ENV")) {
            settings.put("Variables", Map.of(key, "hijack"));
            boolean rejected = false;
            try { PhpEnvironment.fromMap(settings, root); }
            catch (IllegalArgumentException expected) { rejected = true; }
            if (!rejected) throw new AssertionError("Unsafe env key accepted: " + key);
        }
        settings.put("Variables", Map.of("APP_ENV", "production"));
        settings.put("SecretsFile", "../outside.env");
        boolean escaped = false;
        try { PhpEnvironment.fromMap(settings, root); }
        catch (java.io.IOException | IllegalArgumentException expected) { escaped = true; }
        if (!escaped) throw new AssertionError("SecretsFile traversal accepted");

        System.out.println("PASS: PHP environment allowlist, secrets, FPM pool references, disabled cleanup");
    }
}
