package me.jumpwatch.webserver.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class DebugLogger {

    private static final String LOG_FOLDER = "plugins/webplugin/logs/";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024; // 2MB in bytes

    private static File getLogFile() {
        String date = LocalDate.now().format(DATE_FORMAT);
        File logDir = new File(LOG_FOLDER);
        if (!logDir.exists()) {
            logDir.mkdirs();
        }

        int index = 0;
        File file;
        do {
            String suffix = (index == 0) ? "" : "." + index;
            file = new File(logDir, "DEBUGLOG-" + date + suffix + ".log");
            index++;
        } while (file.exists() && file.length() >= MAX_FILE_SIZE);

        return file;
    }

    private static void writeLog(String level, String message) {
        String time = LocalTime.now().format(TIME_FORMAT);
        String line = String.format("[%s] [%s] %s", time, level.toUpperCase(), message);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(getLogFile(), true))) {
            writer.write(line);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Failed to write to debug log: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void info(String message) {
        writeLog("INFO", message);
    }

    public static void warn(String message) {
        writeLog("WARN", message);
    }

    public static void error(String message) {
        writeLog("ERROR", message);
    }

    public static void debug(String message) {
        writeLog("DEBUG", message);
    }
}