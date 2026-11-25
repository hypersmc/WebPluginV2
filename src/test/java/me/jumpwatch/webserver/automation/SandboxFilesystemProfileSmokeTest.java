package me.jumpwatch.webserver.automation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Test Java-created sandbox profiles against a Minecraft-root sentinel.
 * No existing Minecraft data is read and no HTTP-accessible PHP script is made.
 */
public final class SandboxFilesystemProfileSmokeTest {
    public static void main(String[] args) throws Exception {
        // Use the project workspace, not /tmp: compatibility profiles deliberately
        // permit /tmp, so a /tmp-hosted sentinel would give a false sense of safety.
        Path workspace = Path.of("").toAbsolutePath().normalize().toRealPath();
        Path systemTmp = Path.of(System.getProperty("java.io.tmpdir", "/tmp"))
                .toAbsolutePath().normalize().toRealPath();
        if (workspace.startsWith(systemTmp))
            throw new AssertionError("Test workspace lies inside allowed java.io.tmpdir");
        Path base = Files.createTempDirectory(workspace, ".webplugin-profile-test-");
        Path server = Files.createDirectories(base.resolve("minecraft-server"));
        Path plugin = Files.createDirectories(server.resolve("plugins/webplugin"));
        Path website = Files.createDirectories(plugin.resolve("php"));
        Path php = Files.createDirectories(plugin.resolve("phplinux"));
        Path nginx = Files.createDirectories(plugin.resolve("nginxlinux"));
        Path sentinel = Files.writeString(server.resolve("minecraft-private-sentinel"),
                "do-not-expose\n", StandardCharsets.UTF_8);
        if (!Files.readString(sentinel).trim().equals("do-not-expose"))
            throw new AssertionError("JVM must be able to read the sentinel before sandboxing");
        Path helper = LandlockHelperInstaller.prepare(plugin, server, "", System.out::println);
        LandlockSandbox sandbox = new LandlockSandbox(true, LandlockSandbox.Mode.REQUIRED,
                helper, System.out::println);
        if (sandbox.getAvailability() != LandlockSandbox.Availability.AVAILABLE) {
            System.out.println("SKIP: Java filesystem profile enforcement unavailable: "
                    + sandbox.getAvailability());
            return;
        }
        for (LandlockSandbox.Role role : List.of(
                LandlockSandbox.Role.PHP_FPM, LandlockSandbox.Role.TASK)) {
            Path file = website.resolve("can-write-" + role.name().toLowerCase() + ".txt");
            ProcessBuilder child = new ProcessBuilder("/bin/sh", "-c",
                    "printf 'website-safe' > \"$1\" || exit 41; "
                    + "if IFS= read -r value < \"$2\"; then echo ESCAPE_ALLOWED; exit 42; fi; "
                    + "echo OUTSIDE_BLOCKED",
                    "webplugin-profile-check", file.toString(), sentinel.toString());
            child.directory(website.toFile());
            child = sandbox.protect(child, role, plugin, website, php, nginx);
            child.redirectErrorStream(true);
            Process p = child.start();
            if (!p.waitFor(8, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new AssertionError(role + " profile test timed out");
            }
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (p.exitValue() != 0 || !output.contains("OUTSIDE_BLOCKED") || !Files.isRegularFile(file)
                    || !Files.readString(file).equals("website-safe"))
                throw new AssertionError(role + " write/denial profile failed: exit "
                        + p.exitValue() + " output " + output);
        }

        // NGINX reads site content, but must not be able to write inside the site
        // nor read the independent Minecraft-root sentinel.
        Path siteFile = Files.writeString(website.resolve("index-sentinel.txt"), "public-content\n");
        Path notWritable = website.resolve("nginx-should-not-write");
        ProcessBuilder nginxChild = new ProcessBuilder("/bin/sh", "-c",
                "IFS= read -r value < \"$1\" || exit 51; "
                + "[ \"$value\" = public-content ] || exit 52; "
                + "if printf 'bad' > \"$2\" 2>/dev/null; then echo WRITE_ESCAPED; exit 53; fi; "
                + "if IFS= read -r value < \"$3\"; then echo READ_ESCAPED; exit 54; fi; "
                + "echo NGINX_DENIED",
                "webplugin-nginx-check", siteFile.toString(), notWritable.toString(),
                sentinel.toString());
        nginxChild.directory(website.toFile());
        nginxChild = sandbox.protect(nginxChild, LandlockSandbox.Role.NGINX,
                plugin, website, php, nginx);
        nginxChild.redirectErrorStream(true);
        Process process = nginxChild.start();
        if (!process.waitFor(8, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("NGINX profile test timed out");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0 || !output.contains("NGINX_DENIED") || Files.exists(notWritable))
            throw new AssertionError("NGINX profile failed: " + output);

        System.out.println("PASS: PHP-FPM/TASK site write allowed and Minecraft-root read denied; "
                + "NGINX site read allowed, site write denied, Minecraft-root read denied");
    }
}
