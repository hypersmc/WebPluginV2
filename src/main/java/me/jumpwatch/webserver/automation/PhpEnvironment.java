package me.jumpwatch.webserver.automation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Explicit PHP environment bridge: no automatic export of Minecraft/JVM secrets.
 * Config values are non-secret; optional secrets belong in a private .env file.
 * PHP-FPM gets env[KEY] = $KEY references, NEVER literal values in pool config.
 */
public final class PhpEnvironment {
    private static final Pattern KEY = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");
    private static final String START = "; BEGIN WEBPLUGIN MANAGED ENVIRONMENT";
    private static final String END = "; END WEBPLUGIN MANAGED ENVIRONMENT";
    private final boolean enabled;
    private final boolean inheritContainer;
    private final Map<String, String> values;

    private PhpEnvironment(boolean enabled, boolean inheritContainer, Map<String, String> values) {
        this.enabled = enabled;
        this.inheritContainer = inheritContainer;
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static PhpEnvironment disabled() { return new PhpEnvironment(false, false, Map.of()); }
    public boolean enabled() { return enabled; }
    public Set<String> names() { return values.keySet(); }
    public int size() { return values.size(); }

    /** Reads config from all three platform adapters without platform-specific libraries. */
    public static PhpEnvironment fromMap(Map<?, ?> config, Path pluginData) throws IOException {
        if (config == null || !booleanValue(config.get("Enabled"), false)) return disabled();
        boolean inherit = booleanValue(config.get("InheritContainerEnvironment"), false);
        Map<String, String> output = new LinkedHashMap<>();
        Object from = config.get("FromContainer");
        if (from != null) {
            if (!(from instanceof List<?>)) throw new IllegalArgumentException("Environment.FromContainer must be a list");
            for (Object item : (List<?>) from) {
                String name = String.valueOf(item);
                validateName(name);
                String value = System.getenv(name);
                if (value == null)
                    throw new IllegalArgumentException("Required container environment variable is missing: " + name);
                add(output, name, value);
            }
        }
        Object raw = config.get("Variables");
        if (raw != null) {
            if (!(raw instanceof Map<?, ?>)) throw new IllegalArgumentException("Environment.Variables must be a YAML mapping");
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet())
                add(output, String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }
        Object secretConfig = config.get("SecretsFile");
        if (secretConfig != null && !secretConfig.toString().isBlank())
            loadSecrets(output, pluginData, secretConfig.toString().trim());
        if (output.size() > 64) throw new IllegalArgumentException("Too many Environment variables (maximum 64)");
        return new PhpEnvironment(true, inherit, output);
    }

    /** Restricts exposed environment to selected PHP keys, preserving native loader paths. */
    public void apply(ProcessBuilder process) {
        if (!enabled) return;
        Map<String, String> environment = process.environment();
        if (!inheritContainer) {
            String libraries = environment.get("LD_LIBRARY_PATH");
            environment.clear();
            environment.put("PATH", "/usr/bin:/bin");
            environment.put("LANG", "C.UTF-8");
            if (libraries != null && !libraries.isEmpty())
                environment.put("LD_LIBRARY_PATH", libraries);
        }
        environment.putAll(values);
    }

    /** Idempotently installs/removes a marker block; no secret value is written to disk here. */
    public void updateFpmPool(Path poolFile) throws IOException {
        if (!Files.isRegularFile(poolFile)) {
            if (enabled) throw new IOException("PHP-FPM pool configuration missing: " + poolFile);
            return;
        }
        String current = Files.readString(poolFile, StandardCharsets.UTF_8);
        int begin = current.indexOf(START);
        int end = current.indexOf(END);
        if ((begin < 0) != (end < 0) || (begin >= 0 && end < begin))
            throw new IOException("PHP-FPM environment marker block is malformed");
        if (begin >= 0) {
            int finish = end + END.length();
            if (finish < current.length() && current.charAt(finish) == '\r') finish++;
            if (finish < current.length() && current.charAt(finish) == '\n') finish++;
            current = current.substring(0, begin) + current.substring(finish);
        }
        String newText = current;
        if (enabled) {
            if (!Pattern.compile("^\\s*\\[www\\]\\s*$", Pattern.MULTILINE).matcher(current).find())
                throw new IOException("PHP-FPM pool file has no [www] section; refusing to append environment directives");
            StringBuilder block = new StringBuilder();
            if (!current.endsWith("\n")) block.append('\n');
            block.append(START).append('\n');
            block.append("clear_env = yes\n");
            for (String key : values.keySet())
                block.append("env[").append(key).append("] = $").append(key).append('\n');
            block.append(END).append('\n');
            newText = current + block;
        }
        if (!newText.equals(Files.readString(poolFile, StandardCharsets.UTF_8)))
            Files.writeString(poolFile, newText, StandardCharsets.UTF_8);
    }

    private static void loadSecrets(Map<String, String> target, Path pluginData, String relative) throws IOException {
        Path base = pluginData.toAbsolutePath().normalize().resolve("private");
        if (Files.isSymbolicLink(base))
            throw new IOException("Environment private directory must not be a symlink");
        Path path = Path.of(relative);
        if (path.isAbsolute()) throw new IllegalArgumentException("SecretsFile must be relative to plugins/webplugin/private/");
        path = base.resolve(path).normalize();
        if (!path.startsWith(base) || Files.isSymbolicLink(path) ||
                !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Environment.SecretsFile must be a regular file inside plugins/webplugin/private/");
        if (Files.size(path) > 65536) throw new IOException("Environment.SecretsFile is too large");
        // Reject symlink parent directories and paths escaping through links.
        Path realBase = Files.exists(base) ? base.toRealPath() : base;
        if (!path.toRealPath().startsWith(realBase))
            throw new IOException("Environment.SecretsFile resolves outside private directory");
        // No interpolation, shell evaluation or expansion. KEY=value, comments and blank lines only.
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String entry = line.trim();
            if (entry.isEmpty() || entry.startsWith("#")) continue;
            int equals = entry.indexOf('=');
            if (equals <= 0) throw new IOException("Invalid SecretsFile entry; expected KEY=value");
            String key = entry.substring(0, equals).trim();
            String value = entry.substring(equals + 1);
            add(target, key, value);
        }
    }

    private static void add(Map<String, String> values, String name, String value) {
        validateName(name);
        if (value == null || value.length() > 8192 || value.indexOf('\0') >= 0 ||
                value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)
            throw new IllegalArgumentException("Invalid Environment value for " + name);
        values.put(name, value);
    }

    private static void validateName(String name) {
        if (!KEY.matcher(name).matches() || name.startsWith("LD_") ||
                name.equals("PATH") || name.equals("HOME") || name.equals("PHPRC") ||
                name.equals("PHP_INI_SCAN_DIR") || name.equals("BASH_ENV") ||
                name.equals("ENV") || name.startsWith("JAVA_"))
            throw new IllegalArgumentException("Unsafe Environment variable name: " + name);
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(value.toString());
    }
}
