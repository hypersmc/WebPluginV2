package me.jumpwatch.webserver.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class CheckOS {
    public static String OS = System.getProperty("os.name").toLowerCase();
    public static boolean isWindows() {
        return (OS.indexOf("win") >= 0);
    }
    public static boolean isUnix() {
        return (OS.indexOf("nix") >= 0
                || OS.indexOf("nux") >= 0
                || OS.indexOf("aix") > 0);
    }
    public static Boolean isRunningInsideDocker() {
        try (Stream< String > stream =
                     Files.lines(Paths.get("/proc/1/cgroup"))) {
            return stream.anyMatch(line -> line.contains("/docker"));
        } catch (IOException e) {
            return false;
        }
    }
}
