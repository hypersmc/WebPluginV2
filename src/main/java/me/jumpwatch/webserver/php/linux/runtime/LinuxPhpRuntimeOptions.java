package me.jumpwatch.webserver.php.linux.runtime;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Platform-neutral Linux PHP runtime options.
 * Bukkit, Bungee and Velocity use different config APIs, so their PHP server
 * classes translate plugin config into this simple object before launching PHP.
 */
public final class LinuxPhpRuntimeOptions {
    private final File dataFolder;
    private final String serverLocation;
    private final boolean debug;
    private final boolean detectExtensionsFromPhpIni;
    private final List<String> enabledExtensions;
    private final List<String> extraLibraryPaths;
    private final Map<String, List<String>> extensionLibraryPaths;
    private final boolean preflightCheck;

    public LinuxPhpRuntimeOptions(
            File dataFolder,
            String serverLocation,
            boolean debug,
            boolean detectExtensionsFromPhpIni,
            List<String> enabledExtensions,
            List<String> extraLibraryPaths,
            Map<String, List<String>> extensionLibraryPaths
    ) {
        this(dataFolder, serverLocation, debug, detectExtensionsFromPhpIni, enabledExtensions, extraLibraryPaths, extensionLibraryPaths, true);
    }

    public LinuxPhpRuntimeOptions(
            File dataFolder,
            String serverLocation,
            boolean debug,
            boolean detectExtensionsFromPhpIni,
            List<String> enabledExtensions,
            List<String> extraLibraryPaths,
            Map<String, List<String>> extensionLibraryPaths,
            boolean preflightCheck
    ) {
        this.dataFolder = dataFolder;
        this.serverLocation = serverLocation == null ? "" : serverLocation;
        this.debug = debug;
        this.detectExtensionsFromPhpIni = detectExtensionsFromPhpIni;
        this.enabledExtensions = cleanList(enabledExtensions);
        this.extraLibraryPaths = cleanList(extraLibraryPaths);
        this.extensionLibraryPaths = cleanMap(extensionLibraryPaths);
        this.preflightCheck = preflightCheck;
    }

    public File getDataFolder() {
        return dataFolder;
    }

    public String getServerLocation() {
        return serverLocation;
    }

    public boolean isDebug() {
        return debug;
    }

    public boolean isDetectExtensionsFromPhpIni() {
        return detectExtensionsFromPhpIni;
    }

    public List<String> getEnabledExtensions() {
        return enabledExtensions;
    }

    public List<String> getExtraLibraryPaths() {
        return extraLibraryPaths;
    }

    public Map<String, List<String>> getExtensionLibraryPaths() {
        return extensionLibraryPaths;
    }

    public boolean isPreflightCheck() {
        return preflightCheck;
    }

    private static List<String> cleanList(List<String> input) {
        if (input == null || input.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String value : input) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static Map<String, List<String>> cleanMap(Map<String, List<String>> input) {
        if (input == null || input.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : input.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key = normalizeExtensionName(entry.getKey());
            if (key.isEmpty()) {
                continue;
            }
            List<String> values = cleanList(entry.getValue());
            if (!values.isEmpty()) {
                result.put(key, values);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    public static String normalizeExtensionName(String value) {
        if (value == null) {
            return "";
        }
        String result = value.trim().toLowerCase();
        if (result.startsWith("zend_extension=")) {
            result = result.substring("zend_extension=".length());
        } else if (result.startsWith("extension=")) {
            result = result.substring("extension=".length());
        }
        int slash = Math.max(result.lastIndexOf('/'), result.lastIndexOf('\\'));
        if (slash >= 0) {
            result = result.substring(slash + 1);
        }
        if (result.endsWith(".so")) {
            result = result.substring(0, result.length() - 3);
        }
        result = result.trim();

        // mbregex is not a separate PHP shared extension in modern PHP builds.
        // It is enabled as part of mbstring via --enable-mbregex, so accept
        // "mbregex" in config/php.ini as a user-friendly alias for mbstring.
        if ("mbregex".equals(result)) {
            return "mbstring";
        }

        return result;
    }
}
