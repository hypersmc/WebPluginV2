package me.jumpwatch.webserver.automation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

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

        // Exercise Java-generated filesystem rules, not manually crafted helper args.
        // PHP-FPM uses O_RDWR for /dev/null during stdio initialization and must
        // be able to start inside an enabled sandbox (not merely pass php -m).
        Path nativeHelper = LandlockHelperInstaller.prepare(root, root, "", System.out::println);
        LandlockSandbox active = new LandlockSandbox(true, LandlockSandbox.Mode.REQUIRED,
                nativeHelper, System.out::println);
        if (active.getAvailability() == LandlockSandbox.Availability.AVAILABLE) {
            Path website = Files.createDirectories(root.resolve("website"));
            Path phpRuntime = Files.createDirectories(root.resolve("php-runtime"));
            Path nginxRuntime = Files.createDirectories(root.resolve("nginx-runtime"));
            Path pluginData = Files.createDirectories(root.resolve("plugin-data"));
            for (LandlockSandbox.Role role : LandlockSandbox.Role.values()) {
                ProcessBuilder command = new ProcessBuilder("/bin/sh", "-c",
                        "exec 3<>/dev/null || exit 41; printf testing >&3 || exit 42");
                command.directory(website.toFile());
                ProcessBuilder protectedCommand = active.protect(
                        command, role, pluginData, website, phpRuntime, nginxRuntime);
                protectedCommand.redirectErrorStream(true);
                Process child = protectedCommand.start();
                if (!child.waitFor(8, TimeUnit.SECONDS)) {
                    child.destroyForcibly();
                    throw new AssertionError("Timed out testing /dev/null access in " + role);
                }
                String output = new String(child.getInputStream().readAllBytes());
                if (child.exitValue() != 0)
                    throw new AssertionError(role + " cannot open /dev/null for read/write: exit "
                            + child.exitValue() + ": " + output);
            }
            System.out.println("PASS: NGINX, PHP-FPM and TASK sandboxes allow /dev/null O_RDWR");
        } else {
            System.out.println("SKIP: /dev/null O_RDWR enforcement test (Landlock unavailable: "
                    + active.getAvailability() + ")");
        }

        System.out.println("PASS: disabled/preferred/required Landlock fallback and unexpected probe failure");
    }
}
