/*
 * ******************************************************
 *  *Copyright (c) 2020-2022. Jesper Henriksen mhypers@gmail.com
 *
 *  * This file is part of WebServer project
 *  *
 *  * WebServer can not be copied and/or distributed without the express
 *  * permission of Jesper Henriksen
 *  ******************************************************
 */ 
package me.jumpwatch.webserver.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
        // Check for /.dockerenv file (commonly present in Docker containers)
        if (Files.exists(Paths.get("/.dockerenv"))) {
            return true;
        }

        // Check for patterns in /proc/1/cgroup
        try (Stream<String> stream = Files.lines(Paths.get("/proc/1/cgroup"))) {
            return stream.anyMatch(line ->
                    line.contains("/docker/") ||
                            line.contains("/kubepods/") ||
                            line.contains("/containerd/")
            );
        } catch (IOException e) {
            return false;
        }
    }
    public static String getLinuxName(){
        String[] cmd = {"/bin/sh", "-c", "uname -a" };
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            BufferedReader bri = new BufferedReader(new InputStreamReader(
                    p.getInputStream()));
            String line = "";
            while ((line = bri.readLine()) != null) {
                return line;
            }
            bri.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
