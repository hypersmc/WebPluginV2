package me.jumpwatch.webserver.php.linux.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Reads enabled extension/zend_extension lines from php.ini. */
public final class PhpIniExtensionResolver {
    private PhpIniExtensionResolver() {
    }

    public static List<String> readEnabledExtensions(Path phpIni) throws IOException {
        Set<String> extensions = new LinkedHashSet<>();
        if (!Files.exists(phpIni)) {
            return new ArrayList<>();
        }
        for (String rawLine : Files.readAllLines(phpIni)) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) {
                continue;
            }
            String lower = line.toLowerCase();
            if (!(lower.startsWith("extension=") || lower.startsWith("zend_extension="))) {
                continue;
            }
            int commentIndex = line.indexOf(';');
            if (commentIndex >= 0) {
                line = line.substring(0, commentIndex).trim();
            }
            String extension = LinuxPhpRuntimeOptions.normalizeExtensionName(line);
            if (!extension.isEmpty()) {
                extensions.add(extension);
            }
        }
        return new ArrayList<>(extensions);
    }
}
