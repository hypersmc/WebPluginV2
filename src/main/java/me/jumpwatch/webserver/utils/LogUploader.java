package me.jumpwatch.webserver.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogUploader {

    private static final File pluginFolder = new File("plugins/webplugin/logs/");
    private static final File configFile = new File("plugins/webplugin/config.yml");

    public static String uploadLatestLog(boolean useDebug, boolean includeConfig, boolean privacy) {
        File latestLog = getLatestLog(useDebug);
        if (latestLog == null) {
            return null;
        }

        StringBuilder fullContent = new StringBuilder();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String configContent = includeConfig ? readFile(configFile) : "";
        String redactedConfig = privacy ? redactConfig(configContent) : configContent;
        String serverIdentity = privacy ? "<hidden>" : getServerNameOrIP(configContent);

        // Add fancy header
        fullContent.append("######################################\n");
        fullContent.append("#         CONFIG.YML CONTENT         #\n");
        if (!privacy) {
            fullContent.append("#  (Redacted where necessary below)  #\n");
        }
        fullContent.append("#                                    #\n");
        fullContent.append("#  Upload Time: ").append(timestamp).append("  #\n");
        fullContent.append("#  Server: ").append(serverIdentity).append("  #\n");
        fullContent.append("######################################\n\n");
        if (includeConfig) {
            fullContent.append(redactedConfig).append("\n\n");
        }

        // Add log section
        fullContent.append("======================================\n");
        fullContent.append("LOG FILE ").append(latestLog.getName()).append("\n");
        fullContent.append("======================================\n");
        fullContent.append(readFile(latestLog));

        // Upload the full paste
        return uploadToHastyBin(fullContent.toString());
    }

    private static File getLatestLog(boolean debug) {
        File[] logFiles = pluginFolder.listFiles((dir, name) ->
                name.startsWith(debug ? "DEBUGLOG-" : "WPLOG-") && name.endsWith(".log"));

        if (logFiles == null || logFiles.length == 0) return null;

        Arrays.sort(logFiles, Comparator.comparingLong(File::lastModified).reversed());
        return logFiles[0];
    }

    private static String readFile(File file) {
        if (!file.exists()) return "";
        try {
            return new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "<Failed to read file: " + e.getMessage() + ">";
        }
    }

    private static String redactConfig(String yaml) {
        yaml = yaml.replaceAll("(?m)^\\s*ServerIP:.*$", "  ServerIP: <hidden>");
        yaml = yaml.replaceAll("(?m)^\\s*SSLDomain:.*$", "  SSLDomain: <hidden>");
        yaml = yaml.replaceAll("(?m)^\\s*SSLPriv:.*$", "  SSLPriv: <hidden>");
        yaml = yaml.replaceAll("(?m)^\\s*SSLPub:.*$", "  SSLPub: <hidden>");
        yaml = yaml.replaceAll("(?m)^\\s*ServerLocation:\\s*(?!/home/container).*$", "  ServerLocation: <hidden>");
        return yaml;
    }

    private static String getServerNameOrIP(String config) {
        Matcher m = Pattern.compile("SSLDomain:\\s*(\\S+)").matcher(config);
        if (m.find()) return m.group(1);
        m = Pattern.compile("ServerIP:\\s*(\\S+)").matcher(config);
        if (m.find()) return m.group(1);
        return "Unknown";
    }

    private static String uploadToHastyBin(String content) {
        try {
            URL url = new URL("https://bin.zennodes.dk/api/pastes");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("accept", "application/json");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            String json = String.format("{\"content\": %s}", escapeJson(content));
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            InputStream responseStream = conn.getResponseCode() >= 400
                    ? conn.getErrorStream()
                    : conn.getInputStream();

            String result = new String(responseStream.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject jsonResponse = JsonParser.parseString(result.toString()).getAsJsonObject();
            return "https://bin.zennodes.dk/" + jsonResponse.get("paste_id").getAsString();

        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    private static String escapeJson(String content) {
        return "\"" + content.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
}