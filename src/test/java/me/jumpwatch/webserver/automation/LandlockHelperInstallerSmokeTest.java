package me.jumpwatch.webserver.automation;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;

/** Confirms a regular JAR can install and repair its own Landlock helper. */
public final class LandlockHelperInstallerSmokeTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("webplugin-native-helper-test-");
        Path expected = root.resolve("bin/webplugin-landlock");

        Path actual = LandlockHelperInstaller.prepare(root, root, "", System.out::println);
        if (!expected.equals(actual))
            throw new AssertionError("Wrong default helper path: " + actual);
        if (!Files.isRegularFile(actual) || !Files.isExecutable(actual))
            throw new AssertionError("Bundled helper not automatically installed as executable");

        Process check = new ProcessBuilder(actual.toString(), "--probe").start();
        if (!check.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
            check.destroyForcibly();
            throw new AssertionError("Bundled helper probe blocked");
        }
        int exit = check.exitValue();
        if (exit != 0 && exit != 10 && exit != 11 && exit != 12)
            throw new AssertionError("Bundled helper failed unexpectedly: " + exit);

        // Simulate a Pterodactyl upload which lacks executable permission.
        Files.setPosixFilePermissions(actual, EnumSet.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        if (Files.isExecutable(actual))
            throw new AssertionError("Could not remove executable bit for test");
        LandlockHelperInstaller.prepare(root, root, "", System.out::println);
        if (!Files.isExecutable(actual))
            throw new AssertionError("Installer did not fix missing executable permission");

        // A stale/corrupted managed helper must be replaced with the bundled one.
        Files.writeString(actual, "stale helper");
        LandlockHelperInstaller.prepare(root, root, "", System.out::println);
        try (InputStream resource = LandlockHelperInstaller.class.getResourceAsStream(
                "/native/linux-amd64/webplugin-landlock")) {
            if (resource == null || !java.util.Arrays.equals(resource.readAllBytes(), Files.readAllBytes(actual)))
                throw new AssertionError("Stale helper was not restored from JAR");
        }

        // Custom helper paths are administrator-owned: do not overwrite or chmod them.
        Path custom = root.resolve("custom-helper");
        Files.writeString(custom, "custom");
        Files.setPosixFilePermissions(custom, EnumSet.of(PosixFilePermission.OWNER_READ));
        LandlockHelperInstaller.prepare(root, root, custom.toString(), System.out::println);
        if (!Files.readString(custom).equals("custom") || Files.isExecutable(custom))
            throw new AssertionError("Custom helper was modified");

        System.out.println("PASS: embedded helper installation, chmod repair, update and custom-path preservation");
    }
}
