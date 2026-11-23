package me.jumpwatch.webserver.automation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/** Test fallback rules without executing the command or modifying the JVM. */
public final class LandlockSandboxSmokeTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("webplugin-landlock-smoke-");
        Path missing = root.resolve("missing-helper");
        ProcessBuilder original = new ProcessBuilder("true");
        LandlockSandbox disabled = new LandlockSandbox(false, LandlockSandbox.Mode.PREFERRED, missing, System.out::println);
        if (disabled.protect(original, LandlockSandbox.Role.TASK, root, root, root, root) != original)
            throw new AssertionError("Disabled sandbox must preserve the original ProcessBuilder");

        LandlockSandbox preferred = new LandlockSandbox(true, LandlockSandbox.Mode.PREFERRED, missing, System.out::println);
        if (preferred.protect(original, LandlockSandbox.Role.TASK, root, root, root, root) != original)
            throw new AssertionError("Preferred mode should support missing-helper fallback");

        LandlockSandbox required = new LandlockSandbox(true, LandlockSandbox.Mode.REQUIRED, missing, System.out::println);
        boolean denied = false;
        try {
            required.protect(original, LandlockSandbox.Role.TASK, root, root, root, root);
        } catch (IOException expected) { denied = true; }
        if (!denied) throw new AssertionError("Required mode must refuse missing helper");

        // An executable helper which fails its probe is an ERROR, not predictable no-support.
        Path bad = Files.isExecutable(Path.of("/bin/false")) ? Path.of("/bin/false") : Path.of("/usr/bin/false");
        if (Files.isExecutable(bad)) {
            LandlockSandbox unexpected = new LandlockSandbox(true, LandlockSandbox.Mode.PREFERRED, bad, System.out::println);
            denied = false;
            try {
                unexpected.protect(original, LandlockSandbox.Role.TASK, root, root, root, root);
            } catch (IOException expected) { denied = true; }
            if (!denied) throw new AssertionError("Preferred mode must not fallback from a broken helper");
        }

        System.out.println("PASS: disabled/preferred/required Landlock fallback and unexpected probe failure");
    }
}
